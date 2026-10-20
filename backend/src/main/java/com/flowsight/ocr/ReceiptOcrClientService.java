package com.flowsight.ocr;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowsight.dto.receipt.ReceiptOcrResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

// HTTP client for the receipt-ocr microservice; returns empty on any failure so the
// caller falls back to Tesseract. No-op when application.receipt-ocr.url is unconfigured.
@Service
@Slf4j
public class ReceiptOcrClientService {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration REQUEST_TIMEOUT  = Duration.ofSeconds(45);

    private final HttpClient   httpClient;
    private final ObjectMapper objectMapper;
    private final String       receiptOcrUrl;
    private final Duration     backoff;
    private final Clock        clock;
    private volatile Instant   skipUntil = Instant.MIN;

    /** What one call produced; outcome is "ok" or the reason nothing usable came back. */
    public record Call(Optional<ReceiptOcrResponse> response, String outcome) {
        static Call failed(String outcome) {
            return new Call(Optional.empty(), outcome);
        }
    }

    @Autowired
    public ReceiptOcrClientService(
        @Value("${application.receipt-ocr.url:}") String receiptOcrUrl,
        @Value("${application.receipt-ocr.backoff:10m}") Duration backoff,
        ObjectMapper objectMapper
    ) {
        this(receiptOcrUrl, backoff, objectMapper, Clock.systemUTC());
    }

    public ReceiptOcrClientService(String receiptOcrUrl, ObjectMapper objectMapper) {
        this(receiptOcrUrl, Duration.ofMinutes(10), objectMapper, Clock.systemUTC());
    }

    ReceiptOcrClientService(String receiptOcrUrl, Duration backoff, ObjectMapper objectMapper, Clock clock) {
        this.receiptOcrUrl = receiptOcrUrl.trim();
        this.backoff       = backoff;
        this.clock         = clock;
        this.objectMapper  = objectMapper;
        // uvicorn rejects the h2c upgrade Java sends by default on plain http:// (HTTP 422)
        this.httpClient    = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(CONNECT_TIMEOUT)
            .build();
    }

    // send the image to receipt-ocr; empty on any connectivity/parse failure
    public Optional<ReceiptOcrResponse> extract(Path imagePath) {
        return call(imagePath).response();
    }

    public Call call(Path imagePath) {
        if (receiptOcrUrl.isBlank()) return Call.failed("not configured");
        if (clock.instant().isBefore(skipUntil)) return Call.failed("skipped after a recent failure");

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

            int status = response.statusCode();
            if (status != 200) {
                if (status >= 500) backOff("HTTP " + status);
                else log.warn("receipt-ocr returned HTTP {}", status);
                return Call.failed("HTTP " + status);
            }

            Optional<ReceiptOcrResponse> parsed = parseAndValidate(response.body());
            return parsed.isPresent() ? new Call(parsed, "ok") : Call.failed("no merchant or total");

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Call.failed("interrupted");
        } catch (Exception e) {
            backOff(e.getClass().getSimpleName());
            return Call.failed(e.getClass().getSimpleName());
        }
    }

    // a failing service (e.g. a vision model the provider no longer serves) answers every upload
    // with the same error, so stop paying its latency for a while
    private void backOff(String reason) {
        skipUntil = clock.instant().plus(backoff);
        log.warn("receipt-ocr failed ({}); skipping it for {}", reason, backoff);
    }

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

            // reject when the LLM returned no merchant and no amount
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
