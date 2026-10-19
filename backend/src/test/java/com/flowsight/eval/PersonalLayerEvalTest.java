package com.flowsight.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowsight.entity.TransactionCategory;
import com.flowsight.ml.categorization.CategorizerRequest;
import com.flowsight.ml.categorization.CategoryAlternative;
import com.flowsight.ml.categorization.PersonalBlend;
import com.flowsight.ml.categorization.ReviewedHistory;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Test;

import java.io.BufferedWriter;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Simulates the personal layer on the categorization eval set. Each region stands for one
 * user; its rows are split in two halves, and each half is categorized by the model's
 * predictions blended with the other half as that user's reviewed (corrected) history.
 * personalOutput splits by resolved merchant, so a row never sees its own merchant's label;
 * personalRepeatOutput splits by row id, the case of a merchant the user already corrected.
 * Driven by eval/run.sh after the model predictions exist; skipped otherwise.
 */
class PersonalLayerEvalTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void writesPersonalLayerPredictions() throws Exception {
        String modelPred = System.getProperty("eval.categorization.modelPred", "");
        assumeTrue(!modelPred.isBlank(), "runs only with -Deval.categorization.modelPred=<pred.jsonl>");
        Path cases = Path.of(System.getProperty("eval.categorization.input"));
        Path inputs = Path.of(System.getProperty("eval.categorization.modelInput"));
        Path byMerchant = Path.of(System.getProperty("eval.categorization.personalOutput"));
        Path byRow = Path.of(System.getProperty("eval.categorization.personalRepeatOutput"));

        Map<String, CategorizerRequest.Item> items = new HashMap<>();
        for (String line : Files.readAllLines(inputs, StandardCharsets.UTF_8)) {
            if (line.isBlank()) continue;
            JsonNode row = JSON.readTree(line);
            items.put(row.get("id").asText(), JSON.treeToValue(row.get("item"), CategorizerRequest.Item.class));
        }
        Map<String, JsonNode> predictions = new HashMap<>();
        for (String line : Files.readAllLines(Path.of(modelPred), StandardCharsets.UTF_8)) {
            if (line.isBlank()) continue;
            JsonNode row = JSON.readTree(line);
            predictions.put(row.get("id").asText(), row);
        }

        List<CSVRecord> rows = new ArrayList<>();
        CSVFormat format = CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setCommentMarker('#').build();
        try (Reader in = Files.newBufferedReader(cases, StandardCharsets.UTF_8)) {
            format.parse(in).forEach(rows::add);
        }
        simulate(rows, items, predictions, row -> {
            String merchant = items.get(row.get("id")).merchant();
            return merchant == null || merchant.isBlank() ? row.get("id") : merchant.toLowerCase(java.util.Locale.ROOT);
        }, byMerchant);
        simulate(rows, items, predictions, row -> row.get("id"), byRow);
    }

    private static void simulate(List<CSVRecord> rows, Map<String, CategorizerRequest.Item> items,
                                 Map<String, JsonNode> predictions, Function<CSVRecord, String> splitKey,
                                 Path output) throws Exception {
        // one simulated user per (region, half): a row of half h is history for the user of half 1 - h
        Map<UUID, List<ReviewedHistory.Reviewed>> history = new HashMap<>();
        for (CSVRecord row : rows) {
            CategorizerRequest.Item item = items.get(row.get("id"));
            history.computeIfAbsent(user(row.get("region"), 1 - half(splitKey.apply(row))), k -> new ArrayList<>())
                .add(new ReviewedHistory.Reviewed(item.merchant(), item.description(),
                    TransactionCategory.valueOf(row.get("category"))));
        }
        ReviewedHistory reviewed = ReviewedHistory.inMemory(history);

        Files.createDirectories(output.toAbsolutePath().getParent());
        int personal = 0;
        try (BufferedWriter out = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            for (CSVRecord row : rows) {
                String id = row.get("id");
                UUID user = user(row.get("region"), half(splitKey.apply(row)));
                List<CategoryAlternative> global = new ArrayList<>();
                for (JsonNode a : predictions.get(id).get("alternatives")) {
                    global.add(new CategoryAlternative(TransactionCategory.valueOf(a.get("category").asText()),
                        a.get("probability").asDouble()));
                }
                PersonalBlend.Result result = PersonalBlend.blend(global, reviewed.nearest(user, List.of(items.get(id))).get(0));
                if (result.personal()) personal++;
                Map<String, Object> prediction = new LinkedHashMap<>();
                prediction.put("id", id);
                prediction.put("category", result.category().name());
                prediction.put("confidence", result.probability());
                prediction.put("personal", result.personal());
                out.write(JSON.writeValueAsString(prediction));
                out.newLine();
            }
        }
        System.out.printf("personal layer eval: %d rows (%d with neighbours) -> %s%n", rows.size(), personal, output);
    }

    private static UUID user(String region, int half) {
        return UUID.nameUUIDFromBytes((region + ":" + half).getBytes(StandardCharsets.UTF_8));
    }

    private static int half(String key) {
        return Math.floorMod(key.hashCode(), 2);
    }
}
