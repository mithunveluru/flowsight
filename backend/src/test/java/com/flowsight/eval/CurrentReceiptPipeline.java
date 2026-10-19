package com.flowsight.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowsight.exception.OcrException;
import com.flowsight.ml.MlCapability;
import com.flowsight.ml.MlClient;
import com.flowsight.ml.MlProperties;
import com.flowsight.ml.PredictionContext;
import com.flowsight.ml.PredictionLogService;
import com.flowsight.ocr.GroqAIProvider;
import com.flowsight.ocr.MerchantExtractor;
import com.flowsight.ocr.OcrPreprocessor;
import com.flowsight.ocr.OcrService;
import com.flowsight.ocr.ReceiptAmountValidator;
import com.flowsight.ocr.ReceiptExtractionService;
import com.flowsight.ocr.ReceiptOcrClientService;
import com.flowsight.ocr.ReceiptOcrMapper;
import com.flowsight.ocr.ReceiptParserService;
import com.flowsight.ocr.TesseractReceiptPipeline;
import org.mockito.Mockito;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

// The production receipt path, ReceiptService's call to ReceiptExtractionService, without the
// Receipt entity or the database: ml-service OCR when a URL and token are given, then the
// receipt-ocr service when its URL is given (cross-checked by Tesseract), else Tesseract alone.
// This is the only class to change when the eval should score a different pipeline.
final class CurrentReceiptPipeline {

    private final ReceiptExtractionService extraction;

    CurrentReceiptPipeline(String receiptOcrUrl, String groqApiKey, String mlServiceUrl, String mlServiceToken) {
        // same builder Spring Boot uses for the application ObjectMapper
        ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();
        boolean ml = !mlServiceUrl.isBlank();
        MlProperties props = new MlProperties(mlServiceUrl, ml ? mlServiceToken : "", Duration.ofSeconds(2), 1000,
            Duration.ofSeconds(1), Map.of(MlCapability.OCR.id(), new MlProperties.CapabilityFlags(ml, false, Duration.ofSeconds(90))));
        MlClient mlClient = new MlClient(props, objectMapper, Mockito.mock(PredictionLogService.class));
        OcrPreprocessor preprocessor = new OcrPreprocessor();
        TesseractReceiptPipeline tesseract = new TesseractReceiptPipeline(preprocessor, new OcrService(preprocessor),
            new ReceiptParserService(new MerchantExtractor(), new GroqAIProvider(groqApiKey, objectMapper)));
        this.extraction = new ReceiptExtractionService(mlClient,
            new ReceiptOcrClientService(receiptOcrUrl, objectMapper),
            new ReceiptOcrMapper(new ReceiptAmountValidator(), new MerchantExtractor()), tesseract);
    }

    ReceiptPrediction extract(Path image) {
        try {
            ReceiptExtractionService.Extraction e = extraction.extract(image, "image/jpeg", PredictionContext.NONE);
            return ReceiptPrediction.of(e.provider(), e.result());
        } catch (OcrException e) {
            return ReceiptPrediction.failed(e.getMessage());
        }
    }
}
