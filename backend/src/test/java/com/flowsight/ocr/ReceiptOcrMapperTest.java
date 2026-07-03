package com.flowsight.ocr;

import com.flowsight.dto.receipt.OcrExtractionResult;
import com.flowsight.dto.receipt.ReceiptLineItem;
import com.flowsight.dto.receipt.ReceiptOcrResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptOcrMapperTest {

    private ReceiptOcrMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ReceiptOcrMapper(new ReceiptAmountValidator());
    }

    // Happy path

    @Test
    void map_extractsMerchantAndAmount() {
        ReceiptOcrResponse response = ReceiptOcrResponse.builder()
            .merchantName("WALMART SUPERCENTER")
            .totalAmount(new BigDecimal("45.99"))
            .transactionDate("2024-03-15")
            .confidence(0.92)
            .build();

        OcrExtractionResult result = mapper.map(response);

        assertThat(result.getMerchant()).isEqualTo("WALMART SUPERCENTER");
        assertThat(result.getAmount()).isEqualByComparingTo("45.99");
        assertThat(result.isSuccessful()).isTrue();
        // No line items → MEDIUM confidence (validator score 0.50 → AmountConfidence.MEDIUM = 0.60)
        // The raw LLM confidence (0.92) is superseded by the amount validator result
        assertThat(result.getConfidence()).isEqualTo(AmountConfidence.MEDIUM.getNumericValue());
    }

    @Test
    void map_parsesIsoDate() {
        ReceiptOcrResponse response = ReceiptOcrResponse.builder()
            .merchantName("STARBUCKS")
            .totalAmount(new BigDecimal("5.50"))
            .transactionDate("2024-01-20")
            .build();

        assertThat(mapper.map(response).getDate()).isEqualTo(LocalDate.of(2024, 1, 20));
    }

    @Test
    void map_parsesSlashDate() {
        ReceiptOcrResponse response = ReceiptOcrResponse.builder()
            .totalAmount(new BigDecimal("12.00"))
            .transactionDate("20/03/2024")
            .build();

        assertThat(mapper.map(response).getDate()).isEqualTo(LocalDate.of(2024, 3, 20));
    }

    @Test
    void map_parsesMonthNameDate() {
        ReceiptOcrResponse response = ReceiptOcrResponse.builder()
            .totalAmount(new BigDecimal("8.00"))
            .transactionDate("5 Jan 2024")
            .build();

        assertThat(mapper.map(response).getDate()).isEqualTo(LocalDate.of(2024, 1, 5));
    }

    @Test
    void map_includesLineItems() {
        List<ReceiptLineItem> items = List.of(
            ReceiptLineItem.builder().itemName("Coffee").itemPrice(new BigDecimal("3.50")).build(),
            ReceiptLineItem.builder().itemName("Croissant").itemPrice(new BigDecimal("2.00")).build()
        );
        ReceiptOcrResponse response = ReceiptOcrResponse.builder()
            .merchantName("STARBUCKS")
            .totalAmount(new BigDecimal("5.50"))
            .lineItems(items)
            .build();

        OcrExtractionResult result = mapper.map(response);
        assertThat(result.getLineItems()).hasSize(2);
        assertThat(result.getLineItems().get(0).getItemName()).isEqualTo("Coffee");
    }

    @Test
    void map_populatesMerchantAddress() {
        ReceiptOcrResponse response = ReceiptOcrResponse.builder()
            .merchantName("ALDI")
            .merchantAddress("123 Main St, Springfield")
            .totalAmount(new BigDecimal("30.00"))
            .build();

        assertThat(mapper.map(response).getMerchantAddress()).isEqualTo("123 Main St, Springfield");
    }

    // Amount validation

    @Test
    void map_rejectsZeroAmount() {
        ReceiptOcrResponse response = ReceiptOcrResponse.builder()
            .merchantName("TEST")
            .totalAmount(BigDecimal.ZERO)
            .build();

        OcrExtractionResult result = mapper.map(response);
        assertThat(result.getAmount()).isNull();
        assertThat(result.isSuccessful()).isFalse();
    }

    @Test
    void map_rejectsNegativeAmount() {
        ReceiptOcrResponse response = ReceiptOcrResponse.builder()
            .merchantName("TEST")
            .totalAmount(new BigDecimal("-10.00"))
            .build();

        assertThat(mapper.map(response).isSuccessful()).isFalse();
    }

    @Test
    void map_marksUnsuccessfulWhenAmountNull() {
        ReceiptOcrResponse response = ReceiptOcrResponse.builder()
            .merchantName("SOME STORE")
            .build();

        assertThat(mapper.map(response).isSuccessful()).isFalse();
    }

    // Date validation

    @Test
    void map_rejectsFutureDate() {
        ReceiptOcrResponse response = ReceiptOcrResponse.builder()
            .totalAmount(new BigDecimal("10.00"))
            .transactionDate("2099-01-01")
            .build();

        assertThat(mapper.map(response).getDate()).isNull();
    }

    @Test
    void map_handlesUnparsableDate() {
        ReceiptOcrResponse response = ReceiptOcrResponse.builder()
            .totalAmount(new BigDecimal("10.00"))
            .transactionDate("not-a-date")
            .build();

        assertThat(mapper.map(response).getDate()).isNull();
    }

    // Text sanitization

    @Test
    void sanitizeText_stripsControlChars() {
        String withControl = "WALMART STORE";
        String result = mapper.sanitizeText(withControl, 200);
        assertThat(result).isEqualTo("WALMART STORE");
    }

    @Test
    void sanitizeText_truncatesAtMaxLen() {
        String long200 = "A".repeat(250);
        String result = mapper.sanitizeText(long200, 200);
        assertThat(result).hasSize(200);
    }

    @Test
    void sanitizeText_returnsNullForBlank() {
        assertThat(mapper.sanitizeText("   ", 200)).isNull();
        assertThat(mapper.sanitizeText(null, 200)).isNull();
    }

    // Confidence clamping

    @ParameterizedTest
    @ValueSource(doubles = {1.5, 2.0, 99.9})
    void clampConfidence_clampsAbove1(double raw) {
        assertThat(mapper.clampConfidence(raw)).isLessThanOrEqualTo(1.0);
    }

    @ParameterizedTest
    @ValueSource(doubles = {-0.1, -1.0, -100.0})
    void clampConfidence_clampsBelow0(double raw) {
        assertThat(mapper.clampConfidence(raw)).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    void clampConfidence_returnsNullForNull() {
        assertThat(mapper.clampConfidence(null)).isNull();
    }

    // Null safety

    @Test
    void map_handlesAllNullFields() {
        ReceiptOcrResponse response = ReceiptOcrResponse.builder().build();
        OcrExtractionResult result = mapper.map(response);

        assertThat(result.getMerchant()).isNull();
        assertThat(result.getAmount()).isNull();
        assertThat(result.getDate()).isNull();
        assertThat(result.isSuccessful()).isFalse();
        assertThat(result.getLineItems()).isEmpty();
    }
}
