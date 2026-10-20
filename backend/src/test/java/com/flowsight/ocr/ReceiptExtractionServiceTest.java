package com.flowsight.ocr;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowsight.dto.receipt.FieldAssessment;
import com.flowsight.dto.receipt.OcrExtractionResult;
import com.flowsight.dto.receipt.ReceiptAmountOption;
import com.flowsight.dto.receipt.ReceiptDecision;
import com.flowsight.dto.receipt.ReceiptOcrResponse;
import com.flowsight.ml.MlCapability;
import com.flowsight.ml.MlClient;
import com.flowsight.ml.MlOutcome;
import com.flowsight.ml.MlPrediction;
import com.flowsight.ml.PredictionContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ReceiptExtractionServiceTest {

    private final MlClient mlClient = mock(MlClient.class);
    private final ReceiptOcrClientService receiptOcr = mock(ReceiptOcrClientService.class);
    private final TesseractReceiptPipeline tesseract = mock(TesseractReceiptPipeline.class);
    private final ReceiptExtractionService service = new ReceiptExtractionService(
        mlClient, receiptOcr, new ReceiptOcrMapper(new ReceiptAmountValidator()), tesseract);

    private static OcrExtractionResult tesseractResult(String amount, ReceiptDecision amountDecision, String text) {
        Map<String, FieldAssessment> fields = new LinkedHashMap<>();
        fields.put("merchant", FieldAssessment.of(0.9, "heuristic"));
        fields.put("amount", amountDecision == ReceiptDecision.ACCEPT ? FieldAssessment.of(0.95, "arithmetic+total-label")
            : FieldAssessment.of(0.55, "total-label"));
        fields.put("date", FieldAssessment.of(0.95, "labelled"));
        fields.put("currency", FieldAssessment.of(0.97, "symbol"));
        return OcrExtractionResult.builder().merchant("LULU HYPERMARKET").amount(amount == null ? null : new BigDecimal(amount))
            .date(LocalDate.of(2024, 3, 9)).currency("AED").rawText(text).lineItems(List.of()).fields(fields)
            .amountCandidates(amount == null ? List.of() : List.of(new ReceiptAmountOption(new BigDecimal(amount), "Total", "x")))
            .decision(ReceiptDecision.ACCEPT).build();
    }

    private static OcrExtractionResult llm(String amount) {
        return new ReceiptOcrMapper(new ReceiptAmountValidator()).map(ReceiptOcrResponse.builder()
            .merchantName("Lulu Hypermarket").totalAmount(new BigDecimal(amount)).transactionDate("2024-03-09").build());
    }

    @Test
    void withoutMlOrVisionServiceTesseractAnswers(@TempDir Path dir) throws Exception {
        Path img = Files.write(dir.resolve("r.jpg"), new byte[]{1});
        OcrExtractionResult tess = tesseractResult("36.19", ReceiptDecision.ACCEPT, "Total AED 36.19");
        when(mlClient.isActive(MlCapability.OCR)).thenReturn(false);
        when(receiptOcr.call(img)).thenReturn(ReceiptOcrClientService.Call.failed("not configured"));
        when(tesseract.extract(img)).thenReturn(tess);

        ReceiptExtractionService.Extraction e = service.extract(img, "image/jpeg", PredictionContext.NONE);

        assertThat(e.provider()).isEqualTo(ReceiptExtractionService.PROVIDER_TESSERACT);
        assertThat(e.result()).isSameAs(tess);
        verify(mlClient, never()).predict(any(), any(), any(), any(), any());
    }

    @Test
    void mlServiceResultIsUsedAndRevalidated(@TempDir Path dir) throws Exception {
        Path img = Files.write(dir.resolve("r.jpg"), new byte[]{1, 2, 3});
        MlOcrResult ml = new MlOcrResult("Lulu Hypermarket", "LULU HYPERMARKET", new BigDecimal("36.19"), "2999-01-01", "aed",
            null, List.of(), List.of(), null, null, List.of(), List.of(),
            Map.of("amount", new MlOcrResult.Field(0.97, "ACCEPT", "arithmetic")), "ACCEPT", "Total AED 36.19",
            "unavailable: HTTP 404");
        when(mlClient.isActive(MlCapability.OCR)).thenReturn(true);
        when(mlClient.predict(eq(MlCapability.OCR), any(MlOcrRequest.class), eq(MlOcrResult.class), any(), any()))
            .thenReturn(new MlPrediction<>(ml, MlPrediction.Source.MODEL, "ocr-v1", 0.9));

        ReceiptExtractionService.Extraction e = service.extract(img, "image/jpeg", PredictionContext.NONE);

        assertThat(e.provider()).isEqualTo(ReceiptExtractionService.PROVIDER_ML);
        assertThat(e.modelVersion()).isEqualTo("ocr-v1");
        assertThat(e.result().getAmount()).isEqualByComparingTo("36.19");
        assertThat(e.result().getCurrency()).isEqualTo("AED");
        // a future date from the model is dropped and asked for
        assertThat(e.result().getDate()).isNull();
        assertThat(e.result().getFields().get("date").decision()).isEqualTo(ReceiptDecision.ASK_USER);
        assertThat(e.result().getEngineAttempts())
            .containsExactly("ML_SERVICE: ok (vision: unavailable: HTTP 404)", "TESSERACT: cross-check");
    }

    @Test
    void mlServiceFailureFallsBackToTesseract(@TempDir Path dir) throws Exception {
        Path img = Files.write(dir.resolve("r.jpg"), new byte[]{1});
        when(mlClient.isActive(MlCapability.OCR)).thenReturn(true);
        when(mlClient.predict(any(), any(), any(), any(), any()))
            .thenReturn(new MlPrediction<>(null, MlPrediction.Source.RULES, null, null, MlOutcome.REJECTED));
        when(receiptOcr.call(img)).thenReturn(ReceiptOcrClientService.Call.failed("HTTP 500"));
        when(tesseract.extract(img)).thenReturn(tesseractResult("36.19", ReceiptDecision.ACCEPT, ""));

        ReceiptExtractionService.Extraction e = service.extract(img, "image/jpeg", PredictionContext.NONE);

        assertThat(e.provider()).isEqualTo(ReceiptExtractionService.PROVIDER_TESSERACT);
        assertThat(e.result().getEngineAttempts())
            .containsExactly("ML_SERVICE: REJECTED", "RECEIPT_OCR_SERVICE: HTTP 500", "TESSERACT: ok");
    }

    private static OcrExtractionResult engine(String merchant, double merchantConf, String amount, double amountConf) {
        Map<String, FieldAssessment> fields = new LinkedHashMap<>();
        fields.put("merchant", FieldAssessment.of(merchantConf, "m"));
        fields.put("amount", FieldAssessment.of(amountConf, "a"));
        fields.put("date", FieldAssessment.of(0.95, "d"));
        fields.put("currency", FieldAssessment.of(0.97, "c"));
        return OcrExtractionResult.builder().merchant(merchant).amount(amount == null ? null : new BigDecimal(amount))
            .date(LocalDate.of(2024, 3, 9)).currency("AED").lineItems(List.of()).fields(fields).amountCandidates(List.of())
            .decision(ReceiptDecision.ACCEPT).build();
    }

    @Test
    void fusionTakesEachFieldFromTheEngineMoreSureOfIt() {
        OcrExtractionResult r = ReceiptExtractionService.fuse(
            engine("Lulu", 0.70, "36.19", 0.97), engine("Lulu Hypermarket", 0.92, "36.19", 0.80));

        assertThat(r.getMerchant()).isEqualTo("Lulu Hypermarket");
        assertThat(r.getAmount()).isEqualByComparingTo("36.19");
        assertThat(r.getFields().get("amount").confidence()).isEqualTo(0.97);
        // the engines read the merchant differently, so it is shown for review rather than accepted
        assertThat(r.getFields().get("merchant").decision()).isEqualTo(ReceiptDecision.ACCEPT_WITH_HIGHLIGHT);
        assertThat(r.getDecision()).isEqualTo(ReceiptDecision.ACCEPT_WITH_HIGHLIGHT);
    }

    @Test
    void confidentEnginesThatDisagreeOnTheTotalAskTheUser() {
        OcrExtractionResult r = ReceiptExtractionService.fuse(
            engine("Lulu", 0.92, "36.19", 0.95), engine("Lulu", 0.92, "63.19", 0.90));

        assertThat(r.getFields().get("amount").decision()).isEqualTo(ReceiptDecision.ASK_USER);
        assertThat(r.getAmountCandidates()).extracting(o -> o.amount().toPlainString()).contains("63.19");
        assertThat(r.isRequiresConfirmation()).isTrue();
    }

    @Test
    void aWeakDissentingTotalDoesNotForceAQuestion() {
        OcrExtractionResult r = ReceiptExtractionService.fuse(
            engine("Lulu", 0.92, "36.19", 0.95), engine("Lulu", 0.92, "3.19", 0.40));

        assertThat(r.getAmount()).isEqualByComparingTo("36.19");
        assertThat(r.getFields().get("amount").decision()).isEqualTo(ReceiptDecision.ACCEPT_WITH_HIGHLIGHT);
    }

    @Test
    void aFieldOnlyOneEngineReadIsKept() {
        OcrExtractionResult r = ReceiptExtractionService.fuse(
            engine(null, 0.0, "36.19", 0.95), engine("Lulu", 0.60, null, 0.0));

        assertThat(r.getMerchant()).isEqualTo("Lulu");
        assertThat(r.getAmount()).isEqualByComparingTo("36.19");
    }

    @Test
    void visionAndTesseractAgreeing() {
        OcrExtractionResult r = ReceiptExtractionService.crossCheck(llm("36.19"),
            tesseractResult("36.19", ReceiptDecision.ACCEPT, "Total AED 36.19"));

        assertThat(r.getFields().get("amount").decision()).isEqualTo(ReceiptDecision.ACCEPT);
        assertThat(r.getCurrency()).isEqualTo("AED");
    }

    @Test
    void conflictingTotalsAreOfferedNotPicked() {
        OcrExtractionResult r = ReceiptExtractionService.crossCheck(llm("63.19"),
            tesseractResult("36.19", ReceiptDecision.ACCEPT, "Total AED 36.19"));

        assertThat(r.getFields().get("amount").decision()).isEqualTo(ReceiptDecision.ASK_USER);
        assertThat(r.isRequiresConfirmation()).isTrue();
        assertThat(r.getAmountCandidates()).extracting(o -> o.amount().toPlainString()).contains("63.19", "36.19");
    }

    @Test
    void visionTotalTesseractNeverReadStaysASuggestion() {
        OcrExtractionResult r = ReceiptExtractionService.crossCheck(llm("63.19"),
            tesseractResult(null, ReceiptDecision.ASK_USER, "garbled"));

        assertThat(r.getAmount()).isEqualByComparingTo("63.19");
        assertThat(r.getFields().get("amount").decision()).isNotEqualTo(ReceiptDecision.ACCEPT);
    }

    @Test
    void mlResultParsesStringAmounts() throws Exception {
        MlOcrResult r = new ObjectMapper().readValue(
            "{\"merchant\":\"Lulu\",\"amount\":\"1,234.50\",\"line_items\":[{\"name\":\"Dates\",\"price\":\"١٢٫٠٠\"}]}", MlOcrResult.class);

        assertThat(r.amount()).isEqualByComparingTo("1234.50");
        assertThat(r.lineItems().get(0).price()).isEqualByComparingTo("12.00");
    }
}
