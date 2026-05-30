package com.flowsight.ocr;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowsight.dto.receipt.ReceiptOcrResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/**
 * HTTP client adapter for the receipt-ocr FastAPI microservice.
 *
 * POSTs the receipt image as multipart/form-data to {@code POST /ocr/} and
 * deserializes the structured JSON response.  Every failure path returns
 * {@link Optional#empty()} — callers must treat this as a best-effort step
 * and fall back to Tesseract when empty.
 *
 * The client is a no-op (always returns empty) when
 * {@code application.receipt-ocr.url} is not configured, so the system
 * works identically in environments that only have Tesseract available.
 */
@Service
@Slf4j
public class ReceiptOcrClientService {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT  = Duration.ofSeconds(45);

    private final HttpClient   httpClient;
    private final ObjectMapper objectMapper;
    private final String       receiptOcrUrl;

    public ReceiptOcrClientService(
        @Value("${application.receipt-ocr.url:}") String receiptOcrUrl,
        ObjectMapper objectMapper
    ) {
        this.receiptOcrUrl = receiptOcrUrl.trim();
        this.objectMapper  = objectMapper;
        this.httpClient    = HttpClient.newBuilder()
            .connectTimeout(CONNECT_TIMEOUT)
            .build();
    }

    /**
     * Sends the receipt image to the receipt-ocr service and returns the parsed response.
     * Never throws; returns empty on any connectivity or parsing failure.
     */
    public Optional<ReceiptOcrResponse> extract(Path imagePath) {
        if (receiptOcrUrl.isBlank()) {
            log.debug("receipt-ocr URL not configured, skipping LLM extraction");
            return Optional.empty();
        }

        try {
            String boundary = "----FlowSightBoundary" + UUID.randomUUID().toString().replace("-", "");
            byte[] body     = buildMultipartBody(imagePath, boundary);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(receiptOcrUrl + "/ocr/"))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                .timeout(REQUEST_TIMEOUT)
                .build();

            HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("receipt-ocr returned HTTP {} for {}", response.statusCode(), imagePath.getFileName());
                return Optional.empty();
            }

            return parseAndValidate(response.body());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("receipt-ocr call interrupted for {}", imagePath.getFileName());
            return Optional.empty();
        } catch (Exception e) {
            log.warn("receipt-ocr call failed for {}: {}", imagePath.getFileName(), e.getMessage());
            return Optional.empty();
        }
    }

    // -------------------------------------------------------------------------
    // Package-visible for testing
    // -------------------------------------------------------------------------

    byte[] buildMultipartBody(Path imagePath, String boundary) throws IOException {
        byte[] fileBytes = Files.readAllBytes(imagePath);
        String filename  = imagePath.getFileName().toString();
        String mimeType  = probeMimeType(imagePath);

        ByteArrayOutputStream out = new ByteArrayOutputStream(fileBytes.length + 256);
        out.write(("--" + boundary + "\r\n"
            + "Content-Disposition: form-data; name=\"file\"; filename=\"" + filename + "\"\r\n"
            + "Content-Type: " + mimeType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(fileBytes);
        out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    Optional<ReceiptOcrResponse> parseAndValidate(String json) {
        try {
            ReceiptOcrResponse response = objectMapper.readValue(json, ReceiptOcrResponse.class);
            if (response == null) return Optional.empty();

            // Reject if the LLM returned no usable data (null or empty string for merchant, null/zero for amount)
            boolean noMerchant = response.getMerchantName() == null || response.getMerchantName().isBlank();
            boolean noAmount   = response.getTotalAmount() == null
                || response.getTotalAmount().compareTo(java.math.BigDecimal.ZERO) <= 0;
            if (noMerchant && noAmount) {
                log.debug("receipt-ocr returned no merchant and no amount — treating as empty");
                return Optional.empty();
            }
            return Optional.of(response);
        } catch (Exception e) {
            log.warn("Failed to parse receipt-ocr JSON: {}", e.getMessage());
            return Optional.empty();
        }
    }

    // -------------------------------------------------------------------------
    // Utilities
    // -------------------------------------------------------------------------

    private String probeMimeType(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return "image/jpeg";
        if (name.endsWith(".png"))                           return "image/png";
        if (name.endsWith(".webp"))                          return "image/webp";
        if (name.endsWith(".tiff") || name.endsWith(".tif")) return "image/tiff";
        if (name.endsWith(".bmp"))                           return "image/bmp";
        try {
            String detected = Files.probeContentType(path);
            return detected != null ? detected : "application/octet-stream";
        } catch (IOException e) {
            return "application/octet-stream";
        }
    }
}
