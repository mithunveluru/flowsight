package com.flowsight.analytics;

import com.flowsight.analytics.MerchantNormalizationService.Normalized;
import com.flowsight.dto.recurring.RecurringPatternResponse;
import com.flowsight.entity.*;
import com.flowsight.ml.FeedbackCorrection;
import com.flowsight.ml.FeedbackService;
import com.flowsight.ml.FeedbackSource;
import com.flowsight.ml.SubjectType;
import com.flowsight.ml.recurring.RecurringScorer;
import com.flowsight.ml.recurring.RecurringScorerRequest;
import com.flowsight.ml.recurring.RecurringScorerResponse;
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

// Detects recurring payment patterns from transaction history.
// Confidence blends occurrence count, interval consistency, amount stability, category.
@Service
@RequiredArgsConstructor
@Slf4j
public class RecurringDetectionService {

    private static final int     LOOKBACK_MONTHS  = 24;
    private static final double  CONFIDENCE_FLOOR = 0.30; // POSSIBLE tier
    private static final int     MIN_OCCURRENCES  = 2;
    private static final int     MIN_KEY_LENGTH   = 3;

    // confidence weights
    private static final double  W_OCCURRENCE = 0.35;
    private static final double  W_CONSISTENCY = 0.35;
    private static final double  W_AMOUNT      = 0.15;
    private static final double  W_CATEGORY    = 0.15;

    // categories that boost confidence
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
    private final RecurringScorer             recurringScorer;
    private final FeedbackService             feedbackService;

    // Detect, persist, and return active patterns. Confirmed and dismissed keys survive re-scans.
    @Transactional
    public List<RecurringPatternResponse> detectAndRefresh(UUID userId) {
        User user = userRepository.findById(userId).orElseThrow();

        LocalDate asOf = LocalDate.now();
        LocalDate from = asOf.minusMonths(LOOKBACK_MONTHS);
        List<Transaction> txns = transactionRepository.findForRecurringDetection(userId, from);

        List<RecurringPattern> kept = patternRepository.findByUserIdOrderByConfidenceDesc(userId).stream()
            .filter(p -> p.isUserConfirmed() || p.isDismissed())
            .collect(Collectors.toList());

        // keep user-confirmed and dismissed; replace the rest
        patternRepository.deleteActiveNonConfirmedByUserId(userId);
        realignKeys(kept);

        Map<String, RecurringPattern> existing = kept.stream()
            .collect(Collectors.toMap(
                p -> compositeKey(p.getNormalizedKey(), p.getPeriod()),
                p -> p,
                (a, b) -> a // unique constraint prevents dupes
            ));

        Set<String> dismissedKeys = existing.entrySet().stream()
            .filter(e -> e.getValue().isDismissed())
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());

        Set<String> confirmedKeys = existing.entrySet().stream()
            .filter(e -> e.getValue().isUserConfirmed())
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());

        List<RecurringPattern> resultPatterns = new ArrayList<>();

        for (RecurringPattern fresh : detectServed(userId, txns, user, asOf)) {
            String ck = compositeKey(fresh.getNormalizedKey(), fresh.getPeriod());

            if (dismissedKeys.contains(ck)) {
                continue; // user already said "not recurring"
            }

            if (confirmedKeys.contains(ck)) {
                RecurringPattern confirmed = existing.get(ck);
                confirmed.setMerchant(fresh.getMerchant());
                confirmed.setEstimatedAmount(fresh.getEstimatedAmount());
                confirmed.setLastSeenDate(fresh.getLastSeenDate());
                confirmed.setNextExpectedDate(fresh.getNextExpectedDate());
                confirmed.setOccurrenceCount(fresh.getOccurrenceCount());
                confirmed.setConfidence(fresh.getConfidence());
                confirmed.setCancellationCandidate(fresh.isCancellationCandidate());
                confirmed.setProbability(fresh.getProbability());
                confirmed.setNextExpectedLow(fresh.getNextExpectedLow());
                confirmed.setNextExpectedHigh(fresh.getNextExpectedHigh());
                confirmed.setModelVersion(fresh.getModelVersion());
                confirmed.setChargesPerPeriod(fresh.getChargesPerPeriod());
                resultPatterns.add(patternRepository.save(confirmed));
                continue;
            }

            resultPatterns.add(patternRepository.save(fresh));
        }

        // re-include confirmed patterns not detected this round (keep stale metadata)
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

    // The recurring-scorer's patterns when it answers; the rules otherwise.
    private List<RecurringPattern> detectServed(UUID userId, List<Transaction> txns, User user, LocalDate asOf) {
        if (!recurringScorer.active()) return detect(txns, user);
        return recurringScorer.score(userId, scorerRequest(txns, asOf))
            .map(scores -> fromScores(txns, user, asOf, scores))
            .orElseGet(() -> detect(txns, user));
    }

    // One group per merchant key with at least two charges up to asOf, in grouping order.
    public RecurringScorerRequest scorerRequest(List<Transaction> txns, LocalDate asOf) {
        List<RecurringScorerRequest.Group> groups = new ArrayList<>();
        for (MerchantGroup g : candidates(txns, asOf)) {
            List<Transaction> recent = g.txns.subList(
                Math.max(0, g.txns.size() - RecurringScorerRequest.MAX_CHARGES), g.txns.size());
            TransactionCategory category = mostCommonCategory(g.txns);
            groups.add(new RecurringScorerRequest.Group(
                recent.stream().map(t -> (int) ChronoUnit.DAYS.between(asOf, t.getTransactionDate())).toList(),
                recent.stream().map(t -> t.getAmount().doubleValue()).toList(),
                category == null ? null : category.name()));
        }
        return RecurringScorerRequest.of(asOf, groups);
    }

    // Patterns for the groups the scorer calls recurring; scores line up with scorerRequest's groups.
    public List<RecurringPattern> fromScores(List<Transaction> txns, User user, LocalDate asOf,
                                             RecurringScorer.Scores scores) {
        List<MerchantGroup> groups = candidates(txns, asOf);
        List<RecurringPattern> found = new ArrayList<>();
        for (int i = 0; i < groups.size(); i++) {
            RecurringScorerResponse.Score score = scores.scores().get(i);
            RecurringPeriod period = parsePeriod(score.period());
            if (!score.recurring() || period == null || score.nextInDays() == null) continue;
            MerchantGroup g = groups.get(i);
            TransactionCategory dominant = mostCommonCategory(g.txns);
            int charges = Math.max(1, score.chargesPerPeriod());
            double probability = Math.max(0, Math.min(1, score.probability()));
            found.add(RecurringPattern.builder()
                .user(user)
                .merchant(displayName(g))
                .normalizedKey(g.key)
                .period(period)
                .estimatedAmount(BigDecimal.valueOf(score.amount() * charges).setScale(4, RoundingMode.HALF_UP))
                .lastSeenDate(g.txns.get(g.txns.size() - 1).getTransactionDate())
                .nextExpectedDate(asOf.plusDays(score.nextInDays()))
                .nextExpectedLow(score.nextLowInDays() == null ? null : asOf.plusDays(score.nextLowInDays()))
                .nextExpectedHigh(score.nextHighInDays() == null ? null : asOf.plusDays(score.nextHighInDays()))
                .occurrenceCount(g.txns.size())
                .confidence(BigDecimal.valueOf(probability).setScale(3, RoundingMode.HALF_UP))
                .probability(BigDecimal.valueOf(probability).setScale(4, RoundingMode.HALF_UP))
                .modelVersion(scores.modelVersion() == null ? "unknown" : scores.modelVersion())
                .chargesPerPeriod(charges)
                .isCancellationCandidate(dominant != null && CANCELLATION_CATEGORIES.contains(dominant))
                .isDismissed(false)
                .isUserConfirmed(false)
                .build());
        }
        return found;
    }

    private List<MerchantGroup> candidates(List<Transaction> txns, LocalDate asOf) {
        List<Transaction> usable = txns.stream()
            .filter(t -> !t.getTransactionDate().isAfter(asOf) && t.getAmount() != null && t.getAmount().signum() > 0)
            .toList();
        List<MerchantGroup> out = new ArrayList<>();
        for (Map.Entry<String, MerchantGroup> e : groupByNormalizedMerchant(usable).entrySet()) {
            if (e.getValue().txns.size() < MIN_OCCURRENCES) continue;
            e.getValue().txns.sort(Comparator.comparing(Transaction::getTransactionDate));
            out.add(e.getValue());
        }
        return out;
    }

    private static RecurringPeriod parsePeriod(String name) {
        if (name == null) return null;
        try {
            return RecurringPeriod.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    // Rule-based detection over the given debits; nothing is read or persisted.
    public List<RecurringPattern> detect(List<Transaction> txns, User user) {
        List<RecurringPattern> found = new ArrayList<>();
        for (Map.Entry<String, MerchantGroup> entry : groupByNormalizedMerchant(txns).entrySet()) {
            RecurringPattern p = analyze(entry.getKey(), entry.getValue(), user);
            if (p != null) found.add(p);
        }
        return found;
    }

    // stored patterns, no re-detection
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
        recordReview(userId, p, "NOT_RECURRING");
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
        recordReview(userId, p, "RECURRING");
        return toResponse(patternRepository.save(p));
    }

    @Transactional
    public RecurringPatternResponse unconfirm(UUID patternId, UUID userId) {
        RecurringPattern p = patternRepository.findByIdAndUserId(patternId, userId)
            .orElseThrow(() -> new com.flowsight.exception.ResourceNotFoundException("RecurringPattern", patternId));
        p.setUserConfirmed(false);
        return toResponse(patternRepository.save(p));
    }

    // The detector always claimed RECURRING; the user's answer is the correction.
    private void recordReview(UUID userId, RecurringPattern p, String answer) {
        BigDecimal score = p.getProbability() != null ? p.getProbability() : p.getConfidence();
        feedbackService.record(userId, new FeedbackCorrection(SubjectType.RECURRING_PATTERN, p.getId(), "recurring",
            "RECURRING", answer, FeedbackSource.RECURRING_REVIEW, p.getModelVersion(),
            score == null ? null : score.doubleValue()));
    }

    // Stored keys come from whichever MerchantNormalizationService version saved them; recompute
    // them from the pattern's merchant so kept patterns line up with today's transaction keys.
    private void realignKeys(List<RecurringPattern> kept) {
        Set<String> taken = kept.stream()
            .map(p -> compositeKey(p.getNormalizedKey(), p.getPeriod()))
            .collect(Collectors.toSet());
        for (RecurringPattern p : kept) {
            String key = merchantNormalizer.normalizeKey(p.getMerchant());
            if (key.length() < MIN_KEY_LENGTH || key.equals(p.getNormalizedKey())) continue;
            String ck = compositeKey(key, p.getPeriod());
            if (!taken.add(ck)) continue;
            taken.remove(compositeKey(p.getNormalizedKey(), p.getPeriod()));
            p.setNormalizedKey(key);
            patternRepository.save(p);
        }
    }

    private Map<String, MerchantGroup> groupByNormalizedMerchant(List<Transaction> txns) {
        Map<String, MerchantGroup> groups = new LinkedHashMap<>();
        for (Transaction tx : txns) {
            String rawMerchant = tx.getMerchant();
            if (rawMerchant == null || rawMerchant.isBlank()) continue;

            Normalized norm = merchantNormalizer.normalize(rawMerchant);
            if (norm.isEmpty() || norm.getKey().length() < MIN_KEY_LENGTH) continue;

            groups.computeIfAbsent(norm.getKey(), k -> new MerchantGroup(k, norm))
                  .add(tx);
        }
        return groups;
    }

    private RecurringPattern analyze(String key, MerchantGroup group, User user) {
        List<Transaction> txns = group.txns;
        if (txns.size() < MIN_OCCURRENCES) return null;

        txns.sort(Comparator.comparing(Transaction::getTransactionDate));

        List<Integer> intervals = new ArrayList<>();
        for (int i = 1; i < txns.size(); i++) {
            int days = (int) ChronoUnit.DAYS.between(
                txns.get(i - 1).getTransactionDate(),
                txns.get(i).getTransactionDate()
            );
            if (days > 0) intervals.add(days);
        }
        if (intervals.isEmpty()) return null;

        // classify by median interval
        List<Integer> sorted = new ArrayList<>(intervals);
        Collections.sort(sorted);
        int median = sorted.get(sorted.size() / 2);
        RecurringPeriod period = RecurringPeriod.fromDays(median);
        if (period == null) return null;

        // consistency: full within range, half within 2× max (tolerates one missed cycle)
        double consistencyScore = intervals.stream()
            .mapToDouble(d -> {
                if (d >= period.getMinDays() && d <= period.getMaxDays()) return 1.0;
                if (d <= period.getMaxDays() * 2)                          return 0.5;
                return 0.0;
            })
            .average().orElse(0);

        // amount stability
        DoubleSummaryStatistics stats = txns.stream()
            .mapToDouble(tx -> tx.getAmount().doubleValue())
            .summaryStatistics();
        double amountScore = stats.getAverage() > 0
            ? Math.max(0.0, 1.0 - (stats.getMax() - stats.getMin()) / stats.getAverage())
            : 0.0;

        // occurrence count, saturates at 6
        double occurrenceScore = Math.min(txns.size(), 6) / 6.0;

        // category match
        TransactionCategory dominantCategory = mostCommonCategory(txns);
        double categoryScore = dominantCategory != null && RECURRING_CATEGORIES.contains(dominantCategory)
            ? 1.0 : 0.5;

        double confidence =
              occurrenceScore  * W_OCCURRENCE
            + consistencyScore * W_CONSISTENCY
            + amountScore      * W_AMOUNT
            + categoryScore    * W_CATEGORY;

        if (confidence < CONFIDENCE_FLOOR) return null;

        String merchantDisplay = displayName(group);

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

    private RecurringPatternResponse toResponse(RecurringPattern p) {
        LocalDate today = LocalDate.now();
        int daysUntilNext = p.getNextExpectedDate() != null
            ? (int) ChronoUnit.DAYS.between(today, p.getNextExpectedDate())
            : 0;

        // a scored pattern is late only once its next-date interval has passed
        int lateBy = p.getNextExpectedHigh() != null
            ? (int) ChronoUnit.DAYS.between(p.getNextExpectedHigh(), today)
            : -daysUntilNext;

        String status;
        if (p.getNextExpectedDate() == null) {
            status = "ACTIVE";
        } else if (lateBy > p.getPeriod().getNominalDays()) {
            status = "MISSED";
        } else if (lateBy > 0) {
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
            .scoredBy(p.getModelVersion() != null ? "MODEL" : "RULES")
            .probability(p.getProbability())
            .nextExpectedLow(p.getNextExpectedLow())
            .nextExpectedHigh(p.getNextExpectedHigh())
            .modelVersion(p.getModelVersion())
            .chargesPerPeriod(p.getChargesPerPeriod())
            .build();
    }

    // canonical name if known, else the most-common raw name
    private String displayName(MerchantGroup group) {
        return group.normalized.getCanonicalName() != null
            ? group.normalized.getCanonicalName()
            : mostCommonMerchantName(group.txns);
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

    private static class MerchantGroup {
        final String key;
        final Normalized normalized;
        final List<Transaction> txns = new ArrayList<>();

        MerchantGroup(String key, Normalized normalized) {
            this.key = key;
            this.normalized = normalized;
        }

        void add(Transaction tx) {
            txns.add(tx);
        }
    }
}
