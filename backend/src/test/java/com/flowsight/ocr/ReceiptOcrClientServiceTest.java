package com.flowsight.ocr;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowsight.dto.receipt.ReceiptLineItem;
import com.flowsight.dto.receipt.ReceiptOcrResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ReceiptOcrClientServiceTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
    }

    // Disabled when URL is blank

    @Test
    void extract_returnsEmptyWhenUrlIsBlank() {
        ReceiptOcrClientService client = new ReceiptOcrClientService("", mapper);
        assertThat(client.extract(Path.of("/any/path.jpg"))).isEmpty();
    }

    @Test
    void extract_returnsEmptyWhenUrlIsWhitespace() {
        ReceiptOcrClientService client = new ReceiptOcrClientService("   ", mapper);
        assertThat(client.extract(Path.of("/any/path.jpg"))).isEmpty();
    }

    // parseAndValidate

    @Test
    void parseAndValidate_returnsResponseWhenMerchantPresent() {
        ReceiptOcrClientService client = new ReceiptOcrClientService("http://localhost:8000", mapper);
        String json = """
            {
              "merchant_name": "WALMART",
              "total_amount": 45.99,
              "transaction_date": "2024-03-15"
            }
            """;

        Optional<ReceiptOcrResponse> result = client.parseAndValidate(json);
        assertThat(result).isPresent();
        assertThat(result.get().getMerchantName()).isEqualTo("WALMART");
        assertThat(result.get().getTotalAmount()).isEqualByComparingTo("45.99");
    }

    @Test
    void parseAndValidate_returnsResponseWhenOnlyAmountPresent() {
        ReceiptOcrClientService client = new ReceiptOcrClientService("http://localhost:8000", mapper);
        String json = """
            {"total_amount": 12.50}
            """;

        Optional<ReceiptOcrResponse> result = client.parseAndValidate(json);
        assertThat(result).isPresent();
        assertThat(result.get().getTotalAmount()).isEqualByComparingTo("12.50");
    }

    @Test
    void parseAndValidate_returnsEmptyWhenBothMerchantAndAmountNull() {
        ReceiptOcrClientService client = new ReceiptOcrClientService("http://localhost:8000", mapper);
        String json = """
            {"transaction_date": "2024-01-01", "transaction_time": "10:30"}
            """;

        assertThat(client.parseAndValidate(json)).isEmpty();
    }

    @Test
    void parseAndValidate_returnsEmptyWhenMerchantEmptyStringAndAmountZero() {
        // receipt-ocr returns "" and 0 for blank/unreadable images — treat as no data
        ReceiptOcrClientService client = new ReceiptOcrClientService("http://localhost:8000", mapper);
        String json = """
            {"merchant_name": "", "merchant_address": "", "transaction_date": "",
             "transaction_time": "", "total_amount": 0, "line_items": []}
            """;

        assertThat(client.parseAndValidate(json)).isEmpty();
    }

    @Test
    void parseAndValidate_returnsResponseWhenMerchantEmptyButAmountPositive() {
        ReceiptOcrClientService client = new ReceiptOcrClientService("http://localhost:8000", mapper);
        String json = """
            {"merchant_name": "", "total_amount": 25.00}
            """;

        Optional<ReceiptOcrResponse> result = client.parseAndValidate(json);
        assertThat(result).isPresent();
        assertThat(result.get().getTotalAmount()).isEqualByComparingTo("25.00");
    }

    @Test
    void parseAndValidate_returnsEmptyForMalformedJson() {
        ReceiptOcrClientService client = new ReceiptOcrClientService("http://localhost:8000", mapper);
        assertThat(client.parseAndValidate("not json at all")).isEmpty();
        assertThat(client.parseAndValidate("")).isEmpty();
        assertThat(client.parseAndValidate(null)).isEmpty();
    }

    @Test
    void parseAndValidate_deserializesLineItems() {
        ReceiptOcrClientService client = new ReceiptOcrClientService("http://localhost:8000", mapper);
        String json = """
            {
              "merchant_name": "ALDI",
              "total_amount": 28.50,
              "line_items": [
                {"item_name": "Milk", "item_quantity": "1", "item_price": 1.99},
                {"item_name": "Bread", "item_quantity": "2", "item_price": 2.49}
              ]
            }
            """;

        Optional<ReceiptOcrResponse> result = client.parseAndValidate(json);
        assertThat(result).isPresent();
        assertThat(result.get().getLineItems()).hasSize(2);
        assertThat(result.get().getLineItems().get(0).getItemName()).isEqualTo("Milk");
    }

    @Test
    void parseAndValidate_ignoresUnknownFields() {
        ReceiptOcrClientService client = new ReceiptOcrClientService("http://localhost:8000", mapper);
        String json = """
            {
              "merchant_name": "STARBUCKS",
              "total_amount": 5.50,
              "unknown_field": "should be ignored",
              "another_unknown": 999
            }
            """;

        assertThat(client.parseAndValidate(json)).isPresent();
    }

    // buildMultipartBody

    @Test
    void buildMultipartBody_containsFileFieldHeader(@TempDir Path tempDir) throws Exception {
        Path img = tempDir.resolve("receipt.jpg");
        Files.write(img, new byte[]{(byte) 0xFF, (byte) 0xD8}); // minimal JPEG magic bytes

        ReceiptOcrClientService client = new ReceiptOcrClientService("http://localhost:8000", mapper);
        byte[] body = client.buildMultipartBody(img, "TEST_BOUNDARY");
        // ISO-8859-1 gives 1-to-1 byte mapping — safe for inspecting the ASCII header even with binary file content
        String bodyStr = new String(body, java.nio.charset.StandardCharsets.ISO_8859_1);

        assertThat(bodyStr).contains("--TEST_BOUNDARY");
        assertThat(bodyStr).contains("Content-Disposition: form-data; name=\"file\"");
        assertThat(bodyStr).contains("filename=\"receipt.jpg\"");
        assertThat(bodyStr).contains("Content-Type: image/jpeg");
    }

    @Test
    void buildMultipartBody_endsWithClosingBoundary(@TempDir Path tempDir) throws Exception {
        Path img = tempDir.resolve("receipt.png");
        Files.write(img, new byte[4]);

        ReceiptOcrClientService client = new ReceiptOcrClientService("http://localhost:8000", mapper);
        byte[] body = client.buildMultipartBody(img, "BND");
        String bodyStr = new String(body);

        assertThat(bodyStr).endsWith("--BND--\r\n");
    }
}
