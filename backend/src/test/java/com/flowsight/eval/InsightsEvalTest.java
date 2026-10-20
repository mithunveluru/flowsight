package com.flowsight.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowsight.assistant.IntentMatcher;
import com.flowsight.llm.FactSet;
import com.flowsight.llm.GroundingValidator;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Test;

import java.io.BufferedWriter;
import java.io.Reader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Writes grounding-validator verdicts and assistant intent matches for the sets under
 * eval/insights. Skipped unless -Deval.insights.validator / -Deval.insights.intents are set.
 */
class InsightsEvalTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void writesValidatorVerdicts() throws Exception {
        String input = System.getProperty("eval.insights.validator", "");
        assumeTrue(!input.isBlank(), "validator eval runs only with -Deval.insights.validator=<cases.jsonl>");
        Path output = Path.of(System.getProperty("eval.insights.validatorOutput", "target/eval/validator.jsonl"));
        Files.createDirectories(output.toAbsolutePath().getParent());
        try (BufferedWriter out = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            for (String line : Files.readAllLines(Path.of(input), StandardCharsets.UTF_8)) {
                if (line.isBlank()) continue;
                JsonNode c = JSON.readTree(line);
                GroundingValidator.Result r = GroundingValidator.check(c.get("text").asText(), facts(c.get("facts")));
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", c.get("id").asText());
                row.put("grounded", r.grounded());
                row.put("ungrounded", r.ungrounded());
                out.write(JSON.writeValueAsString(row));
                out.newLine();
            }
        }
    }

    @Test
    void writesIntentMatches() throws Exception {
        String input = System.getProperty("eval.insights.intents", "");
        assumeTrue(!input.isBlank(), "intent eval runs only with -Deval.insights.intents=<intents.csv>");
        Path output = Path.of(System.getProperty("eval.insights.intentsOutput", "target/eval/intents.jsonl"));
        Files.createDirectories(output.toAbsolutePath().getParent());
        List<CSVRecord> rows;
        try (Reader reader = Files.newBufferedReader(Path.of(input), StandardCharsets.UTF_8)) {
            rows = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).build().parse(reader).getRecords();
        }
        try (BufferedWriter out = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            for (CSVRecord row : rows) {
                IntentMatcher.Intent intent = IntentMatcher.match(row.get("question"));
                Map<String, Object> pred = new LinkedHashMap<>();
                pred.put("id", row.get("id"));
                pred.put("intent", intent.type().name());
                pred.put("period", intent.period().name());
                pred.put("category", intent.category() == null ? "" : intent.category().name());
                out.write(JSON.writeValueAsString(pred));
                out.newLine();
            }
        }
    }

    private static FactSet facts(JsonNode list) {
        FactSet f = new FactSet();
        for (JsonNode n : list) {
            String key = n.get("key").asText();
            JsonNode v = n.get("value");
            switch (n.get("kind").asText()) {
                case "AMOUNT" -> f.amount(key, new BigDecimal(v.asText()));
                case "PERCENT" -> f.percent(key, v.asDouble());
                case "COUNT" -> f.count(key, v.asLong());
                case "DATE" -> f.date(key, LocalDate.parse(v.asText()));
                default -> f.text(key, v.asText());
            }
        }
        return f;
    }
}
