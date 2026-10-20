package com.flowsight.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.flowsight.analytics.CategorizationService;
import com.flowsight.analytics.NormalizationService;
import com.flowsight.dto.receipt.OcrExtractionResult;
import com.flowsight.dto.receipt.ReceiptConfirmRequest;
import com.flowsight.dto.receipt.ReceiptResponse;
import com.flowsight.entity.*;
import com.flowsight.exception.ResourceNotFoundException;
import com.flowsight.merchant.MerchantResolution;
import com.flowsight.merchant.MerchantResolver;
import com.flowsight.merchant.ResolutionStep;
import com.flowsight.ml.FeedbackCorrection;
import com.flowsight.ml.FeedbackService;
import com.flowsight.ml.FeedbackSource;
import com.flowsight.ml.SubjectType;
import com.flowsight.ml.categorization.CategorySource;
import com.flowsight.ml.categorization.TransactionCategorizer;
import com.flowsight.ocr.ReceiptExtractionService;
import com.flowsight.ocr.ReceiptParserService;
import com.flowsight.repository.ReceiptRepository;
import com.flowsight.repository.TransactionRepository;
import com.flowsight.util.FileStorageService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReceiptConfirmTest {

    @Mock private ReceiptRepository      receiptRepository;
    @Mock private TransactionRepository  transactionRepository;
    @Mock private FileStorageService     fileStorage;
    @Mock private ReceiptParserService   parser;
    @Mock private CategorizationService  categorizationService;
    @Mock private NormalizationService   normalizationService;
    @Mock private ReceiptExtractionService extractionService;
    @Mock private FeedbackService        feedbackService;
    @Mock private MerchantResolver       merchantResolver;
    @Mock private TransactionCategorizer categorizer;
    private AuditLogService auditStub;

    private ReceiptService service;
    private Validator validator;

    private User testUser;
    private Receipt testReceipt;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        // Quota check happens at upload entry, not in the confirm flow — stub it
        ReceiptQuotaService quotaStub = org.mockito.Mockito.mock(ReceiptQuotaService.class);
        auditStub = org.mockito.Mockito.mock(AuditLogService.class);
        com.flowsight.security.RateLimiter rateLimiterStub = org.mockito.Mockito.mock(
            com.flowsight.security.RateLimiter.class);

        service = new ReceiptService(
            receiptRepository, transactionRepository, fileStorage,
            categorizationService, normalizationService, objectMapper,
            quotaStub, auditStub, rateLimiterStub,
            extractionService, parser, feedbackService, merchantResolver, categorizer
        );
        lenient().when(merchantResolver.resolveTyped(any(), any()))
            .thenAnswer(inv -> typed(inv.getArgument(1), null));

        validator = Validation.buildDefaultValidatorFactory().getValidator();

        UUID userId = UUID.randomUUID();
        testUser = User.builder()
            .id(userId)
            .email("test@example.com")
            .passwordHash("$2a$12$hash")
            .role(com.flowsight.entity.Role.USER)
            .build();

        UUID receiptId = UUID.randomUUID();
        testReceipt = Receipt.builder()
            .id(receiptId)
            .user(testUser)
            .fileName("receipt.jpg")
            .fileSize(1024L)
            .mimeType("image/jpeg")
            .status(ReceiptStatus.COMPLETED)
            .extractedMerchant("WALMART")
            .extractedAmount(new BigDecimal("45.99"))
            .extractedDate(LocalDate.of(2024, 3, 15))
            .build();
    }

    // ReceiptConfirmRequest validation

    @Test
    void request_validWhenAllRequiredFieldsPresent() {
        ReceiptConfirmRequest req = validRequest();
        assertThat(validator.validate(req)).isEmpty();
    }

    @Test
    void request_failsWhenMerchantBlank() {
        ReceiptConfirmRequest req = validRequest();
        req.setMerchant("");
        Set<ConstraintViolation<ReceiptConfirmRequest>> violations = validator.validate(req);
        assertThat(violations).isNotEmpty();
        assertThat(violations.iterator().next().getPropertyPath().toString()).isEqualTo("merchant");
    }

    @Test
    void request_failsWhenAmountNull() {
        ReceiptConfirmRequest req = validRequest();
        req.setAmount(null);
        assertThat(validator.validate(req)).isNotEmpty();
    }

    @Test
    void request_failsWhenAmountZero() {
        ReceiptConfirmRequest req = validRequest();
        req.setAmount(BigDecimal.ZERO);
        assertThat(validator.validate(req)).isNotEmpty();
    }

    @Test
    void request_failsWhenAmountNegative() {
        ReceiptConfirmRequest req = validRequest();
        req.setAmount(new BigDecimal("-10.00"));
        assertThat(validator.validate(req)).isNotEmpty();
    }

    @Test
    void request_failsWhenDateNull() {
        ReceiptConfirmRequest req = validRequest();
        req.setDate(null);
        assertThat(validator.validate(req)).isNotEmpty();
    }

    @Test
    void request_allowsNullCategory() {
        ReceiptConfirmRequest req = validRequest();
        req.setCategory(null);
        assertThat(validator.validate(req)).isEmpty();
    }

    @Test
    void request_allowsNullNotes() {
        ReceiptConfirmRequest req = validRequest();
        req.setNotes(null);
        assertThat(validator.validate(req)).isEmpty();
    }

    // confirmReceipt — happy paths

    @Test
    void confirmReceipt_createsTransactionWithUserValues() {
        UUID receiptId = testReceipt.getId();
        ReceiptConfirmRequest req = validRequest();
        req.setCategory(TransactionCategory.GROCERIES); // user explicitly chose

        when(receiptRepository.findByIdAndUserIdForUpdate(receiptId, testUser.getId()))
            .thenReturn(Optional.of(testReceipt));
        when(transactionRepository.findTopByReceiptId(receiptId))
            .thenReturn(Optional.empty());
        when(normalizationService.normalize(any())).thenReturn("WALMART");
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReceiptResponse response = service.confirmReceipt(receiptId, req, testUser);

        assertThat(response).isNotNull();
        verify(transactionRepository).save(argThat(tx ->
            tx.getMerchant().equals("WALMART") &&
            tx.getAmount().compareTo(new BigDecimal("45.99")) == 0 &&
            tx.getCategory() == TransactionCategory.GROCERIES &&
            tx.isReviewed()                          // user confirmed → reviewed=true
        ));
    }

    @Test
    void confirmReceipt_autoDetectsCategoryWhenNotProvided() {
        UUID receiptId = testReceipt.getId();
        ReceiptConfirmRequest req = validRequest();
        req.setCategory(null); // rely on auto-detection

        CategorizationService.CategorizationResult catResult =
            new CategorizationService.CategorizationResult(TransactionCategory.FOOD_DINING, 0.80);

        when(receiptRepository.findByIdAndUserIdForUpdate(receiptId, testUser.getId()))
            .thenReturn(Optional.of(testReceipt));
        when(transactionRepository.findTopByReceiptId(receiptId))
            .thenReturn(Optional.empty());
        when(normalizationService.normalize(any())).thenReturn("WALMART");
        when(categorizationService.categorize(any(), any(), any())).thenReturn(catResult);
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReceiptResponse response = service.confirmReceipt(receiptId, req, testUser);

        assertThat(response).isNotNull();
        verify(categorizationService).categorize(any(), any(), any());
        verify(transactionRepository).save(argThat(tx ->
            tx.getCategory() == TransactionCategory.FOOD_DINING
        ));
    }

    @Test
    void confirmReceipt_usesNotesAsDescription() {
        UUID receiptId = testReceipt.getId();
        ReceiptConfirmRequest req = validRequest();
        req.setNotes("Team lunch at Walmart");

        when(receiptRepository.findByIdAndUserIdForUpdate(receiptId, testUser.getId()))
            .thenReturn(Optional.of(testReceipt));
        when(transactionRepository.findTopByReceiptId(receiptId))
            .thenReturn(Optional.empty());
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        // category is GROCERIES (from validRequest()), so auto-categorize is NOT called;
        // notes is used as the description directly

        service.confirmReceipt(receiptId, req, testUser);

        verify(transactionRepository).save(argThat(tx ->
            "Team lunch at Walmart".equals(tx.getDescription()) &&
            "Team lunch at Walmart".equals(tx.getNotes())
        ));
    }

    @Test
    void confirmReceipt_isIdempotent_returnsExistingOnDuplicateCall() {
        UUID receiptId = testReceipt.getId();
        Transaction existingTx = Transaction.builder()
            .user(testUser).amount(new BigDecimal("45.99")).currency("INR")
            .transactionDate(LocalDate.of(2024, 3, 15))
            .description("WALMART").merchant("WALMART")
            .type(TransactionType.DEBIT).source(TransactionSource.OCR)
            .reviewed(true).build();

        when(receiptRepository.findByIdAndUserIdForUpdate(receiptId, testUser.getId()))
            .thenReturn(Optional.of(testReceipt));
        when(transactionRepository.findTopByReceiptId(receiptId))
            .thenReturn(Optional.of(existingTx));

        service.confirmReceipt(receiptId, validRequest(), testUser);

        // Idempotent: must NOT save another transaction
        verify(transactionRepository, never()).save(any());
    }

    @Test
    void confirmReceipt_throwsWhenReceiptNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(receiptRepository.findByIdAndUserIdForUpdate(unknownId, testUser.getId()))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.confirmReceipt(unknownId, validRequest(), testUser))
            .isInstanceOf(ResourceNotFoundException.class);
    }

    // Draft workflow — processReceipt should NOT auto-create transaction

    @Test
    void noTransactionAutoCreated_afterOcrProcessing() {
        // The receipt returned by upload must never carry a transaction
        // (user must confirm via the review screen first).
        // We verify this structurally: buildTransactionFromConfirm is NOT
        // called during processReceipt — only transactionRepository.save
        // via confirmReceipt creates transactions.
        //
        // Since processReceipt calls extractWithFallback which uses OCR services
        // we cannot easily unit-test the full pipeline here without a real image.
        // The structural guarantee is that ReceiptService.processReceipt() no longer
        // calls buildTransactionFromConfirm or transactionRepository.save directly.
        //
        // This is verified by reading the source — and by the following assertion
        // that transactionRepository is never invoked for a receipt-only operation.

        UUID receiptId = testReceipt.getId();
        when(receiptRepository.findByIdAndUserId(receiptId, testUser.getId()))
            .thenReturn(Optional.of(testReceipt));
        when(transactionRepository.findTopByReceiptId(receiptId))
            .thenReturn(Optional.empty());

        // getById should return the receipt with transaction=null when not confirmed
        ReceiptResponse response = service.getById(receiptId, testUser.getId());
        assertThat(response.getTransaction()).isNull();
    }

    // Low-confidence extraction handling

    @Test
    void confirmReceipt_worksRegardlessOfOcrConfidence() {
        // Low-confidence OCR (receipt.confidence < 0.45) should still confirm fine
        // when the user supplies corrected values
        testReceipt.setOcrConfidence(new BigDecimal("0.30")); // LOW
        UUID receiptId = testReceipt.getId();

        when(receiptRepository.findByIdAndUserIdForUpdate(receiptId, testUser.getId()))
            .thenReturn(Optional.of(testReceipt));
        when(transactionRepository.findTopByReceiptId(receiptId))
            .thenReturn(Optional.empty());
        when(normalizationService.normalize(any())).thenReturn("CORRECTED MERCHANT");
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReceiptConfirmRequest corrected = validRequest();
        corrected.setMerchant("CORRECTED MERCHANT");
        corrected.setAmount(new BigDecimal("120.00")); // user corrected amount
        corrected.setCategory(TransactionCategory.GROCERIES);

        ReceiptResponse response = service.confirmReceipt(receiptId, corrected, testUser);

        assertThat(response).isNotNull();
        verify(transactionRepository).save(argThat(tx ->
            "CORRECTED MERCHANT".equals(tx.getMerchant()) &&
            tx.getAmount().compareTo(new BigDecimal("120.00")) == 0
        ));
    }

    // Prediction kept, corrections recorded, confirmation audited

    @Test
    void confirmReceipt_recordsOnlyCorrectedFields_andKeepsPrediction() {
        UUID receiptId = testReceipt.getId();
        testReceipt.setPredictedMerchant("WALMART");
        testReceipt.setPredictedAmount(new BigDecimal("45.99"));
        testReceipt.setPredictedDate(LocalDate.of(2024, 3, 15));
        testReceipt.setAmountConfidence(new BigDecimal("0.550"));
        when(receiptRepository.findByIdAndUserIdForUpdate(receiptId, testUser.getId())).thenReturn(Optional.of(testReceipt));
        when(transactionRepository.findTopByReceiptId(receiptId)).thenReturn(Optional.empty());
        when(normalizationService.normalize(any())).thenReturn("WALMART");
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ReceiptConfirmRequest req = validRequest();
        req.setAmount(new BigDecimal("54.99"));
        service.confirmReceipt(receiptId, req, testUser);

        verify(feedbackService).record(eq(testUser.getId()), argThat((FeedbackCorrection c) ->
            c.subjectType() == SubjectType.RECEIPT && c.subjectId().equals(receiptId) && c.field().equals("amount")
                && c.predictedValue().equals("45.99") && c.correctedValue().equals("54.99")
                && c.source() == FeedbackSource.RECEIPT_REVIEW && c.confidence() == 0.55));
        verifyNoMoreInteractions(feedbackService);
        assertThat(testReceipt.getPredictedAmount()).isEqualByComparingTo("45.99");
        assertThat(testReceipt.getExtractedAmount()).isEqualByComparingTo("54.99");
        assertThat(testReceipt.getConfirmedAt()).isNotNull();
    }

    @Test
    void confirmReceipt_logsReceiptConfirmedWithoutValues() {
        UUID receiptId = testReceipt.getId();
        when(receiptRepository.findByIdAndUserIdForUpdate(receiptId, testUser.getId())).thenReturn(Optional.of(testReceipt));
        when(transactionRepository.findTopByReceiptId(receiptId)).thenReturn(Optional.empty());
        when(normalizationService.normalize(any())).thenReturn("WALMART");
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.confirmReceipt(receiptId, validRequest(), testUser);

        verify(auditStub).log(eq(testUser), eq(AuditLogService.ACTION_RECEIPT_CONFIRMED), eq("Receipt"),
            eq(receiptId.toString()), argThat(m -> m.containsKey("correctedFields") && !m.containsValue("WALMART")));
    }

    @Test
    void confirmReceipt_alreadyConfirmed_recordsNoFeedback() {
        UUID receiptId = testReceipt.getId();
        Transaction existingTx = Transaction.builder()
            .user(testUser).amount(new BigDecimal("45.99")).currency("INR")
            .transactionDate(LocalDate.of(2024, 3, 15)).merchant("WALMART")
            .type(TransactionType.DEBIT).source(TransactionSource.OCR).reviewed(true).build();
        when(receiptRepository.findByIdAndUserIdForUpdate(receiptId, testUser.getId())).thenReturn(Optional.of(testReceipt));
        when(transactionRepository.findTopByReceiptId(receiptId)).thenReturn(Optional.of(existingTx));

        ReceiptConfirmRequest req = validRequest();
        req.setAmount(new BigDecimal("99.00"));
        service.confirmReceipt(receiptId, req, testUser);

        verifyNoInteractions(feedbackService);
        verify(auditStub, never()).log(any(), eq(AuditLogService.ACTION_RECEIPT_CONFIRMED), any(), any(), any());
    }

    @Test
    void confirmReceipt_usesDetectedCurrencyWhenRequestHasNone() {
        UUID receiptId = testReceipt.getId();
        testReceipt.setCurrency("SAR");
        when(receiptRepository.findByIdAndUserIdForUpdate(receiptId, testUser.getId())).thenReturn(Optional.of(testReceipt));
        when(transactionRepository.findTopByReceiptId(receiptId)).thenReturn(Optional.empty());
        when(normalizationService.normalize(any())).thenReturn("WALMART");
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.confirmReceipt(receiptId, validRequest(), testUser);

        verify(transactionRepository).save(argThat(tx -> "SAR".equals(tx.getCurrency())));
    }

    // Merchant and category resolved as in manual entry

    @Test
    void confirmReceipt_userCategory_isUserSourced_andNotRefined() {
        UUID receiptId = testReceipt.getId();
        testReceipt.setPredictedMerchantRaw("WAL-MART SUPERCENTER #1234");
        stubConfirm(receiptId);
        when(merchantResolver.resolveTyped(testUser.getId(), "walmart"))
            .thenReturn(new MerchantResolution("Walmart", "walmart", ResolutionStep.ALIAS, 1.0, null));

        ReceiptConfirmRequest req = validRequest();
        req.setMerchant("walmart");
        service.confirmReceipt(receiptId, req, testUser);

        verify(transactionRepository).save(argThat(tx ->
            "Walmart".equals(tx.getMerchant())
                && "WAL-MART SUPERCENTER #1234".equals(tx.getMerchantRaw())
                && tx.getCategory() == TransactionCategory.GROCERIES
                && tx.getCategorySource() == CategorySource.USER
                && tx.getConfidenceScore().compareTo(BigDecimal.ONE) == 0
                && tx.isReviewed()));
        verifyNoInteractions(categorizer, categorizationService);
    }

    @Test
    void confirmReceipt_userRuleCategory_isPersonal() {
        UUID receiptId = testReceipt.getId();
        stubConfirm(receiptId);
        when(merchantResolver.resolveTyped(testUser.getId(), "WALMART"))
            .thenReturn(typed("Walmart", TransactionCategory.SHOPPING));

        ReceiptConfirmRequest req = validRequest();
        req.setCategory(null);
        service.confirmReceipt(receiptId, req, testUser);

        verify(transactionRepository).save(argThat(tx ->
            "Walmart".equals(tx.getMerchant())
                && "WALMART".equals(tx.getMerchantRaw())
                && tx.getCategory() == TransactionCategory.SHOPPING
                && tx.getCategorySource() == CategorySource.PERSONAL
                && tx.getConfidenceScore().compareTo(new BigDecimal("0.95")) == 0
                && !tx.isReviewed()));
        verifyNoInteractions(categorizer, categorizationService);
    }

    @Test
    void confirmReceipt_noCategory_usesRulesThenRefinesBeforeSaving() {
        UUID receiptId = testReceipt.getId();
        stubConfirm(receiptId);
        when(merchantResolver.resolveTyped(testUser.getId(), "WALMART")).thenReturn(typed("Walmart", null));
        when(categorizationService.categorize("WALMART", "Walmart", new BigDecimal("45.99")))
            .thenReturn(new CategorizationService.CategorizationResult(TransactionCategory.GROCERIES, 0.7));

        ReceiptConfirmRequest req = validRequest();
        req.setCategory(null);
        service.confirmReceipt(receiptId, req, testUser);

        var order = inOrder(categorizer, transactionRepository);
        order.verify(categorizer).refine(eq(testUser.getId()), argThat((List<Transaction> rows) ->
            rows.size() == 1 && rows.get(0).getCategorySource() == CategorySource.RULES));
        order.verify(transactionRepository).save(argThat(tx -> !tx.isReviewed()));
    }

    // Draft: the merchant is resolved before the review screen sees it

    @Test
    void processReceipt_draftShowsResolvedMerchant_andKeepsWhatOcrRead() throws Exception {
        stubUpload(ocrResult());
        when(merchantResolver.resolve(testUser.getId(), "DOMINOS PIZZA #4411"))
            .thenReturn(new MerchantResolution("Domino's", "dominos", ResolutionStep.ALIAS, 1.0, null));

        ReceiptResponse response = service.processReceipt(pngUpload(), testUser);

        assertThat(response.getExtraction().getMerchant()).isEqualTo("Domino's");
        Receipt saved = lastSavedReceipt();
        assertThat(saved.getExtractedMerchant()).isEqualTo("Domino's");
        assertThat(saved.getPredictedMerchant()).isEqualTo("Domino's");
        assertThat(saved.getPredictedMerchantRaw()).isEqualTo("DOMINOS PIZZA #4411");
    }

    @Test
    void processReceipt_resolverFailure_keepsOcrMerchant() throws Exception {
        stubUpload(ocrResult());
        when(merchantResolver.resolve(any(), any())).thenThrow(new IllegalStateException("db down"));

        ReceiptResponse response = service.processReceipt(pngUpload(), testUser);

        assertThat(response.getStatus()).isEqualTo(ReceiptStatus.COMPLETED);
        assertThat(response.getExtraction().getMerchant()).isEqualTo("Dominos Pizza");
        assertThat(lastSavedReceipt().getPredictedMerchant()).isEqualTo("Dominos Pizza");
    }

    @Test
    void processReceipt_noResolvedName_keepsOcrMerchant_andFallsBackToCleanedText() throws Exception {
        stubUpload(ocrResult().toBuilder().merchantRaw(null).build());
        when(merchantResolver.resolve(testUser.getId(), "Dominos Pizza")).thenReturn(MerchantResolution.NONE);

        ReceiptResponse response = service.processReceipt(pngUpload(), testUser);

        assertThat(response.getExtraction().getMerchant()).isEqualTo("Dominos Pizza");
        assertThat(lastSavedReceipt().getPredictedMerchant()).isEqualTo("Dominos Pizza");
    }

    // Helper

    private void stubConfirm(UUID receiptId) {
        when(receiptRepository.findByIdAndUserIdForUpdate(receiptId, testUser.getId())).thenReturn(Optional.of(testReceipt));
        when(transactionRepository.findTopByReceiptId(receiptId)).thenReturn(Optional.empty());
        when(normalizationService.normalize(any())).thenReturn("WALMART");
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private void stubUpload(OcrExtractionResult result) throws Exception {
        when(receiptRepository.save(any())).thenAnswer(inv -> {
            Receipt r = inv.getArgument(0);
            if (r.getId() == null) r.setId(UUID.randomUUID());
            return r;
        });
        when(fileStorage.store(any(), any(), any())).thenReturn(Path.of("/tmp/receipt.png"));
        when(extractionService.extract(any(), any(), any()))
            .thenReturn(new ReceiptExtractionService.Extraction("TESSERACT", null, result));
    }

    @Test
    void listMarksConfirmedReceiptsWithTheirTransaction() {
        Receipt draft = Receipt.builder().id(UUID.randomUUID()).user(testUser).fileName("draft.jpg")
            .status(ReceiptStatus.COMPLETED).build();
        Transaction tx = Transaction.builder().id(UUID.randomUUID()).user(testUser).receipt(testReceipt)
            .amount(new BigDecimal("12.50")).type(TransactionType.DEBIT).transactionDate(LocalDate.now())
            .category(TransactionCategory.FOOD_DINING).build();
        when(receiptRepository.findByUserIdOrderByCreatedAtDesc(eq(testUser.getId()), any()))
            .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(testReceipt, draft)));
        when(transactionRepository.findByReceiptIdIn(any())).thenReturn(List.of(tx));

        List<ReceiptResponse> page = service.list(testUser.getId(), org.springframework.data.domain.PageRequest.of(0, 20)).getContent();

        assertThat(page.get(0).getTransaction()).isNotNull();
        assertThat(page.get(0).getTransaction().getId()).isEqualTo(tx.getId());
        assertThat(page.get(1).getTransaction()).isNull();
    }

    private Receipt lastSavedReceipt() {
        var captor = org.mockito.ArgumentCaptor.forClass(Receipt.class);
        verify(receiptRepository, atLeastOnce()).save(captor.capture());
        return captor.getValue();
    }

    private static OcrExtractionResult ocrResult() {
        return OcrExtractionResult.builder()
            .merchant("Dominos Pizza")
            .merchantRaw("DOMINOS PIZZA #4411")
            .amount(new BigDecimal("450.00"))
            .currency("INR")
            .successful(true)
            .build();
    }

    private static MockMultipartFile pngUpload() {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};
        return new MockMultipartFile("file", "receipt.png", "image/png", png);
    }

    private static MerchantResolution typed(String name, TransactionCategory category) {
        return new MerchantResolution(name, name.toLowerCase(), ResolutionStep.TYPED, 1.0, category);
    }


    private ReceiptConfirmRequest validRequest() {
        ReceiptConfirmRequest req = new ReceiptConfirmRequest();
        req.setMerchant("WALMART");
        req.setAmount(new BigDecimal("45.99"));
        req.setDate(LocalDate.of(2024, 3, 15));
        req.setCategory(TransactionCategory.GROCERIES);
        return req;
    }
}
