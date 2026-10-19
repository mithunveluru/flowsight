package com.flowsight.merchant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowsight.exception.FlowsightException;
import com.flowsight.security.RateLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Last-resort merchant naming by an OpenAI-compatible chat model, for descriptors no
 * alias, fuzzy or embedding step recognised. Off unless MERCHANT_LLM_ENABLED, a model and
 * an API key are all set. Only the cleaned descriptor is sent, with handles, e-mails and
 * number-bearing tokens removed; transfers on person-to-person rails are never sent. The
 * answer is accepted only if it is "unknown" or a run of words present in what was sent.
 */
@Component
@Slf4j
public class MerchantLlmNamer {

    private static final String INSTRUCTIONS =
        "You read one bank statement or receipt merchant descriptor and name the business in it. "
        + "Reply with JSON only: {\"merchant\": \"<name>\"}. Copy the name from the descriptor; never add "
        + "words, numbers or punctuation that are not in it. If no business name is present, reply "
        + "{\"merchant\": \"unknown\"}.";

    private static final Pattern PERSON_RAILS = Pattern.compile("^(UPI|IMPS|NEFT|RTGS|MMT)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern HANDLE       = Pattern.compile("\\S*@\\S*");
    private static final Pattern NUMBERISH    = Pattern.compile("\\S*\\d\\S*\\d\\S*\\d\\S*");
    private static final int      MAX_NAME_LENGTH = 80;
    private static final int      CALLS_PER_HOUR  = 60;
    private static final int      CACHE_SIZE      = 500;
    private static final long     COOLDOWN_MILLIS = 60_000L;

    private final boolean      enabled;
    private final String       url;
    private final String       model;
    private final String       apiKey;
    private final Duration     timeout;
    private final ObjectMapper mapper;
    private final RateLimiter  rateLimiter;
    private final HttpClient   http;

    private final Map<String, Optional<String>> cache = new LinkedHashMap<>(64, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Optional<String>> eldest) {
            return size() > CACHE_SIZE;
        }
    };
    private volatile long blockedUntil;

    @Autowired
    public MerchantLlmNamer(
        @Value("${MERCHANT_LLM_ENABLED:false}") boolean enabled,
        @Value("${MERCHANT_LLM_URL:https://api.groq.com/openai/v1/chat/completions}") String url,
        @Value("${MERCHANT_LLM_MODEL:}") String model,
        @Value("${application.groq.api-key:}") String apiKey,
        @Value("${MERCHANT_LLM_TIMEOUT_MS:3000}") long timeoutMillis,
        ObjectMapper mapper,
        RateLimiter rateLimiter
    ) {
        this.enabled     = enabled;
        this.url         = url == null ? "" : url.trim();
        this.model       = model == null ? "" : model.trim();
        this.apiKey      = apiKey == null ? "" : apiKey.trim();
        this.timeout     = Duration.ofMillis(Math.max(100, timeoutMillis));
        this.mapper      = mapper;
        this.rateLimiter = rateLimiter;
        this.http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(this.timeout)
            .build();
    }

    public static MerchantLlmNamer disabled() {
        return new MerchantLlmNamer(false, "", "", "", 0, new ObjectMapper(), null);
    }

    public boolean active() {
        return enabled && !url.isEmpty() && !model.isEmpty() && !apiKey.isEmpty();
    }

    public Optional<String> name(UUID userId, String raw, String cleaned) {
        if (!active() || raw == null || cleaned == null || PERSON_RAILS.matcher(raw.trim()).find()) {
            return Optional.empty();
        }
        String input = redact(cleaned);
        if (input.isEmpty()) return Optional.empty();
        synchronized (cache) {
            Optional<String> known = cache.get(input);
            if (known != null) return known;
        }
        if (System.currentTimeMillis() < blockedUntil) return Optional.empty();
        if (userId != null && rateLimiter != null) {
            try {
                rateLimiter.check("merchant-llm:" + userId, CALLS_PER_HOUR, Duration.ofHours(1));
            } catch (FlowsightException e) {
                return Optional.empty();
            }
        }
        Optional<String> result;
        try {
            result = Optional.ofNullable(validate(ask(input), input)).map(MerchantText::display);
        } catch (Exception e) {
            blockedUntil = System.currentTimeMillis() + COOLDOWN_MILLIS;
            log.warn("Merchant LLM call failed: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
        synchronized (cache) {
            cache.put(input, result);
        }
        return result;
    }

    static String redact(String text) {
        String s = HANDLE.matcher(text).replaceAll(" ");
        s = NUMBERISH.matcher(s).replaceAll(" ");
        return s.replaceAll("\\s+", " ").trim();
    }

    // The model may only point at words that were in its input; anything else is discarded.
    static String validate(String answer, String input) {
        if (answer == null) return null;
        String name = answer.trim();
        if (name.isEmpty() || name.length() > MAX_NAME_LENGTH || name.equalsIgnoreCase("unknown")) return null;
        String n = MerchantText.matchText(name);
        String in = MerchantText.matchText(input);
        if (n.isEmpty() || !(" " + in + " ").contains(" " + n + " ")) return null;
        return name;
    }

    private String ask(String input) throws Exception {
        Map<String, Object> body = Map.of(
            "model", model,
            "messages", List.of(
                Map.of("role", "system", "content", INSTRUCTIONS),
                Map.of("role", "user", "content", input)),
            "response_format", Map.of("type", "json_object"),
            "temperature", 0,
            "max_tokens", 40);
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(timeout)
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + apiKey)
            .POST(HttpRequest.BodyPublishers.ofByteArray(mapper.writeValueAsBytes(body)))
            .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IllegalStateException("HTTP " + response.statusCode());
        String content = mapper.readTree(response.body()).at("/choices/0/message/content").asText(null);
        if (content == null || content.isBlank()) return null;
        JsonNode parsed = mapper.readTree(content);
        return parsed.path("merchant").isTextual() ? parsed.path("merchant").asText() : null;
    }
}
