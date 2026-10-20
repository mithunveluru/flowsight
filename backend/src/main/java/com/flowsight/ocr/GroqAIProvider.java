package com.flowsight.ocr;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

// AI fallback (Groq, OpenAI-compatible) for low-confidence/ambiguous merchant extraction.
// Returns empty on any failure; disabled when application.groq.api-key is blank.
@Service
@Slf4j
public class GroqAIProvider implements AITransactionInterpreter {

    static final int     MAX_OCR_LINES  = 20;
    private static final String  GROQ_URL      = "https://api.groq.com/openai/v1/chat/completions";
    private static final String  MODELS_URL    = "https://api.groq.com/openai/v1/models";
    static final String          DEFAULT_MODELS = "openai/gpt-oss-20b,llama-3.1-8b-instant";
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
    private final ModelChooser models;
    private final ObjectMapper mapper;
    private final HttpClient  httpClient;
    private volatile boolean  warnedNoModel;

    @Autowired
    public GroqAIProvider(
        @Value("${application.groq.api-key:}") String apiKey,
        @Value("${application.groq.merchant-model:" + DEFAULT_MODELS + "}") String models,
        ObjectMapper mapper
    ) {
        this.apiKey = apiKey.trim();
        this.mapper = mapper;
        this.httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(Duration.ofSeconds(5))
            .build();
        this.models = new ModelChooser(models == null || models.isBlank() ? DEFAULT_MODELS : models,
            this::fetchModels, Clock.systemUTC());
    }

    public GroqAIProvider(String apiKey, ObjectMapper mapper) {
        this(apiKey, null, mapper);
    }

    @Override
    public Optional<AIInterpretation> interpret(String rawOcrText, List<String> merchantCandidates) {
        if (apiKey.isBlank()) {
            log.debug("Groq: no API key configured, skipping AI fallback");
            return Optional.empty();
        }

        Optional<String> model = models.choose();
        if (model.isEmpty()) {
            if (!warnedNoModel) {
                warnedNoModel = true;
                log.warn("Groq: none of the configured merchant models {} is available to this key; using heuristics",
                    models.preferred());
            }
            return Optional.empty();
        }
        warnedNoModel = false;

        try {
            String userMessage = buildUserMessage(rawOcrText, merchantCandidates);
            String body = buildRequestBody(model.get(), userMessage);
            String response = post(model.get(), body);
            if (response == null) return Optional.empty();
            log.info("Groq merchant check answered by {}", model.get());
            return parseResponse(response);
        } catch (Exception e) {
            log.warn("Groq AI fallback failed: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    String buildUserMessage(String rawOcrText, List<String> candidates) {
        // receipt text leaves the backend here: mask phones, card tails, tax IDs and emails first
        String limitedText = PiiRedactor.redact(limitLines(rawOcrText));

        StringBuilder sb = new StringBuilder("Receipt OCR text:\n");
        sb.append(limitedText).append("\n");

        if (!candidates.isEmpty()) {
            sb.append("\nHeuristic merchant candidates (ranked): ")
              .append(PiiRedactor.redact(String.join(", ", candidates)));
        }

        sb.append("\n\nExtract the actual business or merchant name. Return JSON only.");
        return sb.toString();
    }

    String buildRequestBody(String model, String userMessage) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", List.of(
            Map.of("role", "system", "content", SYSTEM_PROMPT),
            Map.of("role", "user",   "content", userMessage)
        ));
        body.put("response_format", Map.of("type", "json_object"));
        body.put("temperature", 0);
        // reasoning models spend part of max_tokens thinking before they answer
        if (isReasoningModel(model)) {
            body.put("reasoning_effort", "low");
            body.put("max_tokens", 600);
        } else {
            body.put("max_tokens", 150);
        }
        return mapper.writeValueAsString(body);
    }

    static boolean isReasoningModel(String model) {
        return model.startsWith("openai/gpt-oss");
    }

    // null when the provider no longer serves this model; it is skipped from then on
    private String post(String model, String body) throws Exception {
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
        if (status == 404 || (status == 400 && response.body().contains("model"))) {
            models.reject(model);
            log.warn("Groq rejected model {} (HTTP {}); trying the next configured model next time", model, status);
            return null;
        }
        if (status == 429) throw new RuntimeException("Groq rate limit exceeded");
        if (status != 200) throw new RuntimeException("Groq returned HTTP " + status);

        return response.body();
    }

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

    private Optional<Set<String>> fetchModels() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(MODELS_URL))
                .timeout(Duration.ofSeconds(5))
                .header("Authorization", "Bearer " + apiKey)
                .GET()
                .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) return Optional.empty();
            return Optional.of(parseModelIds(response.body()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    Set<String> parseModelIds(String body) throws Exception {
        Set<String> ids = new HashSet<>();
        for (JsonNode m : mapper.readTree(body).path("data")) {
            if (m.path("active").asBoolean(true) && m.hasNonNull("id")) ids.add(m.get("id").asText());
        }
        return ids;
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
