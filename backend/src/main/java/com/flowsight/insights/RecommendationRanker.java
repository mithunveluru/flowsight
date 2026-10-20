package com.flowsight.insights;

import com.flowsight.dto.insights.Recommendation;
import com.flowsight.ml.PersonalizationService;
import com.flowsight.ml.SubjectType;
import com.flowsight.ml.UserFeedback;
import com.flowsight.ml.UserFeedbackRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Orders recommendations by expected value: the chance the user acts on that kind of advice
 * times a saving weight 1 + ln(1 + saving / 100). The chance is a Beta-Bernoulli posterior
 * per kind: a prior from everyone's accept/dismiss counts (worth at most 20 observations),
 * updated with the user's own latest answer per recommendation. Users who turned
 * personalization off are ranked on the global prior only. A dismissed recommendation stays
 * hidden for 30 days.
 */
@Service
@RequiredArgsConstructor
public class RecommendationRanker {

    public static final String FIELD = "action";
    public static final String MODEL_VERSION = "beta-pref-v1";
    public static final String ACCEPTED = "ACCEPTED";
    public static final String DISMISSED = "DISMISSED";

    static final Duration FEEDBACK_TTL = Duration.ofDays(30);
    private static final double PRIOR_CAP = 20;
    private static final int HISTORY = 500;

    private final UserFeedbackRepository        feedbackRepository;
    private final PersonalizationService        personalization;
    private final RecommendationStatsRepository stats;
    private final Clock clock = Clock.systemUTC();

    record Preference(double mean, long userAccepted, long userTotal, long globalTotal) {
    }

    public List<Recommendation> rank(UUID userId, List<Recommendation> candidates, int limit) {
        Map<String, UserFeedback> latest = latestByRecommendation(userId);
        Map<String, long[]> userCounts = personalization.isEnabled(userId) ? countByKind(latest) : Map.of();
        Map<String, RecommendationStatsRepository.Counts> global = stats.all();
        Instant cutoff = clock.instant().minus(FEEDBACK_TTL);

        List<Recommendation> visible = new ArrayList<>();
        for (Recommendation r : candidates) {
            UserFeedback last = latest.get(r.getId());
            boolean recent = last != null && last.getCreatedAt() != null && last.getCreatedAt().isAfter(cutoff);
            if (recent && DISMISSED.equals(last.getCorrectedValue())) continue;
            Preference p = preference(r.getKind(), userCounts, global);
            r.setFeedback(recent ? last.getCorrectedValue() : null);
            r.setAcceptanceRate(round(p.mean()));
            r.setRankScore(round(p.mean() * savingWeight(r.getPotentialMonthlySaving())));
            r.setRankingReason(reason(p));
            visible.add(r);
        }
        // stable: equal scores keep the engine's order
        visible.sort(Comparator.comparing(Recommendation::getRankScore).reversed());
        return visible.size() > limit ? new ArrayList<>(visible.subList(0, limit)) : visible;
    }

    public double acceptanceRate(UUID userId, String kind) {
        Map<String, long[]> userCounts = personalization.isEnabled(userId)
            ? countByKind(latestByRecommendation(userId)) : Map.of();
        return preference(kind, userCounts, stats.all()).mean();
    }

    /** The user's newest answer per recommendation id, newest first. */
    public Map<String, UserFeedback> latestByRecommendation(UUID userId) {
        Map<String, UserFeedback> latest = new LinkedHashMap<>();
        for (UserFeedback f : feedbackRepository.findByUserIdAndSubjectTypeAndFieldOrderByCreatedAtDesc(
                userId, SubjectType.RECOMMENDATION, FIELD, PageRequest.of(0, HISTORY))) {
            latest.putIfAbsent(f.getSubjectId().toString(), f);
        }
        return latest;
    }

    static Preference preference(String kind, Map<String, long[]> userCounts,
                                 Map<String, RecommendationStatsRepository.Counts> global) {
        RecommendationStatsRepository.Counts g = global.getOrDefault(kind, new RecommendationStatsRepository.Counts(0, 0));
        double globalRate = (g.accepted() + 1.0) / (g.total() + 2.0);
        double strength = Math.min(g.total(), PRIOR_CAP);
        double alpha = 1 + strength * globalRate;
        double beta = 1 + strength * (1 - globalRate);
        long[] u = userCounts.getOrDefault(kind, new long[2]);
        double mean = (alpha + u[0]) / (alpha + beta + u[0] + u[1]);
        return new Preference(mean, u[0], u[0] + u[1], g.total());
    }

    static double savingWeight(BigDecimal monthlySaving) {
        if (monthlySaving == null || monthlySaving.signum() <= 0) return 1;
        return 1 + Math.log1p(monthlySaving.doubleValue() / 100);
    }

    private static Map<String, long[]> countByKind(Map<String, UserFeedback> latest) {
        Map<String, long[]> counts = new HashMap<>();
        for (UserFeedback f : latest.values()) {
            if (f.getPredictedValue() == null) continue;
            long[] c = counts.computeIfAbsent(f.getPredictedValue(), k -> new long[2]);
            if (ACCEPTED.equals(f.getCorrectedValue())) c[0]++;
            else if (DISMISSED.equals(f.getCorrectedValue())) c[1]++;
        }
        return counts;
    }

    private static String reason(Preference p) {
        if (p.userTotal() > 0) {
            return String.format("You acted on %d of %d similar suggestions", p.userAccepted(), p.userTotal());
        }
        if (p.globalTotal() >= 5) return "Ranked by how people responded to similar suggestions";
        return "Ranked by estimated saving";
    }

    private static double round(double v) {
        return Math.round(v * 10_000) / 10_000.0;
    }
}
