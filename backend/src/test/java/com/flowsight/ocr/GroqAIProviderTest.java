package com.flowsight.ocr;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class GroqAIProviderTest {

    private ObjectMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
    }

    // Disabled when key is blank

    @Test
    void interpret_returnsEmptyWhenApiKeyIsBlank() {
        GroqAIProvider provider = new GroqAIProvider("", mapper);
        Optional<AIInterpretation> result = provider.interpret("WALMART\n123 Main St", List.of("WALMART"));
        assertThat(result).isEmpty();
    }

    @Test
    void interpret_returnsEmptyWhenApiKeyIsWhitespace() {
        GroqAIProvider provider = new GroqAIProvider("   ", mapper);
        assertThat(provider.interpret("some text", List.of())).isEmpty();
    }

    // Request building

    @Test
    void buildUserMessage_includesOcrTextAndCandidates() throws Exception {
        GroqAIProvider provider = new GroqAIProvider("dummy-key", mapper);
        String msg = provider.buildUserMessage(
            "WALMART\n123 Main St\nTEL 555-1234",
            List.of("WALMART", "123 Main")
        );
        assertThat(msg).contains("WALMART");
        assertThat(msg).contains("WALMART, 123 Main");
        assertThat(msg).contains("merchant");
    }

    @Test
    void buildUserMessage_limitsOcrToMaxLines() throws Exception {
        GroqAIProvider provider = new GroqAIProvider("dummy-key", mapper);
        StringBuilder manyLines = new StringBuilder();
        for (int i = 0; i < 50; i++) manyLines.append("line ").append(i).append("\n");

        String msg = provider.buildUserMessage(manyLines.toString(), List.of());
        // Should contain at most MAX_OCR_LINES non-blank lines worth of content
        long lineCount = msg.lines()
            .filter(l -> l.startsWith("line "))
            .count();
        assertThat(lineCount).isLessThanOrEqualTo(GroqAIProvider.MAX_OCR_LINES);
    }

    @Test
    void buildRequestBody_producesValidJson() throws Exception {
        GroqAIProvider provider = new GroqAIProvider("dummy-key", mapper);
        String body = provider.buildRequestBody("test message");
        var node = mapper.readTree(body);
        assertThat(node.has("model")).isTrue();
        assertThat(node.has("messages")).isTrue();
        assertThat(node.at("/response_format/type").asText()).isEqualTo("json_object");
        assertThat(node.get("temperature").asInt()).isEqualTo(0);
        assertThat(node.get("max_tokens").asInt()).isEqualTo(150);
    }

    // Response parsing

    @Test
    void parseResponse_extractsMerchantFromValidJson() throws Exception {
        GroqAIProvider provider = new GroqAIProvider("dummy-key", mapper);

        String groqResponse = """
            {
              "choices": [{
                "message": {
                  "content": "{\\"merchant\\": \\"WALMART\\", \\"confidence\\": 0.95, \\"category\\": \\"GROCERIES\\"}"
                }
              }]
            }
            """;

        Optional<AIInterpretation> result = provider.parseResponse(groqResponse);
        assertThat(result).isPresent();
        assertThat(result.get().getMerchant()).isEqualTo("WALMART");
        assertThat(result.get().getConfidence()).isCloseTo(0.95, org.assertj.core.data.Offset.offset(0.01));
        assertThat(result.get().getCategoryHint()).isEqualTo("GROCERIES");
    }

    @Test
    void parseResponse_returnsEmptyWhenMerchantIsNull() throws Exception {
        GroqAIProvider provider = new GroqAIProvider("dummy-key", mapper);

        String groqResponse = """
            {
              "choices": [{
                "message": {
                  "content": "{\\"merchant\\": null, \\"confidence\\": 0.1, \\"category\\": null}"
                }
              }]
            }
            """;

        assertThat(provider.parseResponse(groqResponse)).isEmpty();
    }

    @Test
    void parseResponse_returnsEmptyWhenContentIsMalformedJson() throws Exception {
        GroqAIProvider provider = new GroqAIProvider("dummy-key", mapper);

        String groqResponse = """
            {
              "choices": [{
                "message": {
                  "content": "not json at all"
                }
              }]
            }
            """;

        assertThat(provider.parseResponse(groqResponse)).isEmpty();
    }

    @Test
    void parseResponse_returnsEmptyWhenChoicesIsEmpty() throws Exception {
        GroqAIProvider provider = new GroqAIProvider("dummy-key", mapper);
        String groqResponse = "{\"choices\": []}";
        assertThat(provider.parseResponse(groqResponse)).isEmpty();
    }

    @Test
    void parseResponse_clampsBadConfidenceValues() throws Exception {
        GroqAIProvider provider = new GroqAIProvider("dummy-key", mapper);

        String groqResponse = """
            {
              "choices": [{
                "message": {
                  "content": "{\\"merchant\\": \\"STARBUCKS\\", \\"confidence\\": 1.8, \\"category\\": null}"
                }
              }]
            }
            """;

        Optional<AIInterpretation> result = provider.parseResponse(groqResponse);
        assertThat(result).isPresent();
        assertThat(result.get().getConfidence()).isLessThanOrEqualTo(1.0);
    }

    @Test
    void parseResponse_nullCategoryStoredAsNull() throws Exception {
        GroqAIProvider provider = new GroqAIProvider("dummy-key", mapper);

        String groqResponse = """
            {
              "choices": [{
                "message": {
                  "content": "{\\"merchant\\": \\"ZOMATO\\", \\"confidence\\": 0.80, \\"category\\": \\"null\\"}"
                }
              }]
            }
            """;

        Optional<AIInterpretation> result = provider.parseResponse(groqResponse);
        assertThat(result).isPresent();
        assertThat(result.get().getCategoryHint()).isNull(); // "null" string → null
    }

    // limitLines utility

    @Test
    void limitLines_capsAt20NonBlankLines() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 30; i++) sb.append("line ").append(i).append("\n");

        String limited = GroqAIProvider.limitLines(sb.toString());
        long count = limited.lines().filter(l -> !l.isBlank()).count();
        assertThat(count).isLessThanOrEqualTo(GroqAIProvider.MAX_OCR_LINES);
    }

    @Test
    void limitLines_skipsBlankLines() {
        String text = "line1\n\n\nline2\n\nline3";
        String limited = GroqAIProvider.limitLines(text);
        assertThat(limited).contains("line1");
        assertThat(limited).contains("line2");
        assertThat(limited).contains("line3");
    }

    @Test
    void limitLines_handlesNullGracefully() {
        assertThat(GroqAIProvider.limitLines(null)).isEmpty();
    }
}
