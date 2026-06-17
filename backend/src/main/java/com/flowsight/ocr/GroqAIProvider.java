package com.flowsight.ocr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * AI fallback interpreter backed by Groq's inference API (OpenAI-compatible endpoint).
 *
 * Invocation contract:
 *   - Only called when heuristic confidence is low or extraction is ambiguous.
 *   - Uses llama3-8b-8192 — fast, low-cost, adequate for single-field extraction.
 *   - OCR text is capped at {@value #MAX_OCR_LINES} lines to minimise token usage.
 *   - Temperature = 0, max_tokens = 150 — deterministic short responses.
 *   - Any failure (network, rate-limit, malformed JSON) returns Optional.empty()
 *     so the heuristic result is always preserved as the safe fallback.
 *
 * Enabled only when {@code application.groq.api-key} is set. If the key is blank
 * the bean is still registered but every call returns Optional.empty() immediately.
 */
@Service
@Slf4j
public class GroqAIProvider implements AITransactionInterpreter {

    static final int     MAX_OCR_LINES  = 20;
    private static final String  GROQ_URL      = "https://api.groq.com/openai/v1/chat/completions";
    private static final String  MODEL         = "llama-3.1-8b-instant";
    private static final String  CATEGORIES    =
        "FOOD_DINING, GROCERIES, SHOPPING, TRANSPORTATION, UTILITIES, " +
        "ENTERTAINMENT, HEALTHCARE, FINANCE, EDUCATION, TRAVEL, " +
        "SUBSCRIPTIONS, INCOME, OTHER";

    private static final String SYSTEM_PROMPT =
        "You are a receipt OCR parser. Extract the business/merchant name from the given receipt text. " +
        "Return ONLY valid JSON with exactly these fields: " +
        "{\"merchant\": \"<business name or null>\", " +
        "\"confidence\": <0.0-1.0>, " +
        "\"category\": \"<one of: " + CATEGORIES + " or null>\"}. " +
        "Ignore: addresses, phone numbers, totals, tax lines, receipt numbers, dates.";

    private final String     apiKey;
    private final ObjectMapper mapper;
    private final HttpClient  httpClient;

    public GroqAIProvider(
        @Value("${application.groq.api-key:}") String apiKey,
        ObjectMapper mapper
    ) {
        this.apiKey = apiKey.trim();
        this.mapper = mapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    }

    @Override
    public Optional<AIInterpretation> interpret(String rawOcrText, List<String> merchantCandidates) {
        if (apiKey.isBlank()) {
            log.debug("Groq: no API key configured, skipping AI fallback");
            return Optional.empty();
        }

        try {
            String userMessage = buildUserMessage(rawOcrText, merchantCandidates);
            String body = buildRequestBody(userMessage);
            String response = post(body);
            return parseResponse(response);
        } catch (Exception e) {
            log.warn("Groq AI fallback failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    // Request building

    String buildUserMessage(String rawOcrText, List<String> candidates) {
        String limitedText = limitLines(rawOcrText);

        StringBuilder sb = new StringBuilder("Receipt OCR text:\n");
        sb.append(limitedText).append("\n");

        if (!candidates.isEmpty()) {
            sb.append("\nHeuristic merchant candidates (ranked): ")
              .append(String.join(", ", candidates));
        }

        sb.append("\n\nExtract the actual business or merchant name. Return JSON only.");
        return sb.toString();
    }

    String buildRequestBody(String userMessage) throws Exception {
        Map<String, Object> body = Map.of(
            "model",   MODEL,
            "messages", List.of(
                Map.of("role", "system", "content", SYSTEM_PROMPT),
                Map.of("role", "user",   "content", userMessage)
            ),
            "response_format", Map.of("type", "json_object"),
            "temperature", 0,
            "max_tokens",  150
        );
        return mapper.writeValueAsString(body);
    }

    // HTTP call

    private String post(String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(GROQ_URL))
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + apiKey)
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        HttpResponse<String> response = httpClient.send(
            request, HttpResponse.BodyHandlers.ofString()
        );

        int status = response.statusCode();
        if (status == 429) throw new RuntimeException("Groq rate limit exceeded");
        if (status != 200) throw new RuntimeException("Groq returned HTTP " + status);

        return response.body();
    }

    // Response parsing

    Optional<AIInterpretation> parseResponse(String responseBody) {
        try {
            JsonNode root    = mapper.readTree(responseBody);
            String content   = root.at("/choices/0/message/content").asText(null);
            if (content == null || content.isBlank()) return Optional.empty();

            JsonNode parsed  = mapper.readTree(content);
            String merchant  = parsed.path("merchant").asText(null);
            double confidence = parsed.path("confidence").asDouble(0.5);
            String category  = parsed.path("category").asText(null);

            if (merchant == null || merchant.equals("null") || merchant.isBlank()) {
                return Optional.empty();
            }

            log.debug("Groq result: merchant='{}' category='{}' conf={}", merchant, category, confidence);

            return Optional.of(AIInterpretation.builder()
                .merchant(merchant.trim())
                .categoryHint("null".equals(category) ? null : category)
                .confidence(Math.max(0.0, Math.min(1.0, confidence)))
                .build());

        } catch (Exception e) {
            log.warn("Groq response parse failed: {}", e.getMessage());
            return Optional.empty();
        }
    }

    static String limitLines(String text) {
        if (text == null) return "";
        String[] lines = text.split("\\r?\\n");
        StringBuilder sb = new StringBuilder();
        int count = 0;
        for (String line : lines) {
            if (count >= MAX_OCR_LINES) break;
            if (!line.isBlank()) {
                sb.append(line).append('\n');
                count++;
            }
        }
        return sb.toString().trim();
    }
}
