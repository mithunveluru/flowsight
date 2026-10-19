package com.flowsight.eval;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Rolling-origin backtest of the cash-flow forecasts on the synthetic histories from
 * eval/forecast/generate.py. At every origin ("today") only transactions up to that day are
 * visible; the forecasts for the next three months and the month-end budget pace are written
 * as JSONL for score.py. Skipped unless -Deval.forecast.input is set. Driven by eval/run.sh.
 */
class ForecastEvalTest {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final int HORIZON = 3;
    private static final int GRACE_DAYS = 7;

    record Txn(LocalDate date, boolean credit, String category, double amount) {}

    record Origin(String user, LocalDate today, List<Txn> visible, int completeMonths, TreeSet<String> categories) {}

    @Test
    void writesForecastPredictions() throws Exception {
        String input = System.getProperty("eval.forecast.input", "");
        assumeTrue(!input.isBlank(), "forecast eval runs only with -Deval.forecast.input=<histories.jsonl>");
        Path outDir = Path.of(System.getProperty("eval.forecast.outputDir", "target/eval"));
        Files.createDirectories(outDir);

        List<Map<String, Object>> trend = new ArrayList<>();
        List<Map<String, Object>> pace = new ArrayList<>();
        int origins = 0;
        try (BufferedReader in = Files.newBufferedReader(Path.of(input), StandardCharsets.UTF_8)) {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.isBlank()) continue;
                JsonNode user = JSON.readTree(line);
                List<Txn> all = new ArrayList<>();
                for (JsonNode t : user.get("tx")) {
                    all.add(new Txn(LocalDate.parse(t.get(0).asText()), "CREDIT".equals(t.get(1).asText()),
                        t.get(2).asText(), t.get(3).asDouble()));
                }
                for (JsonNode o : user.get("origins")) {
                    Origin origin = origin(user.get("user").asText(), LocalDate.parse(o.asText()), all);
                    trend.addAll(currentTrendForecast(origin));
                    pace.addAll(currentBudgetPace(origin));
                    origins++;
                }
            }
        }
        write(outDir.resolve("pred-forecast-current.jsonl"), trend);
        write(outDir.resolve("pred-budget-pace-current.jsonl"), pace);
        System.out.printf("forecast eval: %d origins -> %d trend rows, %d pace rows in %s%n",
            origins, trend.size(), pace.size(), outDir);
    }

    static Origin origin(String user, LocalDate today, List<Txn> all) {
        List<Txn> visible = all.stream().filter(t -> !t.date().isAfter(today)).toList();
        TreeSet<String> categories = new TreeSet<>();
        visible.stream().filter(t -> !t.credit()).forEach(t -> categories.add(t.category()));
        int complete = 0;
        if (!visible.isEmpty()) {
            LocalDate earliest = visible.get(0).date();
            YearMonth first = earliest.getDayOfMonth() <= GRACE_DAYS
                ? YearMonth.from(earliest) : YearMonth.from(earliest).plusMonths(1);
            YearMonth current = YearMonth.from(today);
            while (first.isBefore(current)) {
                complete++;
                first = first.plusMonths(1);
            }
        }
        return new Origin(user, today, visible, complete, categories);
    }

    // Monthly totals of the visible transactions: income, spend, cat:<CATEGORY>.
    static Map<YearMonth, Map<String, Double>> monthly(List<Txn> visible) {
        Map<YearMonth, Map<String, Double>> out = new TreeMap<>();
        for (Txn t : visible) {
            Map<String, Double> m = out.computeIfAbsent(YearMonth.from(t.date()), k -> new TreeMap<>());
            if (t.credit()) {
                m.merge("income", t.amount(), Double::sum);
            } else {
                m.merge("spend", t.amount(), Double::sum);
                m.merge("cat:" + t.category(), t.amount(), Double::sum);
            }
        }
        return out;
    }

    // AnalyticsService.getTrend as shipped: mean of the last three trend points, the current
    // partial month included and months before the history counted as zero, repeated for three months.
    // The same rule is applied per category for comparison (the product had no category forecast).
    static List<Map<String, Object>> currentTrendForecast(Origin o) {
        Map<YearMonth, Map<String, Double>> monthly = monthly(o.visible());
        YearMonth now = YearMonth.from(o.today());
        List<String> series = new ArrayList<>(List.of("income", "spend"));
        o.categories().forEach(c -> series.add("cat:" + c));
        Map<String, Double> point = new LinkedHashMap<>();
        for (String s : series) {
            double sum = 0;
            for (int back = 0; back < 3; back++) {
                sum += monthly.getOrDefault(now.minusMonths(back), Map.of()).getOrDefault(s, 0.0);
            }
            point.put(s, sum / 3);
        }
        point.put("net", point.get("income") - point.get("spend"));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (int h = 1; h <= HORIZON; h++) {
            for (Map.Entry<String, Double> e : point.entrySet()) {
                rows.add(row(o, e.getKey(), now.plusMonths(h), h, e.getValue(), "flat-mean-3"));
            }
        }
        return rows;
    }

    // BudgetService as shipped: month-to-date spend * days in month / days elapsed.
    static List<Map<String, Object>> currentBudgetPace(Origin o) {
        YearMonth now = YearMonth.from(o.today());
        Map<String, Double> toDate = monthly(o.visible()).getOrDefault(now, Map.of());
        double factor = (double) now.lengthOfMonth() / o.today().getDayOfMonth();
        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(row(o, "spend", now, 0, toDate.getOrDefault("spend", 0.0) * factor, "linear-pace"));
        for (String c : o.categories()) {
            rows.add(row(o, "cat:" + c, now, 0, toDate.getOrDefault("cat:" + c, 0.0) * factor, "linear-pace"));
        }
        return rows;
    }

    static Map<String, Object> row(Origin o, String series, YearMonth target, int h, double point, String method) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("user", o.user());
        r.put("origin", o.today().toString());
        r.put("series", series);
        r.put("target", target.toString());
        r.put("h", h);
        r.put("n", o.completeMonths());
        r.put("point", Math.round(point * 100) / 100.0);
        r.put("method", method);
        return r;
    }

    static void write(Path path, List<Map<String, Object>> rows) throws Exception {
        try (BufferedWriter out = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            for (Map<String, Object> r : rows) {
                out.write(JSON.writeValueAsString(r));
                out.newLine();
            }
        }
    }
}
