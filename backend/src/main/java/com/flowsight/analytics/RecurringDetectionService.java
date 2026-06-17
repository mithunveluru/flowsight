package com.flowsight.analytics;

import com.flowsight.analytics.MerchantNormalizationService.Normalized;
import com.flowsight.dto.recurring.RecurringPatternResponse;
import com.flowsight.entity.*;
import com.flowsight.repository.RecurringPatternRepository;
import com.flowsight.repository.TransactionRepository;
import com.flowsight.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Detects recurring payment patterns from a user's transaction history.
 *
 * <p>Algorithm per merchant group:
 * <ol>
 *   <li>Normalize merchants via {@link MerchantNormalizationService}
 *       (brand aliases, suffix stripping, txn-reference removal).</li>
 *   <li>Sort transactions by date, compute all consecutive intervals.</li>
 *   <li>Take the median interval and classify into a {@link RecurringPeriod}.</li>
 *   <li>Score confidence from four signals:
 *       occurrence count, interval consistency (tolerating one missed cycle),
 *       amount stability, and category match.</li>
 *   <li>Persist patterns (preserving user-confirmed and dismissed ones).</li>
 * </ol>
 *
 * <p>Minimum requirements to emit a pattern:
 * <ul>
 *   <li>≥ 2 occurrences</li>
 *   <li>Median interval matches a known period</li>
 *   <li>Confidence ≥ 0.30 (POSSIBLE tier — surfaced for user review)</li>
 * </ul>
 *
 * <p>User-confirmed patterns are <em>always</em> preserved across re-scans, their
 * metadata refreshed from the latest transactions. Dismissed patterns are also
 * preserved so they don't reappear in re-scans.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecurringDetectionService {

    private static final int     LOOKBACK_MONTHS  = 24;
    private static final double  CONFIDENCE_FLOOR = 0.30; // POSSIBLE tier
    private static final int     MIN_OCCURRENCES  = 2;
    private static final int     MIN_KEY_LENGTH   = 3;

    // Confidence weight tuning
    private static final double  W_OCCURRENCE = 0.35;
    private static final double  W_CONSISTENCY = 0.35;
    private static final double  W_AMOUNT      = 0.15;
    private static final double  W_CATEGORY    = 0.15;

    // Categories that boost confidence (typical recurring-payment buckets)
    private static final Set<TransactionCategory> RECURRING_CATEGORIES = Set.of(
        TransactionCategory.SUBSCRIPTIONS,
        TransactionCategory.UTILITIES,
        TransactionCategory.FINANCE,
        TransactionCategory.ENTERTAINMENT,
        TransactionCategory.EDUCATION,
        TransactionCategory.HEALTHCARE
    );

    private static final Set<TransactionCategory> CANCELLATION_CATEGORIES = Set.of(
        TransactionCategory.SUBSCRIPTIONS,
        TransactionCategory.ENTERTAINMENT,
        TransactionCategory.EDUCATION
    );

    private final TransactionRepository       transactionRepository;
    private final RecurringPatternRepository  patternRepository;
    private final UserRepository              userRepository;
    private final MerchantNormalizationService merchantNormalizer;

    // Public API

    /**
     * Runs detection, persists patterns, and returns all active (non-dismissed) patterns.
     *
     * <p>State handling per composite key (normalizedKey + period):
     * <ul>
     *   <li><b>Dismissed</b> — preserved, excluded from response</li>
     *   <li><b>User-confirmed</b> — preserved, metadata refreshed from latest detection</li>
     *   <li><b>Auto-detected (default)</b> — fully replaced by fresh detection</li>
     * </ul>
     */
    @Transactional
    public List<RecurringPatternResponse> detectAndRefresh(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow();

        LocalDate from = LocalDate.now().minusMonths(LOOKBACK_MONTHS);
        List<Transaction> txns = transactionRepository.findForRecurringDetection(userId, from);

        // Index existing patterns by composite key
        Map<String, RecurringPattern> existing = patternRepository
            .findByUserIdOrderByConfidenceDesc(userId).stream()
            .collect(Collectors.toMap(
                p -> compositeKey(p.getNormalizedKey(), p.getPeriod()),
                p -> p,
                (a, b) -> a // shouldn't happen due to unique constraint
            ));

        Set<String> dismissedKeys = existing.entrySet().stream()
            .filter(e -> e.getValue().isDismissed())
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());

        Set<String> confirmedKeys = existing.entrySet().stream()
            .filter(e -> e.getValue().isUserConfirmed())
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());

        // Delete auto-detected patterns; keep user-confirmed and dismissed
        patternRepository.deleteActiveNonConfirmedByUserId(userId);

        // Detect from transaction history
        Map<String, MerchantGroup> groups = groupByNormalizedMerchant(txns);
        List<RecurringPattern> resultPatterns = new ArrayList<>();

        for (Map.Entry<String, MerchantGroup> entry : groups.entrySet()) {
            RecurringPattern fresh = analyze(entry.getKey(), entry.getValue(), user);
            if (fresh == null) continue;

            String ck = compositeKey(fresh.getNormalizedKey(), fresh.getPeriod());

            if (dismissedKeys.contains(ck)) {
                continue; // user already said "not recurring"
            }

            if (confirmedKeys.contains(ck)) {
                // Refresh confirmed pattern's metadata
                RecurringPattern confirmed = existing.get(ck);
                confirmed.setMerchant(fresh.getMerchant());
                confirmed.setEstimatedAmount(fresh.getEstimatedAmount());
                confirmed.setLastSeenDate(fresh.getLastSeenDate());
                confirmed.setNextExpectedDate(fresh.getNextExpectedDate());
                confirmed.setOccurrenceCount(fresh.getOccurrenceCount());
                confirmed.setConfidence(fresh.getConfidence());
                confirmed.setCancellationCandidate(fresh.isCancellationCandidate());
                resultPatterns.add(patternRepository.save(confirmed));
                continue;
            }

            resultPatterns.add(patternRepository.save(fresh));
        }

        // Re-include user-confirmed patterns that weren't detected this round
        // (they keep stale metadata until detected again — UI should show a hint)
        for (Map.Entry<String, RecurringPattern> entry : existing.entrySet()) {
            if (!entry.getValue().isUserConfirmed()) continue;
            if (entry.getValue().isDismissed())     continue;
            boolean alreadyIncluded = resultPatterns.stream()
                .anyMatch(p -> p.getId().equals(entry.getValue().getId()));
            if (!alreadyIncluded) resultPatterns.add(entry.getValue());
        }

        log.debug("Detected {} recurring patterns for user {}", resultPatterns.size(), userId);

        return resultPatterns.stream()
            .sorted(Comparator
                .comparing(RecurringPattern::isUserConfirmed).reversed()
                .thenComparing(RecurringPattern::getEstimatedAmount,
                    Comparator.nullsLast(Comparator.reverseOrder())))
            .map(this::toResponse)
            .collect(Collectors.toList());
    }

    /** Returns stored patterns without re-running detection. */
    public List<RecurringPatternResponse> getStored(UUID userId) {
        return patternRepository
            .findByUserIdAndIsDismissedFalseOrderByEstimatedAmountDesc(userId)
            .stream().map(this::toResponse).collect(Collectors.toList());
    }

    @Transactional
    public RecurringPatternResponse dismiss(UUID patternId, UUID userId) {
        RecurringPattern p = patternRepository.findByIdAndUserId(patternId, userId)
            .orElseThrow(() -> new com.flowsight.exception.ResourceNotFoundException("RecurringPattern", patternId));
        p.setDismissed(true);
        p.setUserConfirmed(false); // mutually exclusive
        return toResponse(patternRepository.save(p));
    }

    @Transactional
    public RecurringPatternResponse restore(UUID patternId, UUID userId) {
        RecurringPattern p = patternRepository.findByIdAndUserId(patternId, userId)
            .orElseThrow(() -> new com.flowsight.exception.ResourceNotFoundException("RecurringPattern", patternId));
        p.setDismissed(false);
        return toResponse(patternRepository.save(p));
    }

    @Transactional
    public RecurringPatternResponse confirm(UUID patternId, UUID userId) {
        RecurringPattern p = patternRepository.findByIdAndUserId(patternId, userId)
            .orElseThrow(() -> new com.flowsight.exception.ResourceNotFoundException("RecurringPattern", patternId));
        p.setUserConfirmed(true);
        p.setDismissed(false); // un-dismiss if previously dismissed
        return toResponse(patternRepository.save(p));
    }

    @Transactional
    public RecurringPatternResponse unconfirm(UUID patternId, UUID userId) {
        RecurringPattern p = patternRepository.findByIdAndUserId(patternId, userId)
            .orElseThrow(() -> new com.flowsight.exception.ResourceNotFoundException("RecurringPattern", patternId));
        p.setUserConfirmed(false);
        return toResponse(patternRepository.save(p));
    }

    // Detection algorithm

    private Map<String, MerchantGroup> groupByNormalizedMerchant(List<Transaction> txns) {
        Map<String, MerchantGroup> groups = new LinkedHashMap<>();
        for (Transaction tx : txns) {
            String rawMerchant = tx.getMerchant();
            if (rawMerchant == null || rawMerchant.isBlank()) continue;

            Normalized norm = merchantNormalizer.normalize(rawMerchant);
            if (norm.isEmpty() || norm.getKey().length() < MIN_KEY_LENGTH) continue;

            groups.computeIfAbsent(norm.getKey(), k -> new MerchantGroup(norm))
                  .add(tx);
        }
        return groups;
    }

    private RecurringPattern analyze(String key, MerchantGroup group, User user) {
        List<Transaction> txns = group.txns;
        if (txns.size() < MIN_OCCURRENCES) return null;

        txns.sort(Comparator.comparing(Transaction::getTransactionDate));

        // Compute consecutive intervals (in days)
        List<Integer> intervals = new ArrayList<>();
        for (int i = 1; i < txns.size(); i++) {
            int days = (int) ChronoUnit.DAYS.between(
                txns.get(i - 1).getTransactionDate(),
                txns.get(i).getTransactionDate()
            );
            if (days > 0) intervals.add(days);
        }
        if (intervals.isEmpty()) return null;

        // Classify period by median interval
        List<Integer> sorted = new ArrayList<>(intervals);
        Collections.sort(sorted);
        int median = sorted.get(sorted.size() / 2);
        RecurringPeriod period = RecurringPeriod.fromDays(median);
        if (period == null) return null;

        // Signal 1: interval consistency — full credit if within range,
        // partial credit if within 2× max (tolerates one missed cycle)
        double consistencyScore = intervals.stream()
            .mapToDouble(d -> {
                if (d >= period.getMinDays() && d <= period.getMaxDays()) return 1.0;
                if (d <= period.getMaxDays() * 2)                          return 0.5;
                return 0.0;
            })
            .average().orElse(0);

        // Signal 2: amount stability
        DoubleSummaryStatistics stats = txns.stream()
            .mapToDouble(tx -> tx.getAmount().doubleValue())
            .summaryStatistics();
        double amountScore = stats.getAverage() > 0
            ? Math.max(0.0, 1.0 - (stats.getMax() - stats.getMin()) / stats.getAverage())
            : 0.0;

        // Signal 3: occurrence count (saturates at 6)
        double occurrenceScore = Math.min(txns.size(), 6) / 6.0;

        // Signal 4: category match — does any txn belong to a known recurring category?
        TransactionCategory dominantCategory = mostCommonCategory(txns);
        double categoryScore = dominantCategory != null && RECURRING_CATEGORIES.contains(dominantCategory)
            ? 1.0 : 0.5;

        // Weighted confidence
        double confidence =
              occurrenceScore  * W_OCCURRENCE
            + consistencyScore * W_CONSISTENCY
            + amountScore      * W_AMOUNT
            + categoryScore    * W_CATEGORY;

        if (confidence < CONFIDENCE_FLOOR) return null;

        // Display merchant: canonical name from alias map if available, else most-common raw
        String merchantDisplay = group.normalized.getCanonicalName() != null
            ? group.normalized.getCanonicalName()
            : mostCommonMerchantName(txns);

        BigDecimal estimatedAmount = BigDecimal.valueOf(stats.getAverage())
            .setScale(4, RoundingMode.HALF_UP);

        Transaction last = txns.get(txns.size() - 1);
        LocalDate nextExpected = last.getTransactionDate().plusDays(period.getNominalDays());

        boolean cancellationCandidate = dominantCategory != null
            && CANCELLATION_CATEGORIES.contains(dominantCategory);

        return RecurringPattern.builder()
            .user(user)
            .merchant(merchantDisplay)
            .normalizedKey(key)
            .period(period)
            .estimatedAmount(estimatedAmount)
            .lastSeenDate(last.getTransactionDate())
            .nextExpectedDate(nextExpected)
            .occurrenceCount(txns.size())
            .confidence(BigDecimal.valueOf(confidence).setScale(3, RoundingMode.HALF_UP))
            .isCancellationCandidate(cancellationCandidate)
            .isDismissed(false)
            .isUserConfirmed(false)
            .build();
    }

    // Response mapping

    private RecurringPatternResponse toResponse(RecurringPattern p) {
        LocalDate today = LocalDate.now();
        int daysUntilNext = p.getNextExpectedDate() != null
            ? (int) ChronoUnit.DAYS.between(today, p.getNextExpectedDate())
            : 0;

        String status;
        if (p.getNextExpectedDate() == null) {
            status = "ACTIVE";
        } else if (daysUntilNext < -p.getPeriod().getNominalDays()) {
            status = "MISSED";
        } else if (daysUntilNext < 0) {
            status = "OVERDUE";
        } else if (daysUntilNext <= 7) {
            status = "DUE_SOON";
        } else {
            status = "ACTIVE";
        }

        BigDecimal annual = p.getEstimatedAmount() != null
            ? p.getEstimatedAmount().multiply(BigDecimal.valueOf(p.getPeriod().getAnnualFrequency()))
                .setScale(2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;

        BigDecimal monthly = annual.divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);

        // Confidence tier for UI badge
        double conf = p.getConfidence() != null ? p.getConfidence().doubleValue() : 0.0;
        String confidenceTier =
              conf >= 0.70 ? "HIGH"
            : conf >= 0.50 ? "MEDIUM"
            :                "POSSIBLE";

        return RecurringPatternResponse.builder()
            .id(p.getId())
            .merchant(p.getMerchant())
            .period(p.getPeriod().name())
            .periodLabel(p.getPeriod().getDisplayName())
            .estimatedAmount(p.getEstimatedAmount())
            .annualCost(annual)
            .monthlyEquivalent(monthly)
            .lastSeenDate(p.getLastSeenDate())
            .nextExpectedDate(p.getNextExpectedDate())
            .occurrenceCount(p.getOccurrenceCount())
            .confidence(p.getConfidence())
            .confidenceTier(confidenceTier)
            .isCancellationCandidate(p.isCancellationCandidate())
            .isDismissed(p.isDismissed())
            .isUserConfirmed(p.isUserConfirmed())
            .status(status)
            .daysUntilNext(daysUntilNext)
            .build();
    }

    private TransactionCategory mostCommonCategory(List<Transaction> txns) {
        return txns.stream()
            .filter(t -> t.getCategory() != null)
            .collect(Collectors.groupingBy(Transaction::getCategory, Collectors.counting()))
            .entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(null);
    }

    private String mostCommonMerchantName(List<Transaction> txns) {
        return txns.stream()
            .collect(Collectors.groupingBy(Transaction::getMerchant, Collectors.counting()))
            .entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse(txns.get(0).getMerchant());
    }

    private static String compositeKey(String normalizedKey, RecurringPeriod period) {
        return normalizedKey + ":" + period.name();
    }

    /** Internal carrier for a merchant group's transactions + its normalization. */
    private static class MerchantGroup {
        final Normalized normalized;
        final List<Transaction> txns = new ArrayList<>();

        MerchantGroup(Normalized normalized) {
            this.normalized = normalized;
        }

        void add(Transaction tx) {
            txns.add(tx);
        }
    }
}
