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
 * that would be stored, and the merchant keys named by each leak. Driven by eval/run.sh.
 */
class RecurringEvalTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final MerchantNormalizationService normalizer = new MerchantNormalizationService();
    private final RecurringDetectionService recurring = new RecurringDetectionService(null, null, null, normalizer);
    private final LeakDetectionService leaks = new LeakDetectionService(null, null, normalizer);

    @Test
    void writesRulePredictions() throws Exception {
        String input = System.getProperty("eval.recurring.input", "");
        assumeTrue(!input.isBlank(), "runs only with -Deval.recurring.input=<cases.jsonl>");
        Path output = Path.of(System.getProperty("eval.recurring.rulesOutput", "target/eval/recurring-rules.jsonl"));

        Files.createDirectories(output.toAbsolutePath().getParent());
        int users = 0;
        try (BufferedWriter out = Files.newBufferedWriter(output, StandardCharsets.UTF_8)) {
            for (String line : Files.readAllLines(Path.of(input), StandardCharsets.UTF_8)) {
                if (line.isBlank()) continue;
                Case c = Case.parse(JSON.readTree(line));
                List<RecurringPattern> patterns = new ArrayList<>(recurring.detect(c.transactions, null));
                // the repository hands patterns to leak detection largest amount first
                patterns.sort(Comparator.comparing(RecurringPattern::getEstimatedAmount, Comparator.reverseOrder()));
                LeakDetectionResponse found = leaks.analyze(c.transactions, patterns, c.asOf);
                out.write(JSON.writeValueAsString(prediction(c, patterns, found)));
                out.newLine();
                users++;
            }
        }
        System.out.printf("recurring eval (rules): %d users -> %s%n", users, output);
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
            row.put("confidence", p.getConfidence());
            stored.add(row);
        }
        Map<String, List<String>> leakKeys = new TreeMap<>();
        for (LeakInsight leak : found.getLeaks()) {
            for (LeakItem item : leak.getItems()) {
                leakKeys.computeIfAbsent(leakKind(leak), k -> new ArrayList<>()).add(keyOf(item, patterns));
            }
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("user", c.user);
        row.put("keys", keys);
        row.put("patterns", stored);
        row.put("leaks", leakKeys);
        return row;
    }

    private static String leakKind(LeakInsight leak) {
        return switch (leak.getType()) {
            case "SUBSCRIPTION_CREEP" -> "creep";
            case "DUPLICATE_SUBSCRIPTIONS" -> "overlap";
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
