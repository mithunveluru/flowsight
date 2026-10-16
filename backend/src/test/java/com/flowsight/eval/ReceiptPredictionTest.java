package com.flowsight.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.flowsight.dto.receipt.OcrExtractionResult;
import com.flowsight.dto.receipt.ReceiptLineItem;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptPredictionTest {

    private final ObjectMapper json = new ObjectMapper()
        .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);

    @Test
    void serializesExtractionInScorerFormat() {
        OcrExtractionResult result = OcrExtractionResult.builder()
            .merchant("Lulu Hypermarket")
            .amount(new BigDecimal("1234.50"))
            .date(LocalDate.of(2024, 3, 9))
            .currency("INR")
            .lineItems(List.of(new ReceiptLineItem("Dates 1kg", "2", new BigDecimal("56.00"))))
            .confidence(0.85)
            .build();

        JsonNode node = json.valueToTree(ReceiptPrediction.of("RECEIPT_OCR_SERVICE", result));

        assertThat(node.get("provider").asText()).isEqualTo("RECEIPT_OCR_SERVICE");
        assertThat(node.get("merchant").asText()).isEqualTo("Lulu Hypermarket");
        assertThat(node.get("total").decimalValue()).isEqualByComparingTo("1234.50");
        assertThat(node.get("date").asText()).isEqualTo("2024-03-09");
        assertThat(node.get("currency").asText()).isEqualTo("INR");
        assertThat(node.get("line_items").get(0).get("name").asText()).isEqualTo("Dates 1kg");
        assertThat(node.get("line_items").get(0).get("price").decimalValue()).isEqualByComparingTo("56.00");
        assertThat(node.get("confidence").asDouble()).isEqualTo(0.85);
        assertThat(node.get("error").isNull()).isTrue();
    }

    @Test
    void tesseractResultWithoutItemsOrDateHasNulls() {
        OcrExtractionResult result = OcrExtractionResult.builder().merchant("SHELL").build();

        JsonNode node = json.valueToTree(ReceiptPrediction.of("TESSERACT_FALLBACK", result));

        assertThat(node.get("line_items").isEmpty()).isTrue();
        assertThat(node.get("date").isNull()).isTrue();
        assertThat(node.get("total").isNull()).isTrue();
        assertThat(node.get("confidence").isNull()).isTrue();
    }

    @Test
    void failureCarriesTheError() {
        JsonNode node = json.valueToTree(ReceiptPrediction.failed("Tesseract exited 1"));

        assertThat(node.get("provider").asText()).isEqualTo("FAILED");
        assertThat(node.get("error").asText()).isEqualTo("Tesseract exited 1");
    }
}
