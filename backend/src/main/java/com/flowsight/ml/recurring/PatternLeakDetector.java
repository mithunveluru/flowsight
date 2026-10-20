package com.flowsight.ml.recurring;

import com.flowsight.dto.leak.LeakEvidence;
import com.flowsight.dto.leak.LeakInsight;
import com.flowsight.dto.leak.LeakItem;
import com.flowsight.entity.RecurringPattern;
import com.flowsight.entity.Transaction;
import com.flowsight.entity.TransactionCategory;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

/**
 * Leak detectors used when patterns come from the recurring-scorer: price steps by change
 * point over a year of payments, one service billed twice (parallel charges or two patterns on
 * one canonical merchant key), category overlap counted per canonical key, and habit drains
 * against the user's {@link SpendBaseline}. Pure: the caller supplies debits, patterns and keys.
 */
public final class PatternLeakDetector {

    public static final int CREEP_LOOKBACK_MONTHS = 13;
    public static final int WINDOW_MONTHS = 3;
    static final int DRAIN_MIN_COUNT = 8;
    static final Set<TransactionCategory> OVERLAP_CATEGORIES = Set.of(
        TransactionCategory.SUBSCRIPTIONS, TransactionCategory.ENTERTAINMENT, TransactionCategory.EDUCATION);
    // necessities and money movements are not habits to cut back on
    static final Set<TransactionCategory> NOT_HABITS = Set.of(
        TransactionCategory.GROCERIES, TransactionCategory.UTILITIES, TransactionCategory.HEALTHCARE,
        TransactionCategory.FINANCE, TransactionCategory.TRANSFER, TransactionCategory.INCOME,
        TransactionCategory.EDUCATION, TransactionCategory.TRANSPORTATION);

    public static final String METHOD_CHANGE_POINT = "change-point:median-mad";
    public static final String METHOD_PARALLEL     = "duplicate:parallel-charges";
    public static final String METHOD_SAME_KEY     = "duplicate:same-merchant-key";
    public static final String METHOD_OVERLAP      = "duplicate:category-overlap";
    public static final String METHOD_DRAIN        = "drain:personal-baseline";
    public static final String METHOD_DRAIN_GLOBAL = "drain:global-thresholds";

    private final UnaryOperator<String> keyOf;
    private final Function<BigDecimal, String> severity;

    /** keyOf: the merchant grouping key; severity: the leak severity for a monthly impact. */
    public PatternLeakDetector(UnaryOperator<String> keyOf, Function<BigDecimal, String> severity) {
        this.keyOf = keyOf;
        this.severity = severity;
    }

    public LeakInsight creep(List<RecurringPattern> patterns, List<Transaction> debits, LocalDate asOf) {
        LocalDate from = asOf.minusMonths(CREEP_LOOKBACK_MONTHS);
        Map<String, List<Transaction>> byKey = byKey(debits.stream()
            .filter(t -> !t.getTransactionDate().isBefore(from)).toList());
        List<LeakItem> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (RecurringPattern p : patterns) {
            List<PriceChangeDetector.Payment> payments = byKey.getOrDefault(p.getNormalizedKey(), List.of()).stream()
                .sorted(Comparator.comparing(Transaction::getTransactionDate))
                .map(t -> new PriceChangeDetector.Payment(t.getTransactionDate(), t.getAmount().doubleValue()))
                .toList();
            PriceChangeDetector.Step step = PriceChangeDetector.detect(payments).orElse(null);
            if (step == null) continue;
            BigDecimal monthly = BigDecimal.valueOf((step.after() - step.before())
                    * p.getPeriod().getAnnualFrequency() * Math.max(1, p.getChargesPerPeriod()))
                .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
            total = total.add(monthly);
            Map<String, Object> inputs = new LinkedHashMap<>();
            inputs.put("priceBefore", round(step.before()));
            inputs.put("priceAfter", round(step.after()));
            inputs.put("paymentsBefore", step.paymentsBefore());
            inputs.put("paymentsAfter", step.paymentsAfter());
            inputs.put("changeDate", step.changeDate().toString());
            inputs.put("minRobustZ", round(Math.min(step.minZ(), 999)));
            inputs.put("minRobustZThreshold", PriceChangeDetector.MIN_Z);
            inputs.put("minRatio", PriceChangeDetector.MIN_RATIO);
            items.add(LeakItem.builder()
                .merchant(p.getMerchant())
                .amount(monthly)
                .detail(String.format("₹%.0f → ₹%.0f (+%.0f%%) since %s", step.before(), step.after(),
                    (step.ratio() - 1) * 100, step.changeDate()))
                .category(p.getPeriod().getDisplayName())
                .categoryLabel(p.getPeriod().getDisplayName())
                .evidence(evidence(METHOD_CHANGE_POINT, from, asOf, inputs, step.confidence()))
                .build());
        }
        if (items.isEmpty()) return null;
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("lookbackMonths", CREEP_LOOKBACK_MONTHS);
        inputs.put("minPaymentsBefore", PriceChangeDetector.MIN_BEFORE);
        inputs.put("minPaymentsAfter", PriceChangeDetector.MIN_AFTER);
        inputs.put("minRobustZ", PriceChangeDetector.MIN_Z);
        inputs.put("minRatio", PriceChangeDetector.MIN_RATIO);
        return LeakInsight.builder()
            .type("SUBSCRIPTION_CREEP")
            .severity(severity.apply(total))
            .title(items.size() + " subscription" + (items.size() == 1 ? "" : "s") + " with price increases")
            .description("These payments stepped up to a new price and stayed there.")
            .recommendation("Check whether the new price is worth it, or switch to a cheaper plan.")
            .monthlyImpact(total.setScale(2, RoundingMode.HALF_UP))
            .annualImpact(total.multiply(BigDecimal.valueOf(12)).setScale(2, RoundingMode.HALF_UP))
            .affectedItemsCount(items.size())
            .items(items)
            .evidence(evidence(METHOD_CHANGE_POINT, from, asOf, inputs,
                items.stream().mapToDouble(i -> i.getEvidence().getConfidence()).min().orElse(0)))
            .build();
    }

    public LeakInsight duplicates(List<RecurringPattern> patterns, List<Transaction> debits, LocalDate asOf) {
        LocalDate from = asOf.minusMonths(WINDOW_MONTHS);
        List<LeakItem> items = new ArrayList<>();
        BigDecimal saving = BigDecimal.ZERO;

        for (RecurringPattern p : patterns) {
            if (p.getChargesPerPeriod() < 2) continue;
            BigDecimal one = monthly(p).divide(BigDecimal.valueOf(p.getChargesPerPeriod()), 2, RoundingMode.HALF_UP);
            saving = saving.add(one.multiply(BigDecimal.valueOf(p.getChargesPerPeriod() - 1)));
            items.add(item(p, one, "Billed " + p.getChargesPerPeriod() + " times per "
                    + p.getPeriod().getDisplayName().toLowerCase() + " period",
                evidence(METHOD_PARALLEL, from, asOf, Map.of("chargesPerPeriod", p.getChargesPerPeriod()),
                    p.getProbability() == null ? null : p.getProbability().doubleValue())));
        }
        Map<String, List<RecurringPattern>> byKey = patterns.stream()
            .collect(Collectors.groupingBy(RecurringPattern::getNormalizedKey, TreeMap::new, Collectors.toList()));
        for (List<RecurringPattern> same : byKey.values()) {
            if (same.size() < 2) continue;
            List<RecurringPattern> sorted = same.stream().sorted(Comparator.comparing(this::monthly)).toList();
            saving = saving.add(monthly(sorted.get(0)));
            for (RecurringPattern p : sorted) {
                items.add(item(p, monthly(p), p.getPeriod().getDisplayName() + " plan alongside another plan",
                    evidence(METHOD_SAME_KEY, from, asOf, Map.of("merchantKey", p.getNormalizedKey(),
                        "plans", same.size()), null)));
            }
        }

        Map<String, TransactionCategory> categories = categoryByKey(debits);
        Map<TransactionCategory, Map<String, RecurringPattern>> byCategory = new EnumMap<>(TransactionCategory.class);
        for (RecurringPattern p : patterns) {
            TransactionCategory c = categories.get(p.getNormalizedKey());
            if (c == null || !OVERLAP_CATEGORIES.contains(c)) continue;
            // one entry per canonical merchant, whatever its spellings or plans
            byCategory.computeIfAbsent(c, k -> new TreeMap<>()).merge(p.getNormalizedKey(), p,
                (a, b) -> monthly(a).compareTo(monthly(b)) >= 0 ? a : b);
        }
        TransactionCategory overlap = byCategory.entrySet().stream()
            .filter(e -> e.getValue().size() >= 2)
            .max(Comparator.<Map.Entry<TransactionCategory, Map<String, RecurringPattern>>>comparingInt(e -> e.getValue().size())
                .thenComparing(Map.Entry::getKey, Comparator.reverseOrder()))
            .map(Map.Entry::getKey).orElse(null);
        String overlapTitle = null;
        if (overlap != null) {
            List<RecurringPattern> members = byCategory.get(overlap).values().stream()
                .sorted(Comparator.comparing(this::monthly, Comparator.reverseOrder())).toList();
            saving = saving.add(monthly(members.get(members.size() - 1)));
            overlapTitle = overlapTitle(members.size(), overlap);
            for (RecurringPattern p : members) {
                items.add(item(p, monthly(p), p.getPeriod().getDisplayName() + " · " + p.getOccurrenceCount() + " payments",
                    evidence(METHOD_OVERLAP, from, asOf, Map.of("category", overlap.name(), "services", members.size()),
                        p.getProbability() == null ? null : p.getProbability().doubleValue())));
            }
        }
        if (items.isEmpty()) return null;

        long billedTwice = items.stream().filter(i -> !METHOD_OVERLAP.equals(i.getEvidence().getMethod()))
            .map(LeakItem::getMerchant).distinct().count();
        String title = billedTwice == 0 ? overlapTitle
            : (billedTwice == 1 ? "1 service billed more than once" : billedTwice + " services billed more than once")
              + (overlapTitle == null ? "" : " · " + overlapTitle);
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("overlapCategories", OVERLAP_CATEGORIES.stream().map(Enum::name).sorted().toList());
        inputs.put("groupedBy", "merchant key");
        return LeakInsight.builder()
            .type("DUPLICATE_SUBSCRIPTIONS")
            .severity(severity.apply(saving))
            .title(title)
            .description(billedTwice > 0
                ? "The same service is being paid more than once, or you hold overlapping subscriptions."
                : String.format("You have %d active subscriptions in %s.", byCategory.get(overlap).size(),
                    overlap.getDisplayName()))
            .recommendation("Cancel the extra charges and keep one plan per service.")
            .monthlyImpact(saving.setScale(2, RoundingMode.HALF_UP))
            .annualImpact(saving.multiply(BigDecimal.valueOf(12)).setScale(2, RoundingMode.HALF_UP))
            .affectedItemsCount(items.size())
            .items(items)
            .evidence(evidence("duplicate:canonical-key", from, asOf, inputs, null))
            .build();
    }

    public LeakInsight drains(List<Transaction> debits, List<RecurringPattern> patterns, SpendBaseline baseline,
                              LocalDate asOf) {
        LocalDate from = asOf.minusMonths(WINDOW_MONTHS);
        Set<String> recurringKeys = patterns.stream().map(RecurringPattern::getNormalizedKey).collect(Collectors.toSet());
        Map<String, List<Transaction>> byKey = byKey(debits.stream()
            .filter(t -> !t.getTransactionDate().isBefore(from))
            .filter(t -> t.getCategory() == null || !NOT_HABITS.contains(t.getCategory()))
            .toList());
        String method = baseline.personal() ? METHOD_DRAIN : METHOD_DRAIN_GLOBAL;
        List<LeakItem> items = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<String, List<Transaction>> e : byKey.entrySet()) {
            List<Transaction> visits = e.getValue();
            if (recurringKeys.contains(e.getKey()) || visits.size() < DRAIN_MIN_COUNT) continue;
            double medianTicket = PriceChangeDetector.median(visits.stream().mapToDouble(t -> t.getAmount().doubleValue()).toArray());
            if (medianTicket > baseline.maxTicket().doubleValue()) continue;
            BigDecimal sum = visits.stream().map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal monthly = sum.divide(BigDecimal.valueOf(WINDOW_MONTHS), 2, RoundingMode.HALF_UP);
            if (monthly.compareTo(baseline.minMonthly()) < 0) continue;
            TransactionCategory cat = visits.get(visits.size() - 1).getCategory();
            Map<String, Object> inputs = new LinkedHashMap<>();
            inputs.put("visits", visits.size());
            inputs.put("medianTicket", round(medianTicket));
            inputs.put("monthlySpend", monthly);
            items.add(LeakItem.builder()
                .merchant(mostCommonMerchant(visits))
                .amount(monthly)
                .detail(visits.size() + " visits · median ₹" + String.format("%,.0f", medianTicket) + "/visit")
                .category(cat != null ? cat.name() : TransactionCategory.UNCATEGORIZED.name())
                .categoryLabel(cat != null ? cat.getDisplayName() : "Uncategorized")
                .evidence(evidence(method, from, asOf, inputs, null))
                .build());
            total = total.add(monthly);
        }
        if (items.isEmpty()) return null;
        items.sort(Comparator.comparing(LeakItem::getAmount).reversed());
        Map<String, Object> inputs = new LinkedHashMap<>();
        inputs.put("minVisits", DRAIN_MIN_COUNT);
        inputs.put("maxMedianTicket", baseline.maxTicket());
        inputs.put("minMonthlySpend", baseline.minMonthly());
        inputs.put("personalBaseline", baseline.personal());
        inputs.put("excludedCategories", NOT_HABITS.stream().map(Enum::name).sorted().toList());
        return LeakInsight.builder()
            .type("HIGH_FREQUENCY_SMALL_SPEND")
            .severity(severity.apply(total))
            .title("Silent drains: " + items.size() + " high-frequency merchant" + (items.size() == 1 ? "" : "s"))
            .description("Small, frequent purchases that add up to significant monthly outflows.")
            .recommendation("Habits are easier to reduce than to eliminate — track these visits to make conscious choices.")
            .monthlyImpact(total.setScale(2, RoundingMode.HALF_UP))
            .annualImpact(total.multiply(BigDecimal.valueOf(12)).setScale(2, RoundingMode.HALF_UP))
            .affectedItemsCount(items.size())
            .items(items)
            .evidence(evidence(method, from, asOf, inputs, null))
            .build();
    }

    /** "3 entertainment subscriptions", or "3 subscriptions" when the category already says so. */
    public static String overlapTitle(int count, TransactionCategory category) {
        String label = category.getDisplayName().toLowerCase();
        return label.contains("subscription") ? count + " subscriptions" : count + " " + label + " subscriptions";
    }

    private Map<String, List<Transaction>> byKey(List<Transaction> debits) {
        return debits.stream().filter(t -> t.getMerchant() != null && !t.getMerchant().isBlank())
            .collect(Collectors.groupingBy(t -> keyOf.apply(t.getMerchant()), TreeMap::new, Collectors.toList()));
    }

    // last category wins per key, as in the rules
    private Map<String, TransactionCategory> categoryByKey(List<Transaction> debits) {
        Map<String, TransactionCategory> map = new TreeMap<>();
        debits.stream().filter(t -> t.getMerchant() != null && t.getCategory() != null)
            .sorted(Comparator.comparing(Transaction::getTransactionDate))
            .forEach(t -> map.put(keyOf.apply(t.getMerchant()), t.getCategory()));
        return map;
    }

    private LeakItem item(RecurringPattern p, BigDecimal amount, String detail, LeakEvidence evidence) {
        return LeakItem.builder().merchant(p.getMerchant()).amount(amount).detail(detail)
            .category(p.getPeriod().name()).categoryLabel(p.getPeriod().getDisplayName()).evidence(evidence).build();
    }

    private BigDecimal monthly(RecurringPattern p) {
        if (p.getEstimatedAmount() == null) return BigDecimal.ZERO;
        return p.getEstimatedAmount().multiply(BigDecimal.valueOf(p.getPeriod().getAnnualFrequency()))
            .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
    }

    private static String mostCommonMerchant(List<Transaction> visits) {
        return visits.stream().collect(Collectors.groupingBy(Transaction::getMerchant, TreeMap::new, Collectors.counting()))
            .entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElseThrow();
    }

    private static LeakEvidence evidence(String method, LocalDate from, LocalDate to, Map<String, Object> inputs,
                                         Double confidence) {
        return LeakEvidence.builder().source("MODEL").method(method).windowStart(from).windowEnd(to)
            .inputs(inputs).confidence(confidence == null ? null : Math.round(confidence * 1000) / 1000.0).build();
    }

    private static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
