package com.flowsight.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowsight.analytics.LeakDetectionService;
import com.flowsight.analytics.MerchantNormalizationService;
import com.flowsight.analytics.RecurringDetectionService;
import com.flowsight.dto.leak.LeakDetectionResponse;
import com.flowsight.dto.leak.LeakInsight;
import com.flowsight.dto.leak.LeakItem;
import com.flowsight.entity.RecurringPattern;
import com.flowsight.entity.Transaction;
import com.flowsight.entity.TransactionCategory;
import com.flowsight.entity.TransactionSource;
import com.flowsight.entity.TransactionType;
import com.flowsight.ml.recurring.PatternLeakDetector;
import com.flowsight.ml.recurring.RecurringScorer;
import com.flowsight.ml.recurring.RecurringScorerResponse;
import com.flowsight.ml.recurring.SpendBaseline;
import org.junit.jupiter.api.Test;

import java.io.BufferedWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Runs recurring detection and leak detection over the synthetic histories of
 * eval/recurring (generate.py) as of each user's date, without the database, and writes one
 * prediction line per user: the merchant keys with their transaction indices, the patterns
 * that would be stored, and the merchant keys named by each leak. The rules run writes the
 * recurring-scorer request for each user; eval/recurring/predict_model.py answers it with the
 * artifact, and the model run maps those answers exactly as RecurringDetectionService does.
 * Driven by eval/run.sh.
 */
class RecurringEvalTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final MerchantNormalizationService normalizer = new MerchantNormalizationService();
    private final RecurringDetectionService recurring = new RecurringDetectionService(null, null, null, normalizer, null, null);
    private final LeakDetectionService leaks = new LeakDetectionService(null, null, normalizer, null, null);

    /** Rules predictions, plus the scorer request the backend would send for each user. */
    @Test
    void writesRulePredictions() throws Exception {
        String input = System.getProperty("eval.recurring.input", "");
        assumeTrue(!input.isBlank(), "runs only with -Deval.recurring.input=<cases.jsonl>");
        Path output = Path.of(System.getProperty("eval.recurring.rulesOutput", "target/eval/recurring-rules.jsonl"));
        Path requests = Path.of(System.getProperty("eval.recurring.modelInput", "target/eval/recurring-model-input.jsonl"));

        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.createDirectories(requests.toAbsolutePath().getParent());
        int users = 0;
        try (BufferedWriter out = Files.newBufferedWriter(output, StandardCharsets.UTF_8);
             BufferedWriter req = Files.newBufferedWriter(requests, StandardCharsets.UTF_8)) {
            for (Case c : cases(input)) {
                List<RecurringPattern> patterns = sorted(recurring.detect(c.transactions, null));
                LeakDetectionResponse found = leaks.analyze(c.transactions, patterns, c.asOf);
                out.write(JSON.writeValueAsString(prediction(c, patterns, found)));
                out.newLine();
                req.write(JSON.writeValueAsString(Map.of("user", c.user,
                    "request", recurring.scorerRequest(c.transactions, c.asOf))));
                req.newLine();
                users++;
            }
        }
        System.out.printf("recurring eval (rules): %d users -> %s%n", users, output);
    }

    /** The model path: scores from predict_recurring.py, mapped and fed to the model-path leak detectors. */
    @Test
    void writesModelPredictions() throws Exception {
        String modelPred = System.getProperty("eval.recurring.modelPred", "");
        assumeTrue(!modelPred.isBlank(), "runs only with -Deval.recurring.modelPred=<pred.jsonl>");
        Path output = Path.of(System.getProperty("eval.recurring.modelOutput", "target/eval/recurring-model.jsonl"));
        Map<String, RecurringScorer.Scores> scores = new LinkedHashMap<>();
        for (String line : Files.readAllLines(Path.of(modelPred), StandardCharsets.UTF_8)) {
            if (line.isBlank()) continue;
            JsonNode row = JSON.readTree(line);
            RecurringScorerResponse response = JSON.treeToValue(row.get("result"), RecurringScorerResponse.class);
            scores.put(row.get("user").asText(), new RecurringScorer.Scores(response.scores(),
                row.get("model_version").asText()));
        }

        Files.createDirectories(output.toAbsolutePath().getParent());
        int users = 0;
        try (BufferedWriter out = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            for (Case c : cases(System.getProperty("eval.recurring.input"))) {
                List<RecurringPattern> patterns = sorted(recurring.fromScores(c.transactions, null, c.asOf, scores.get(c.user)));
                // eval users have personalization on
                LeakDetectionResponse found = leaks.analyzeWithModel(c.transactions, patterns, c.asOf,
                    SpendBaseline.personal(c.transactions, c.asOf));
                out.write(JSON.writeValueAsString(prediction(c, patterns, found)));
                out.newLine();
                users++;
            }
        }
        System.out.printf("recurring eval (model): %d users -> %s%n", users, output);
    }

    private static List<Case> cases(String input) throws Exception {
        List<Case> out = new ArrayList<>();
        for (String line : Files.readAllLines(Path.of(input), StandardCharsets.UTF_8)) {
            if (!line.isBlank()) out.add(Case.parse(JSON.readTree(line)));
        }
        return out;
    }

    // the repository hands patterns to leak detection largest amount first
    private static List<RecurringPattern> sorted(List<RecurringPattern> patterns) {
        List<RecurringPattern> out = new ArrayList<>(patterns);
        out.sort(Comparator.comparing(RecurringPattern::getEstimatedAmount, Comparator.reverseOrder()));
        return out;
    }

    private Map<String, Object> prediction(Case c, List<RecurringPattern> patterns, LeakDetectionResponse found) {
        Map<String, List<Integer>> keys = new TreeMap<>();
        for (int i = 0; i < c.transactions.size(); i++) {
            keys.computeIfAbsent(normalizer.normalizeKey(c.transactions.get(i).getMerchant()), k -> new ArrayList<>()).add(i);
        }
        List<Map<String, Object>> stored = new ArrayList<>();
        for (RecurringPattern p : patterns) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key", p.getNormalizedKey());
            row.put("period", p.getPeriod().name());
            row.put("next", p.getNextExpectedDate() == null ? null : p.getNextExpectedDate().toString());
            row.put("low", p.getNextExpectedLow() == null ? null : p.getNextExpectedLow().toString());
            row.put("high", p.getNextExpectedHigh() == null ? null : p.getNextExpectedHigh().toString());
            row.put("confidence", p.getConfidence());
            row.put("charges", p.getChargesPerPeriod());
            stored.add(row);
        }
        Map<String, List<String>> leakKeys = new TreeMap<>();
        for (LeakInsight leak : found.getLeaks()) {
            for (LeakItem item : leak.getItems()) {
                leakKeys.computeIfAbsent(leakKind(leak, item), k -> new ArrayList<>()).add(keyOf(item, patterns));
            }
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("user", c.user);
        row.put("keys", keys);
        row.put("patterns", stored);
        row.put("leaks", leakKeys);
        return row;
    }

    private static String leakKind(LeakInsight leak, LeakItem item) {
        return switch (leak.getType()) {
            case "SUBSCRIPTION_CREEP" -> "creep";
            // rule items are all category overlap; model items say which check found them
            case "DUPLICATE_SUBSCRIPTIONS" -> item.getEvidence() == null
                || PatternLeakDetector.METHOD_OVERLAP.equals(item.getEvidence().getMethod()) ? "overlap" : "duplicate";
            case "HIGH_FREQUENCY_SMALL_SPEND" -> "drain";
            default -> leak.getType().toLowerCase();
        };
    }

    // pattern items carry the pattern's display name; drain items the transaction merchant
    private String keyOf(LeakItem item, List<RecurringPattern> patterns) {
        return patterns.stream().filter(p -> p.getMerchant().equals(item.getMerchant()))
            .map(RecurringPattern::getNormalizedKey).findFirst()
            .orElseGet(() -> normalizer.normalizeKey(item.getMerchant()));
    }

    record Case(String user, LocalDate asOf, List<Transaction> transactions) {

        static Case parse(JsonNode node) {
            JsonNode groups = node.get("groups");
            List<Transaction> txns = new ArrayList<>();
            for (JsonNode tx : node.get("tx")) {
                String merchant = groups.get(tx.get(1).asInt()).get("merchant").asText();
                txns.add(Transaction.builder()
                    .transactionDate(LocalDate.parse(tx.get(0).asText()))
                    .merchant(merchant)
                    .description(merchant)
                    .amount(new BigDecimal(tx.get(2).asText()))
                    .currency(node.get("currency").asText())
                    .category(TransactionCategory.valueOf(tx.get(3).asText()))
                    .type(TransactionType.DEBIT)
                    .source(TransactionSource.CSV)
                    .build());
            }
            return new Case(node.get("user").asText(), LocalDate.parse(node.get("as_of").asText()), txns);
        }
    }
}
