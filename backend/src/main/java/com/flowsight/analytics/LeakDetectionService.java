package com.flowsight.analytics;

import com.flowsight.dto.leak.LeakDetectionResponse;
import com.flowsight.dto.leak.LeakInsight;
import com.flowsight.dto.leak.LeakItem;
import com.flowsight.entity.RecurringPattern;
import com.flowsight.entity.Transaction;
import com.flowsight.entity.TransactionCategory;
import com.flowsight.repository.RecurringPatternRepository;
import com.flowsight.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

// Detects recoverable spending leaks on-demand; no new persistence.
// Types: duplicate subscriptions, subscription creep, silent drains, bank fees.
@Service
@RequiredArgsConstructor
@Slf4j
public class LeakDetectionService {

    private static final int LOOKBACK_MONTHS = 3;

    private static final int        FREQ_MIN_COUNT        = 8;          // ≥ 8 visits in window
    private static final BigDecimal FREQ_MAX_PER_TX       = new BigDecimal("500");
    private static final BigDecimal FREQ_MIN_MONTHLY      = new BigDecimal("1500");

    // creep: ≥4 occurrences, recent avg > earlier avg × 1.10
    private static final int    CREEP_MIN_OCCURRENCES = 4;
    private static final double CREEP_RATIO_THRESHOLD = 1.10;

    // severity thresholds (monthly impact)
    private static final BigDecimal SEVERITY_HIGH   = new BigDecimal("2000");
    private static final BigDecimal SEVERITY_MEDIUM = new BigDecimal("500");

    private static final Set<TransactionCategory> DUPLICATE_DETECT_CATEGORIES = Set.of(
        TransactionCategory.SUBSCRIPTIONS,
        TransactionCategory.ENTERTAINMENT,
        TransactionCategory.EDUCATION
    );

    // word-boundary based to avoid false positives like "Charging Station"
    private static final Pattern FEE_PATTERN = Pattern.compile(
        "\\b(fee|fees|penalty|penalties|overdraft|late\\s+(?:fee|charge|payment)|" +
        "atm\\s+(?:fee|charge)|processing\\s+(?:fee|charge)|service\\s+charge|" +
        "finance\\s+charge|annual\\s+(?:fee|charge)|maintenance\\s+(?:fee|charge)|" +
        "convenience\\s+(?:fee|charge)|cancellation\\s+(?:fee|charge))\\b",
        Pattern.CASE_INSENSITIVE
    );

    private final TransactionRepository      transactionRepository;
    private final RecurringPatternRepository patternRepository;

    public LeakDetectionResponse detectLeaks(UUID userId) {
        LocalDate from = LocalDate.now().minusMonths(LOOKBACK_MONTHS);

        List<Transaction> txns = transactionRepository.findForRecurringDetection(userId, from);
        List<RecurringPattern> patterns = patternRepository
            .findByUserIdAndIsDismissedFalseOrderByEstimatedAmountDesc(userId);

        Map<String, TransactionCategory> merchantCategory = buildMerchantCategoryMap(txns);

        List<LeakInsight> leaks = new ArrayList<>();
        addIfPresent(leaks, detectDuplicateSubscriptions(patterns, merchantCategory));
        addIfPresent(leaks, detectSubscriptionCreep(patterns, txns));
        addIfPresent(leaks, detectHighFrequencySmallSpend(txns));
        addIfPresent(leaks, detectBankFees(txns));

        // biggest opportunities first
        leaks.sort(Comparator.comparing(
            LeakInsight::getMonthlyImpact, Comparator.nullsLast(Comparator.reverseOrder())));

        BigDecimal totalMonthly = leaks.stream()
            .map(LeakInsight::getMonthlyImpact)
            .filter(Objects::nonNull)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalAnnual = totalMonthly.multiply(BigDecimal.valueOf(12))
            .setScale(2, RoundingMode.HALF_UP);

        log.debug("Detected {} leaks for user {}: monthly={}, annual={}",
            leaks.size(), userId, totalMonthly, totalAnnual);

        return LeakDetectionResponse.builder()
            .totalLeaksFound(leaks.size())
            .totalMonthlyImpact(totalMonthly.setScale(2, RoundingMode.HALF_UP))
            .totalAnnualImpact(totalAnnual)
            .leaks(leaks)
            .build();
    }

    private LeakInsight detectDuplicateSubscriptions(
        List<RecurringPattern> patterns,
        Map<String, TransactionCategory> merchantCategory
    ) {
        Map<TransactionCategory, List<RecurringPattern>> byCategory = new EnumMap<>(TransactionCategory.class);
        for (RecurringPattern p : patterns) {
            TransactionCategory cat = merchantCategory.get(p.getMerchant());
            if (cat == null || !DUPLICATE_DETECT_CATEGORIES.contains(cat)) continue;
            byCategory.computeIfAbsent(cat, k -> new ArrayList<>()).add(p);
        }

        Optional<Map.Entry<TransactionCategory, List<RecurringPattern>>> winner = byCategory.entrySet().stream()
            .filter(e -> e.getValue().size() >= 2)
            .max(Comparator.comparingInt(e -> e.getValue().size()));

        if (winner.isEmpty()) return null;

        TransactionCategory cat = winner.get().getKey();
        List<RecurringPattern> dupes = winner.get().getValue();

        // cancel the cheapest to keep the best service
        BigDecimal cheapestMonthly = dupes.stream()
            .map(this::monthlyEquivalent)
            .min(BigDecimal::compareTo)
            .orElse(BigDecimal.ZERO);

        BigDecimal totalMonthly = dupes.stream()
            .map(this::monthlyEquivalent)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<LeakItem> items = dupes.stream()
            .sorted(Comparator.comparing(this::monthlyEquivalent, Comparator.reverseOrder()))
            .map(p -> LeakItem.builder()
                .merchant(p.getMerchant())
                .amount(monthlyEquivalent(p))
                .detail(p.getPeriod().getDisplayName() + " · " + p.getOccurrenceCount() + " payments")
                .category(cat.name())
                .categoryLabel(cat.getDisplayName())
                .build())
            .collect(Collectors.toList());

        return LeakInsight.builder()
            .type("DUPLICATE_SUBSCRIPTIONS")
            .severity(severityFor(cheapestMonthly))
            .title(dupes.size() + " " + cat.getDisplayName().toLowerCase() + " subscriptions")
            .description(String.format(
                "You have %d active subscriptions in %s totaling ₹%s/month.",
                dupes.size(), cat.getDisplayName(), formatBD(totalMonthly)))
            .recommendation("Review if all are necessary — cancelling the smallest saves ₹"
                + formatBD(cheapestMonthly) + "/month.")
            .monthlyImpact(cheapestMonthly.setScale(2, RoundingMode.HALF_UP))
            .annualImpact(cheapestMonthly.multiply(BigDecimal.valueOf(12)).setScale(2, RoundingMode.HALF_UP))
            .affectedItemsCount(dupes.size())
            .items(items)
            .build();
    }

    private LeakInsight detectSubscriptionCreep(List<RecurringPattern> patterns, List<Transaction> allTxns) {
        Map<String, List<Transaction>> byMerchant = allTxns.stream()
            .filter(t -> t.getMerchant() != null)
            .collect(Collectors.groupingBy(Transaction::getMerchant));

        List<LeakItem> crept = new ArrayList<>();
        BigDecimal totalImpact = BigDecimal.ZERO;

        for (RecurringPattern p : patterns) {
            List<Transaction> txns = byMerchant.getOrDefault(p.getMerchant(), List.of());
            if (txns.size() < CREEP_MIN_OCCURRENCES) continue;

            // compare the last 2 payments vs the earlier average
            txns.sort(Comparator.comparing(Transaction::getTransactionDate));
            int recentCount = 2;
            int earlierCount = txns.size() - recentCount;

            double earlierAvg = txns.subList(0, earlierCount).stream()
                .mapToDouble(t -> t.getAmount().doubleValue()).average().orElse(0);
            double recentAvg = txns.subList(earlierCount, txns.size()).stream()
                .mapToDouble(t -> t.getAmount().doubleValue()).average().orElse(0);

            if (earlierAvg <= 0 || recentAvg / earlierAvg < CREEP_RATIO_THRESHOLD) continue;

            BigDecimal delta = BigDecimal.valueOf(recentAvg - earlierAvg).setScale(2, RoundingMode.HALF_UP);
            BigDecimal monthlyDelta = delta.multiply(BigDecimal.valueOf(p.getPeriod().getAnnualFrequency()))
                .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);

            totalImpact = totalImpact.add(monthlyDelta);
            double percentIncrease = (recentAvg - earlierAvg) / earlierAvg * 100;

            crept.add(LeakItem.builder()
                .merchant(p.getMerchant())
                .amount(monthlyDelta)
                .detail(String.format("₹%.0f → ₹%.0f (+%.0f%%)", earlierAvg, recentAvg, percentIncrease))
                .category(p.getPeriod().getDisplayName())
                .categoryLabel(p.getPeriod().getDisplayName())
                .build());
        }

        if (crept.isEmpty()) return null;

        return LeakInsight.builder()
            .type("SUBSCRIPTION_CREEP")
            .severity(severityFor(totalImpact))
            .title(crept.size() + " subscription" + (crept.size() == 1 ? "" : "s") + " with price increases")
            .description("Recent payments are higher than your historical average — likely silent price hikes.")
            .recommendation("Compare current prices to your past payments and contact merchants if increases were unannounced.")
            .monthlyImpact(totalImpact.setScale(2, RoundingMode.HALF_UP))
            .annualImpact(totalImpact.multiply(BigDecimal.valueOf(12)).setScale(2, RoundingMode.HALF_UP))
            .affectedItemsCount(crept.size())
            .items(crept)
            .build();
    }

    private LeakInsight detectHighFrequencySmallSpend(List<Transaction> txns) {
        Map<String, List<Transaction>> byMerchant = txns.stream()
            .filter(t -> t.getMerchant() != null && !t.getMerchant().isBlank())
            .filter(t -> t.getAmount().compareTo(FREQ_MAX_PER_TX) <= 0)
            .collect(Collectors.groupingBy(Transaction::getMerchant));

        List<LeakItem> drains = new ArrayList<>();
        BigDecimal totalMonthly = BigDecimal.ZERO;

        for (Map.Entry<String, List<Transaction>> entry : byMerchant.entrySet()) {
            List<Transaction> merchantTxns = entry.getValue();
            if (merchantTxns.size() < FREQ_MIN_COUNT) continue;

            BigDecimal total = merchantTxns.stream()
                .map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

            // normalize to monthly (window is LOOKBACK_MONTHS)
            BigDecimal monthly = total.divide(BigDecimal.valueOf(LOOKBACK_MONTHS), 2, RoundingMode.HALF_UP);
            if (monthly.compareTo(FREQ_MIN_MONTHLY) < 0) continue;

            BigDecimal avg = total.divide(BigDecimal.valueOf(merchantTxns.size()), 2, RoundingMode.HALF_UP);
            TransactionCategory cat = merchantTxns.get(merchantTxns.size() - 1).getCategory();

            drains.add(LeakItem.builder()
                .merchant(entry.getKey())
                .amount(monthly)
                .detail(merchantTxns.size() + " visits · avg ₹" + formatBD(avg) + "/visit")
                .category(cat != null ? cat.name() : TransactionCategory.UNCATEGORIZED.name())
                .categoryLabel(cat != null ? cat.getDisplayName() : "Uncategorized")
                .build());

            totalMonthly = totalMonthly.add(monthly);
        }

        if (drains.isEmpty()) return null;

        drains.sort(Comparator.comparing(LeakItem::getAmount).reversed());

        return LeakInsight.builder()
            .type("HIGH_FREQUENCY_SMALL_SPEND")
            .severity(severityFor(totalMonthly))
            .title("Silent drains: " + drains.size() + " high-frequency merchant" + (drains.size() == 1 ? "" : "s"))
            .description("Small, frequent purchases that add up to significant monthly outflows.")
            .recommendation("Habits are easier to reduce than to eliminate — track these visits to make conscious choices.")
            .monthlyImpact(totalMonthly.setScale(2, RoundingMode.HALF_UP))
            .annualImpact(totalMonthly.multiply(BigDecimal.valueOf(12)).setScale(2, RoundingMode.HALF_UP))
            .affectedItemsCount(drains.size())
            .items(drains)
            .build();
    }

    private LeakInsight detectBankFees(List<Transaction> txns) {
        Map<String, List<Transaction>> feesByMerchant = new LinkedHashMap<>();

        for (Transaction tx : txns) {
            String description = tx.getDescription() != null ? tx.getDescription() : "";
            String merchant    = tx.getMerchant()    != null ? tx.getMerchant()    : "";
            String combined    = description + " " + merchant;

            if (!FEE_PATTERN.matcher(combined).find()) continue;

            String key = merchant.isBlank() ? "Bank fees" : merchant;
            feesByMerchant.computeIfAbsent(key, k -> new ArrayList<>()).add(tx);
        }

        if (feesByMerchant.isEmpty()) return null;

        List<LeakItem> items = new ArrayList<>();
        BigDecimal totalAll = BigDecimal.ZERO;

        for (Map.Entry<String, List<Transaction>> entry : feesByMerchant.entrySet()) {
            List<Transaction> txnsFromMerchant = entry.getValue();
            BigDecimal totalForMerchant = txnsFromMerchant.stream()
                .map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

            totalAll = totalAll.add(totalForMerchant);

            items.add(LeakItem.builder()
                .merchant(entry.getKey())
                .amount(totalForMerchant)
                .detail(txnsFromMerchant.size() + " charge" + (txnsFromMerchant.size() == 1 ? "" : "s") + " over " + LOOKBACK_MONTHS + " months")
                .category(TransactionCategory.FINANCE.name())
                .categoryLabel(TransactionCategory.FINANCE.getDisplayName())
                .build());
        }

        items.sort(Comparator.comparing(LeakItem::getAmount).reversed());
        BigDecimal monthly = totalAll.divide(BigDecimal.valueOf(LOOKBACK_MONTHS), 2, RoundingMode.HALF_UP);

        return LeakInsight.builder()
            .type("BANK_FEES")
            .severity(severityFor(monthly))
            .title("Bank fees and charges detected")
            .description(items.size() + " merchant" + (items.size() == 1 ? "" : "s")
                + " charged fees totaling ₹" + formatBD(totalAll) + " in the last " + LOOKBACK_MONTHS + " months.")
            .recommendation("Many fees are negotiable — call your bank or switch providers if these recur monthly.")
            .monthlyImpact(monthly)
            .annualImpact(monthly.multiply(BigDecimal.valueOf(12)).setScale(2, RoundingMode.HALF_UP))
            .affectedItemsCount(items.size())
            .items(items)
            .build();
    }

    private Map<String, TransactionCategory> buildMerchantCategoryMap(List<Transaction> txns) {
        // last category wins per merchant
        Map<String, TransactionCategory> map = new HashMap<>();
        for (Transaction tx : txns) {
            if (tx.getMerchant() == null || tx.getCategory() == null) continue;
            map.put(tx.getMerchant(), tx.getCategory());
        }
        return map;
    }

    private BigDecimal monthlyEquivalent(RecurringPattern p) {
        if (p.getEstimatedAmount() == null) return BigDecimal.ZERO;
        return p.getEstimatedAmount()
            .multiply(BigDecimal.valueOf(p.getPeriod().getAnnualFrequency()))
            .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
    }

    private String severityFor(BigDecimal monthlyImpact) {
        if (monthlyImpact.compareTo(SEVERITY_HIGH)   >= 0) return "HIGH";
        if (monthlyImpact.compareTo(SEVERITY_MEDIUM) >= 0) return "MEDIUM";
        return "LOW";
    }

    private static String formatBD(BigDecimal v) {
        return v == null ? "0" : String.format("%,.0f", v.doubleValue());
    }

    private static void addIfPresent(List<LeakInsight> list, LeakInsight insight) {
        if (insight != null) list.add(insight);
    }
}
