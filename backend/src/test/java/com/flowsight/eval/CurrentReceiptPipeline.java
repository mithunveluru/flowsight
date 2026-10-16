package com.flowsight.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowsight.dto.receipt.ReceiptOcrResponse;
import com.flowsight.exception.OcrException;
import com.flowsight.ocr.GroqAIProvider;
import com.flowsight.ocr.MerchantExtractor;
import com.flowsight.ocr.OcrDocument;
import com.flowsight.ocr.OcrPreprocessor;
import com.flowsight.ocr.OcrService;
import com.flowsight.ocr.ReceiptAmountValidator;
import com.flowsight.ocr.ReceiptOcrClientService;
import com.flowsight.ocr.ReceiptOcrMapper;
import com.flowsight.ocr.ReceiptParserService;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import java.lang.reflect.Field;
import java.net.http.HttpClient;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;

// Mirrors ReceiptService.extractWithFallback without the Receipt entity or the database:
// receipt-ocr + ReceiptOcrMapper first, then Tesseract + ReceiptParserService.
// This is the only class to change when the eval should score a different pipeline.
final class CurrentReceiptPipeline {

    private final ReceiptOcrClientService receiptOcrClient;
    private final ReceiptOcrMapper ocrMapper;
    private final OcrService ocrService;
    private final ReceiptParserService parser;

    CurrentReceiptPipeline(String receiptOcrUrl, String groqApiKey, boolean forceHttp11) {
        // same builder Spring Boot uses for the application ObjectMapper
        ObjectMapper objectMapper = Jackson2ObjectMapperBuilder.json().build();
        this.receiptOcrClient = new ReceiptOcrClientService(receiptOcrUrl, objectMapper);
        if (forceHttp11) useHttp11(receiptOcrClient);
        this.ocrMapper = new ReceiptOcrMapper(new ReceiptAmountValidator());
        this.ocrService = new OcrService(new OcrPreprocessor());
        this.parser = new ReceiptParserService(
            new MerchantExtractor(), new GroqAIProvider(groqApiKey, objectMapper));
    }

    ReceiptPrediction extract(Path image) {
        Optional<ReceiptOcrResponse> ocrResponse = receiptOcrClient.extract(image);
        if (ocrResponse.isPresent()) {
            return ReceiptPrediction.of("RECEIPT_OCR_SERVICE", ocrMapper.map(ocrResponse.get()));
        }
        try {
            OcrDocument document = ocrService.extractDocument(image);
            return ReceiptPrediction.of("TESSERACT_FALLBACK", parser.parse(document));
        } catch (OcrException e) {
            return ReceiptPrediction.failed(e.getMessage());
        }
    }

    // Diagnostic only: the production client defaults to HTTP/2, whose h2c upgrade uvicorn
    // rejects with 422, so it never gets an LLM answer. This measures the LLM itself.
    private static void useHttp11(ReceiptOcrClientService client) {
        try {
            Field field = ReceiptOcrClientService.class.getDeclaredField("httpClient");
            field.setAccessible(true);
            field.set(client, HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(10))
                .build());
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("cannot swap the receipt-ocr HttpClient", e);
        }
    }
}
