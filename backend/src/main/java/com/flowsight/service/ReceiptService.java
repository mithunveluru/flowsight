package com.flowsight.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowsight.analytics.CategorizationService;
import com.flowsight.analytics.NormalizationService;
import com.flowsight.dto.receipt.OcrExtractionResult;
import com.flowsight.dto.receipt.ReceiptConfirmRequest;
import com.flowsight.dto.receipt.ReceiptLineItem;
import com.flowsight.dto.receipt.ReceiptOcrResponse;
import com.flowsight.dto.receipt.ReceiptResponse;
import com.flowsight.dto.transaction.TransactionResponse;
import com.flowsight.entity.*;
import com.flowsight.exception.FlowsightException;
import com.flowsight.exception.OcrException;
import com.flowsight.exception.ResourceNotFoundException;
import com.flowsight.ocr.OcrDocument;
import com.flowsight.ocr.OcrService;
import com.flowsight.ocr.ReceiptOcrClientService;
import com.flowsight.ocr.ReceiptOcrMapper;
import com.flowsight.ocr.ReceiptParserService;
import com.flowsight.repository.ReceiptRepository;
import com.flowsight.repository.TransactionRepository;
import com.flowsight.util.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReceiptService {

    private static final long MAX_FILE_BYTES = 5L * 1024 * 1024; // 5 MB
    private static final List<String> ALLOWED_MIME_TYPES =
        List.of("image/jpeg", "image/png", "image/webp", "image/tiff", "image/bmp");

    private final ReceiptRepository    receiptRepository;
    private final TransactionRepository transactionRepository;
    private final FileStorageService   fileStorage;
    private final CategorizationService categorizationService;
    private final NormalizationService  normalizationService;
    private final ObjectMapper          objectMapper;
    private final ReceiptQuotaService   quotaService;
    private final AuditLogService       auditLogService;
    private final com.flowsight.security.RateLimiter rateLimiter;

    // primary: receipt-ocr LLM microservice
    private final ReceiptOcrClientService receiptOcrClient;
    private final ReceiptOcrMapper        ocrMapper;

    // fallback: Tesseract heuristic pipeline (deprecated, kept for rollback)
    private final OcrService          ocrService;
    private final ReceiptParserService parser;

    @Transactional
    public ReceiptResponse processReceipt(MultipartFile file, User user) throws IOException {
        // quota before OCR/storage so a capped user consumes nothing
        quotaService.requireQuotaAvailable(user);
        rateLimiter.checkUploadAttempt(user.getId().toString());
        validateUpload(file);

        Receipt receipt = Receipt.builder()
            .user(user)
            .fileName(sanitize(file.getOriginalFilename()))
            .fileSize(file.getSize())
            .mimeType(file.getContentType())
            .status(ReceiptStatus.PENDING)
            .build();
        receipt = receiptRepository.save(receipt);
        auditLogService.log(user, AuditLogService.ACTION_RECEIPT_UPLOADED, "Receipt", receipt.getId().toString());

        Path filePath;
        try {
            filePath = fileStorage.store(file, user.getId(), receipt.getId());
            receipt.setFilePath(filePath.toString());
        } catch (IOException e) {
            receipt.setStatus(ReceiptStatus.FAILED);
            receipt.setErrorMessage("File storage failed: " + e.getMessage());
            receiptRepository.save(receipt);
            throw e;
        }

        receipt.setStatus(ReceiptStatus.PROCESSING);
        receiptRepository.save(receipt);

        // extract: receipt-ocr primary, Tesseract fallback
        OcrExtractionResult extraction = extractWithFallback(filePath, receipt);
        // OCR ran; count it regardless of success
        quotaService.recordReceiptProcessed(user.getId());
        if (extraction == null) {
            return toResponse(receipt, null, null);
        }

        // draft only; the transaction is created when the user confirms
        receipt.setStatus(ReceiptStatus.COMPLETED);
        if (!extraction.isSuccessful()) {
            receipt.setErrorMessage("Could not extract a total amount from the receipt image.");
        }
        receiptRepository.save(receipt);
        return toResponse(receipt, extraction, null);
    }

    // Persist a user-reviewed OCR draft as a transaction. Idempotent.
    @Transactional
    public ReceiptResponse confirmReceipt(UUID receiptId, ReceiptConfirmRequest request, User user) {
        Receipt receipt = receiptRepository.findByIdAndUserId(receiptId, user.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Receipt", receiptId));

        // already confirmed: return existing, don't duplicate
        Transaction existing = transactionRepository.findTopByReceiptId(receiptId).orElse(null);
        if (existing != null) {
            log.debug("Receipt {} already confirmed, returning existing transaction {}", receiptId, existing.getId());
            OcrExtractionResult extraction = reconstructExtraction(receipt);
            return toResponse(receipt, extraction, existing);
        }

        Transaction tx = buildTransactionFromConfirm(request, receipt, user);
        transactionRepository.save(tx);

        // store the confirmed values on the receipt for audit
        receipt.setExtractedMerchant(request.getMerchant());
        receipt.setExtractedAmount(request.getAmount());
        receipt.setExtractedDate(request.getDate());
        receiptRepository.save(receipt);

        log.info("Receipt {} confirmed by user: merchant='{}' amount={}", receiptId, request.getMerchant(), request.getAmount());
        OcrExtractionResult extraction = reconstructExtraction(receipt);
        return toResponse(receipt, extraction, tx);
    }

    public Page<ReceiptResponse> list(UUID userId, Pageable pageable) {
        return receiptRepository
            .findByUserIdOrderByCreatedAtDesc(userId, pageable)
            .map(r -> toResponse(r, null, null));
    }

    public ReceiptResponse getById(UUID id, UUID userId) {
        Receipt receipt = receiptRepository.findByIdAndUserId(id, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Receipt", id));

        OcrExtractionResult extraction = reconstructExtraction(receipt);
        Transaction tx = transactionRepository.findTopByReceiptId(receipt.getId()).orElse(null);
        return toResponse(receipt, extraction, tx);
    }

    @Transactional
    public void delete(UUID id, UUID userId) {
        Receipt receipt = receiptRepository.findByIdAndUserId(id, userId)
            .orElseThrow(() -> new ResourceNotFoundException("Receipt", id));
        fileStorage.delete(receipt.getFilePath());
        receiptRepository.delete(receipt);
    }

    // receipt-ocr first, Tesseract fallback; null (FAILED) only if both throw
    private OcrExtractionResult extractWithFallback(Path filePath, Receipt receipt) {
        Optional<ReceiptOcrResponse> ocrResponse = receiptOcrClient.extract(filePath);

        if (ocrResponse.isPresent()) {
            ReceiptOcrResponse ocrResult = ocrResponse.get();
            OcrExtractionResult extraction = ocrMapper.map(ocrResult);

            receipt.setOcrText(extraction.getRawText());
            receipt.setOcrProvider("RECEIPT_OCR_SERVICE");
            receipt.setExtractedMerchant(extraction.getMerchant());
            receipt.setExtractedAmount(extraction.getAmount());
            receipt.setExtractedDate(extraction.getDate());

            if (extraction.getConfidence() != null) {
                receipt.setOcrConfidence(
                    BigDecimal.valueOf(extraction.getConfidence()).setScale(3, RoundingMode.HALF_UP));
            }
            if (ocrResult.getLineItems() != null && !ocrResult.getLineItems().isEmpty()) {
                try {
                    receipt.setLineItemsJson(objectMapper.writeValueAsString(ocrResult.getLineItems()));
                } catch (JsonProcessingException e) {
                    log.debug("Could not serialize line items for receipt {}: {}", receipt.getId(), e.getMessage());
                }
            }

            log.info("receipt-ocr extracted merchant='{}' amount={} for receipt {}",
                extraction.getMerchant(), extraction.getAmount(), receipt.getId());
            return extraction;
        }

        // Tesseract fallback
        log.debug("receipt-ocr unavailable for receipt {}, falling back to Tesseract", receipt.getId());
        try {
            OcrDocument ocrDoc = ocrService.extractDocument(filePath);
            receipt.setOcrText(ocrDoc.plainText());
            receipt.setOcrProvider("TESSERACT_FALLBACK");
            OcrExtractionResult extraction = parser.parse(ocrDoc);
            receipt.setExtractedMerchant(extraction.getMerchant());
            receipt.setExtractedAmount(extraction.getAmount());
            receipt.setExtractedDate(extraction.getDate());
            return extraction;
        } catch (OcrException e) {
            receipt.setStatus(ReceiptStatus.FAILED);
            receipt.setErrorMessage("OCR failed: " + e.getMessage());
            receiptRepository.save(receipt);
            log.warn("OCR failed for receipt {}: {}", receipt.getId(), e.getMessage());
            return null;
        }
    }

    private OcrExtractionResult reconstructExtraction(Receipt receipt) {
        // structured data present when either field is set
        if (receipt.getExtractedAmount() != null || receipt.getExtractedMerchant() != null) {
            List<ReceiptLineItem> items = deserializeLineItems(receipt.getLineItemsJson());
            Double conf = receipt.getOcrConfidence() != null
                ? receipt.getOcrConfidence().doubleValue() : null;
            return OcrExtractionResult.builder()
                .merchant(receipt.getExtractedMerchant())
                .amount(receipt.getExtractedAmount())
                .date(receipt.getExtractedDate())
                .currency("INR")
                .successful(receipt.getExtractedAmount() != null)
                .rawText(receipt.getOcrText())
                .lineItems(items)
                .confidence(conf)
                .requiresConfirmation(conf != null && conf < 0.45)
                .build();
        }
        // legacy pre-V4: re-parse stored OCR text
        return receipt.getOcrText() != null ? parser.parse(receipt.getOcrText()) : null;
    }

    @SuppressWarnings("unchecked")
    private List<ReceiptLineItem> deserializeLineItems(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            return objectMapper.readValue(json,
                objectMapper.getTypeFactory().constructCollectionType(List.class, ReceiptLineItem.class));
        } catch (Exception e) {
            log.debug("Could not deserialize line items: {}", e.getMessage());
            return List.of();
        }
    }

    // build a transaction from user-confirmed values (reviewed=true)
    private Transaction buildTransactionFromConfirm(ReceiptConfirmRequest req, Receipt receipt, User user) {
        // notes override the normalized description
        String baseDescription = (req.getNotes() != null && !req.getNotes().isBlank())
            ? req.getNotes()
            : normalizationService.normalize(req.getMerchant());

        TransactionCategory category;
        BigDecimal confidenceScore;

        if (req.getCategory() != null) {
            category        = req.getCategory();
            confidenceScore = BigDecimal.ONE.setScale(4, RoundingMode.HALF_UP); // user chose explicitly
        } else {
            CategorizationService.CategorizationResult cat =
                categorizationService.categorize(baseDescription, req.getMerchant(), req.getAmount());
            category        = cat.getCategory();
            confidenceScore = BigDecimal.valueOf(cat.getConfidence()).setScale(4, RoundingMode.HALF_UP);
        }

        return Transaction.builder()
            .user(user)
            .amount(req.getAmount().setScale(4, RoundingMode.HALF_UP))
            .currency(req.getCurrency() != null && !req.getCurrency().isBlank() ? req.getCurrency() : "INR")
            .transactionDate(req.getDate())
            .description(baseDescription)
            .merchant(req.getMerchant())
            .category(category)
            .type(TransactionType.DEBIT)
            .source(TransactionSource.OCR)
            .confidenceScore(confidenceScore)
            .notes(req.getNotes())
            .rawText(receipt.getOcrText())
            .receipt(receipt)
            .reviewed(true) // user-confirmed
            .build();
    }

    private void validateUpload(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new FlowsightException("File is empty", HttpStatus.BAD_REQUEST);
        }
        if (file.getSize() > MAX_FILE_BYTES) {
            throw new FlowsightException(
                "File exceeds the 5 MB limit (" + (file.getSize() / 1024 / 1024) + " MB uploaded)",
                HttpStatus.BAD_REQUEST
            );
        }
        String mime = file.getContentType();
        if (mime == null || !ALLOWED_MIME_TYPES.contains(mime)) {
            throw new FlowsightException(
                "Unsupported file type: " + mime + ". Supported: JPEG, PNG, WebP, TIFF, BMP",
                HttpStatus.UNSUPPORTED_MEDIA_TYPE
            );
        }
        // Content-Type is client-controlled and spoofable; sniff magic bytes so a
        // disguised payload (script sent as image/png) never reaches OCR or disk.
        if (!hasAllowedImageSignature(file.getBytes())) {
            throw new FlowsightException(
                "File content does not match a supported image format",
                HttpStatus.UNSUPPORTED_MEDIA_TYPE
            );
        }
    }

    // magic-byte sniff for the allowed formats
    private boolean hasAllowedImageSignature(byte[] b) {
        if (b == null || b.length < 12) return false;
        // JPEG: FF D8 FF
        if (u(b[0]) == 0xFF && u(b[1]) == 0xD8 && u(b[2]) == 0xFF) return true;
        // PNG: 89 50 4E 47 0D 0A 1A 0A
        if (u(b[0]) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
            && u(b[4]) == 0x0D && u(b[5]) == 0x0A && u(b[6]) == 0x1A && u(b[7]) == 0x0A) return true;
        // WebP: "RIFF" .... "WEBP"
        if (b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
            && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return true;
        // TIFF: little-endian "II*\0" or big-endian "MM\0*"
        if (u(b[0]) == 0x49 && u(b[1]) == 0x49 && u(b[2]) == 0x2A && u(b[3]) == 0x00) return true;
        if (u(b[0]) == 0x4D && u(b[1]) == 0x4D && u(b[2]) == 0x00 && u(b[3]) == 0x2A) return true;
        // BMP: "BM"
        if (b[0] == 'B' && b[1] == 'M') return true;
        return false;
    }

    // unsigned byte value (0-255) for signature comparison
    private static int u(byte x) {
        return x & 0xFF;
    }

    private String sanitize(String filename) {
        if (filename == null) return "receipt.jpg";
        return filename.replaceAll("[^a-zA-Z0-9._\\-]", "_");
    }

    private ReceiptResponse toResponse(Receipt receipt, OcrExtractionResult extraction, Transaction tx) {
        TransactionResponse txResponse = tx != null ? toTransactionResponse(tx) : null;
        List<ReceiptLineItem> items = extraction != null ? extraction.getLineItems() : null;

        Double confidence = receipt.getOcrConfidence() != null
            ? receipt.getOcrConfidence().doubleValue() : null;
        boolean requiresConfirmation = extraction != null && extraction.isRequiresConfirmation();

        return ReceiptResponse.builder()
            .id(receipt.getId())
            .fileName(receipt.getFileName())
            .fileSize(receipt.getFileSize())
            .mimeType(receipt.getMimeType())
            .status(receipt.getStatus())
            .ocrText(receipt.getOcrText())
            .errorMessage(receipt.getErrorMessage())
            .extraction(extraction)
            .transaction(txResponse)
            .lineItems(items)
            .ocrProvider(receipt.getOcrProvider())
            .confidence(confidence)
            .requiresConfirmation(requiresConfirmation)
            .createdAt(receipt.getCreatedAt())
            .build();
    }

    private TransactionResponse toTransactionResponse(Transaction tx) {
        return TransactionResponse.builder()
            .id(tx.getId())
            .amount(tx.getAmount())
            .currency(tx.getCurrency())
            .transactionDate(tx.getTransactionDate())
            .description(tx.getDescription())
            .merchant(tx.getMerchant())
            .category(tx.getCategory())
            .categoryDisplayName(tx.getCategory() != null ? tx.getCategory().getDisplayName() : null)
            .type(tx.getType())
            .source(tx.getSource())
            .confidenceScore(tx.getConfidenceScore())
            .reviewed(tx.isReviewed())
            .createdAt(tx.getCreatedAt())
            .build();
    }
}
