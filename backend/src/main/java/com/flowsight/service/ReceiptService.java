package com.flowsight.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowsight.analytics.CategorizationService;
import com.flowsight.analytics.NormalizationService;
import com.flowsight.dto.receipt.OcrExtractionResult;
import com.flowsight.dto.receipt.ReceiptConfirmRequest;
import com.flowsight.dto.receipt.ReceiptLineItem;
import com.flowsight.dto.receipt.ReceiptResponse;
import com.flowsight.dto.transaction.TransactionResponse;
import com.flowsight.entity.*;
import com.flowsight.exception.FlowsightException;
import com.flowsight.exception.OcrException;
import com.flowsight.exception.ResourceNotFoundException;
import com.flowsight.dto.receipt.FieldAssessment;
import com.flowsight.ml.FeedbackCorrection;
import com.flowsight.ml.FeedbackService;
import com.flowsight.ml.FeedbackSource;
import com.flowsight.ml.SubjectType;
import com.flowsight.ocr.ReceiptExtractionService;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ReceiptService {

    private static final long MAX_FILE_BYTES = 5L * 1024 * 1024; // 5 MB
    private static final List<String> ALLOWED_MIME_TYPES =
        List.of("image/jpeg", "image/png", "image/webp", "image/tiff", "image/bmp");

    private static final int MERCHANT_COLUMN = 255;

    private final ReceiptRepository    receiptRepository;
    private final TransactionRepository transactionRepository;
    private final FileStorageService   fileStorage;
    private final CategorizationService categorizationService;
    private final NormalizationService  normalizationService;
    private final ObjectMapper          objectMapper;
    private final ReceiptQuotaService   quotaService;
    private final AuditLogService       auditLogService;
    private final com.flowsight.security.RateLimiter rateLimiter;
    private final ReceiptExtractionService extractionService;
    // re-parses stored OCR text of receipts processed before structured extraction existed
    private final ReceiptParserService parser;
    private final FeedbackService      feedbackService;

    // Not transactional on purpose: OCR can take tens of seconds (several Tesseract passes, or an
    // ml-service / vision-model call) and must not hold a pool connection. Each save is its own
    // short transaction. OCR only produces a draft; nothing is booked until confirmReceipt.
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
        receipt = receiptRepository.save(receipt);

        ReceiptExtractionService.Extraction extraction = null;
        try {
            extraction = extractionService.extract(filePath, file.getContentType(),
                ReceiptExtractionService.context(user.getId(), receipt.getId()));
        } catch (OcrException e) {
            log.warn("OCR failed for receipt {}: {}", receipt.getId(), e.getMessage());
        }
        // OCR ran; count it regardless of success
        quotaService.recordReceiptProcessed(user.getId());

        if (extraction == null) {
            receipt.setStatus(ReceiptStatus.FAILED);
            receipt.setErrorMessage("OCR failed: the image could not be read");
            receipt = receiptRepository.save(receipt);
            return toResponse(receipt, null, null);
        }

        OcrExtractionResult result = extraction.result();
        applyExtraction(receipt, extraction);
        // draft only; the transaction is created when the user confirms
        receipt.setStatus(ReceiptStatus.COMPLETED);
        if (!result.isSuccessful()) {
            receipt.setErrorMessage("Could not extract a total amount from the receipt image.");
        }
        receipt = receiptRepository.save(receipt);
        log.debug("Receipt {} extracted by {} decision={}", receipt.getId(), extraction.provider(), result.getDecision());
        return toResponse(receipt, result, null);
    }

    // Persist a user-reviewed OCR draft as a transaction. Idempotent: the receipt row is locked, so a
    // concurrent second submit waits and then returns the transaction the first one created.
    @Transactional
    public ReceiptResponse confirmReceipt(UUID receiptId, ReceiptConfirmRequest request, User user) {
        Receipt receipt = receiptRepository.findByIdAndUserIdForUpdate(receiptId, user.getId())
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

        List<String> corrected = recordCorrections(receipt, request, tx.getCurrency(), user);

        // store the confirmed values on the receipt; predicted_* keep what OCR read
        receipt.setExtractedMerchant(request.getMerchant());
        receipt.setExtractedAmount(request.getAmount());
        receipt.setExtractedDate(request.getDate());
        receipt.setCurrency(tx.getCurrency());
        receipt.setConfirmedAt(Instant.now());
        receiptRepository.save(receipt);

        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("correctedFields", corrected);
        if (receipt.getOcrDecision() != null) metadata.put("ocrDecision", receipt.getOcrDecision());
        auditLogService.log(user, AuditLogService.ACTION_RECEIPT_CONFIRMED, "Receipt", receiptId.toString(), metadata);

        log.info("Receipt {} confirmed ({} field(s) corrected)", receiptId, corrected.size());
        OcrExtractionResult extraction = reconstructExtraction(receipt);
        return toResponse(receipt, extraction, tx);
    }

    // One feedback row per field the user changed: what OCR predicted, what the user confirmed.
    private List<String> recordCorrections(Receipt receipt, ReceiptConfirmRequest req, String currency, User user) {
        String predictedMerchant = receipt.getPredictedMerchant() != null ? receipt.getPredictedMerchant() : receipt.getExtractedMerchant();
        BigDecimal predictedAmount = receipt.getPredictedAmount() != null ? receipt.getPredictedAmount() : receipt.getExtractedAmount();
        LocalDate predictedDate = receipt.getPredictedDate() != null ? receipt.getPredictedDate() : receipt.getExtractedDate();
        String predictedCurrency = receipt.getPredictedCurrency();

        List<String> corrected = new ArrayList<>();
        if (!sameText(predictedMerchant, req.getMerchant())) {
            correction(receipt, user, ReceiptParserService.FIELD_MERCHANT, predictedMerchant, req.getMerchant(),
                receipt.getMerchantConfidence(), corrected);
        }
        if (predictedAmount == null || predictedAmount.compareTo(req.getAmount()) != 0) {
            correction(receipt, user, ReceiptParserService.FIELD_AMOUNT, plain(predictedAmount), plain(req.getAmount()),
                receipt.getAmountConfidence(), corrected);
        }
        if (!Objects.equals(predictedDate, req.getDate())) {
            correction(receipt, user, ReceiptParserService.FIELD_DATE, predictedDate == null ? null : predictedDate.toString(),
                req.getDate().toString(), receipt.getDateConfidence(), corrected);
        }
        // receipts read before V17 have no predicted currency (it was always INR)
        if (receipt.getExtractionDetails() != null && !Objects.equals(predictedCurrency, currency)) {
            correction(receipt, user, ReceiptParserService.FIELD_CURRENCY, predictedCurrency, currency,
                receipt.getCurrencyConfidence(), corrected);
        }
        return corrected;
    }

    private void correction(Receipt receipt, User user, String field, String predicted, String confirmed,
                            BigDecimal confidence, List<String> corrected) {
        feedbackService.record(user.getId(), new FeedbackCorrection(SubjectType.RECEIPT, receipt.getId(), field,
            predicted, confirmed, FeedbackSource.RECEIPT_REVIEW, receipt.getOcrModelVersion(),
            confidence == null ? null : confidence.doubleValue()));
        corrected.add(field);
    }

    private static boolean sameText(String a, String b) {
        if (a == null || b == null) return a == null && b == null;
        return a.strip().equalsIgnoreCase(b.strip());
    }

    private static String plain(BigDecimal v) {
        return v == null ? null : v.setScale(2, RoundingMode.HALF_UP).toPlainString();
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

    private void applyExtraction(Receipt receipt, ReceiptExtractionService.Extraction extraction) {
        OcrExtractionResult r = extraction.result();
        String merchant = truncate(r.getMerchant(), MERCHANT_COLUMN);
        receipt.setOcrText(r.getRawText());
        receipt.setOcrProvider(extraction.provider());
        receipt.setOcrModelVersion(truncate(extraction.modelVersion(), 64));
        receipt.setExtractedMerchant(merchant);
        receipt.setExtractedAmount(r.getAmount());
        receipt.setExtractedDate(r.getDate());
        receipt.setCurrency(r.getCurrency());
        receipt.setPredictedMerchant(merchant);
        receipt.setPredictedMerchantRaw(truncate(r.getMerchantRaw(), MERCHANT_COLUMN));
        receipt.setPredictedAmount(r.getAmount());
        receipt.setPredictedDate(r.getDate());
        receipt.setPredictedCurrency(r.getCurrency());
        Map<String, FieldAssessment> fields = r.getFields() != null ? r.getFields() : Map.of();
        receipt.setMerchantConfidence(confidence(fields.get(ReceiptParserService.FIELD_MERCHANT)));
        receipt.setAmountConfidence(confidence(fields.get(ReceiptParserService.FIELD_AMOUNT)));
        receipt.setDateConfidence(confidence(fields.get(ReceiptParserService.FIELD_DATE)));
        receipt.setCurrencyConfidence(confidence(fields.get(ReceiptParserService.FIELD_CURRENCY)));
        receipt.setOcrDecision(r.getDecision() != null ? r.getDecision().name() : null);
        if (r.getConfidence() != null) {
            receipt.setOcrConfidence(BigDecimal.valueOf(r.getConfidence()).setScale(3, RoundingMode.HALF_UP));
        }
        if (r.getLineItems() != null && !r.getLineItems().isEmpty()) {
            try {
                receipt.setLineItemsJson(objectMapper.writeValueAsString(r.getLineItems()));
            } catch (JsonProcessingException e) {
                log.debug("Could not serialize line items for receipt {}", receipt.getId());
            }
        }
        try {
            receipt.setExtractionDetails(objectMapper.writeValueAsString(r.toBuilder().rawText(null).lineItems(null).build()));
        } catch (JsonProcessingException e) {
            log.debug("Could not serialize extraction details for receipt {}", receipt.getId());
        }
    }

    private static BigDecimal confidence(FieldAssessment f) {
        return f == null || f.confidence() == null ? null : BigDecimal.valueOf(f.confidence()).setScale(3, RoundingMode.HALF_UP);
    }

    private static String truncate(String v, int max) {
        return v == null || v.length() <= max ? v : v.substring(0, max);
    }

    private OcrExtractionResult reconstructExtraction(Receipt receipt) {
        List<ReceiptLineItem> items = deserializeLineItems(receipt.getLineItemsJson());
        if (receipt.getExtractionDetails() != null) {
            try {
                OcrExtractionResult details = objectMapper.readValue(receipt.getExtractionDetails(), OcrExtractionResult.class);
                // extracted_* hold the confirmed values after confirmation
                return details.toBuilder()
                    .merchant(receipt.getExtractedMerchant())
                    .amount(receipt.getExtractedAmount())
                    .date(receipt.getExtractedDate())
                    .currency(receipt.getCurrency())
                    .rawText(receipt.getOcrText())
                    .lineItems(items)
                    .build();
            } catch (JsonProcessingException e) {
                log.debug("Could not read extraction details for receipt {}", receipt.getId());
            }
        }
        // structured data present when either field is set
        if (receipt.getExtractedAmount() != null || receipt.getExtractedMerchant() != null) {
            Double conf = receipt.getOcrConfidence() != null
                ? receipt.getOcrConfidence().doubleValue() : null;
            return OcrExtractionResult.builder()
                .merchant(receipt.getExtractedMerchant())
                .amount(receipt.getExtractedAmount())
                .date(receipt.getExtractedDate())
                // receipts read before V17 were always treated as INR
                .currency(receipt.getCurrency() != null ? receipt.getCurrency() : "INR")
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
            .currency(currencyFor(req, receipt))
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

    // the user's choice, else what OCR detected, else the column's historical default
    private static String currencyFor(ReceiptConfirmRequest req, Receipt receipt) {
        if (req.getCurrency() != null && !req.getCurrency().isBlank()) return req.getCurrency().trim().toUpperCase(java.util.Locale.ROOT);
        if (receipt.getCurrency() != null) return receipt.getCurrency();
        return "INR";
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
            .currency(receipt.getCurrency())
            .decision(extraction != null ? extraction.getDecision() : null)
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
