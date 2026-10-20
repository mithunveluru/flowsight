package com.flowsight.insights;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class RecommendationStatsRepository {

    public record Counts(long accepted, long dismissed) {
        public long total() {
            return accepted + dismissed;
        }
    }

    private static final String ADJUST = """
        INSERT INTO recommendation_kind_stats (kind, accepted, dismissed)
        VALUES (?, GREATEST(0, ?), GREATEST(0, ?))
        ON CONFLICT (kind) DO UPDATE SET
            accepted   = GREATEST(0, recommendation_kind_stats.accepted + ?),
            dismissed  = GREATEST(0, recommendation_kind_stats.dismissed + ?),
            updated_at = NOW()
        """;

    private final JdbcTemplate jdbc;

    public Map<String, Counts> all() {
        Map<String, Counts> out = new HashMap<>();
        jdbc.query("SELECT kind, accepted, dismissed FROM recommendation_kind_stats",
            rs -> { out.put(rs.getString(1), new Counts(rs.getLong(2), rs.getLong(3))); });
        return out;
    }

    /** Atomic; deltas may be negative (an accepted item later dismissed), counts never drop below 0. */
    public void adjust(String kind, int acceptedDelta, int dismissedDelta) {
        jdbc.update(ADJUST, kind, acceptedDelta, dismissedDelta, acceptedDelta, dismissedDelta);
    }
}
