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

    // Primary: receipt-ocr LLM microservice
    private final ReceiptOcrClientService receiptOcrClient;
    private final ReceiptOcrMapper        ocrMapper;

    // Fallback: Tesseract + heuristic pipeline (deprecated — retained for rollback safety)
    private final OcrService          ocrService;
    private final ReceiptParserService parser;

    @Transactional
    public ReceiptResponse processReceipt(MultipartFile file, User user) throws IOException {
        // Quota check FIRST — must run before any OCR or storage so we never
        // consume external resources when the user has hit their cap.
        quotaService.requireQuotaAvailable(user);
        rateLimiter.checkUploadAttempt(user.getId().toString());
        validateUpload(file);

        // 1 — Persist receipt record (PENDING)
        Receipt receipt = Receipt.builder()
            .user(user)
            .fileName(sanitize(file.getOriginalFilename()))
            .fileSize(file.getSize())
            .mimeType(file.getContentType())
            .status(ReceiptStatus.PENDING)
            .build();
        receipt = receiptRepository.save(receipt);
        auditLogService.log(user, AuditLogService.ACTION_RECEIPT_UPLOADED, "Receipt", receipt.getId().toString());

        // 2 — Store file
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

        // 3 — Extract: primary = receipt-ocr LLM service; fallback = Tesseract
        OcrExtractionResult extraction = extractWithFallback(filePath, receipt);
        // Quota was checked at entry; the OCR call has now run so the resource was consumed.
        // Increment the user's counter regardless of OCR success/failure.
        quotaService.recordReceiptProcessed(user.getId());
        if (extraction == null) {
            return toResponse(receipt, null, null);
        }

        // 4 — OCR complete; receipt is now a draft awaiting user review.
        //     Transaction creation is deferred until the user confirms via POST /receipts/{id}/confirm.
        receipt.setStatus(ReceiptStatus.COMPLETED);
        if (!extraction.isSuccessful()) {
            receipt.setErrorMessage("Could not extract a total amount from the receipt image.");
        }
        receiptRepository.save(receipt);
        return toResponse(receipt, extraction, null);
    }

    /**
     * Converts a user-reviewed OCR draft into a persisted transaction.
     *
     * The user has had an opportunity to view, correct, and approve the OCR-extracted
     * fields on the review screen before calling this endpoint. All values in the
     * request are trusted as user-confirmed.
     *
     * <p>Idempotent: returns the existing transaction if the receipt was already confirmed.
     */
    @Transactional
    public ReceiptResponse confirmReceipt(UUID receiptId, ReceiptConfirmRequest request, User user) {
        Receipt receipt = receiptRepository.findByIdAndUserId(receiptId, user.getId())
            .orElseThrow(() -> new ResourceNotFoundException("Receipt", receiptId));

        // Idempotency: if already confirmed, return current state without duplicating
        Transaction existing = transactionRepository.findTopByReceiptId(receiptId).orElse(null);
        if (existing != null) {
            log.debug("Receipt {} already confirmed, returning existing transaction {}", receiptId, existing.getId());
            OcrExtractionResult extraction = reconstructExtraction(receipt);
            return toResponse(receipt, extraction, existing);
        }

        Transaction tx = buildTransactionFromConfirm(request, receipt, user);
        transactionRepository.save(tx);

        // Persist the user-confirmed values on the receipt for audit trail
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

    // -------------------------------------------------------------------------
    // Extraction pipeline
    // -------------------------------------------------------------------------

    /**
     * Tries receipt-ocr LLM service first. Falls back to Tesseract on failure.
     * Returns null (and marks receipt FAILED) only if both paths throw.
     */
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

        // Tesseract fallback (deprecated path)
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

    // -------------------------------------------------------------------------
    // Detail-view reconstruction (avoids re-parsing for stored data)
    // -------------------------------------------------------------------------

    private OcrExtractionResult reconstructExtraction(Receipt receipt) {
        // Structured data is available when either field is populated
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
        // Legacy receipts processed before V4: re-parse stored OCR text
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

    // -------------------------------------------------------------------------
    // Transaction creation
    // -------------------------------------------------------------------------

    /**
     * Creates a transaction from user-confirmed values.
     * The transaction is always marked reviewed=true because the user explicitly approved it.
     */
    private Transaction buildTransactionFromConfirm(ReceiptConfirmRequest req, Receipt receipt, User user) {
        // User-supplied notes override the auto-normalized description
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
            .reviewed(true) // user explicitly reviewed and confirmed this data
            .build();
    }

    // -------------------------------------------------------------------------
    // Input validation
    // -------------------------------------------------------------------------

    private void validateUpload(MultipartFile file) {
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
    }

    private String sanitize(String filename) {
        if (filename == null) return "receipt.jpg";
        return filename.replaceAll("[^a-zA-Z0-9._\\-]", "_");
    }

    // -------------------------------------------------------------------------
    // Response mapping
    // -------------------------------------------------------------------------

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
