package com.flowsight.ml.categorization;

import com.flowsight.entity.TransactionCategory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * pg_trgm neighbours among the user's reviewed transactions, scored as {@link ReviewedHistory}
 * describes. Sample rows and uncategorized rows are never neighbours. Any database error
 * yields no neighbours (global model only).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DbReviewedHistory implements ReviewedHistory {

    private static final int CHUNK = 50;

    private final JdbcTemplate jdbc;

    @Override
    public List<List<Neighbour>> nearest(UUID userId, List<CategorizerRequest.Item> items) {
        List<List<Neighbour>> out = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) out.add(new ArrayList<>());
        try {
            for (int start = 0; start < items.size(); start += CHUNK) {
                query(userId, items.subList(start, Math.min(items.size(), start + CHUNK)), start, out);
            }
        } catch (DataAccessException e) {
            log.warn("Reviewed-transaction lookup failed: {}", e.getClass().getSimpleName());
            return items.stream().map(i -> List.<Neighbour>of()).toList();
        }
        return out;
    }

    private void query(UUID userId, List<CategorizerRequest.Item> chunk, int offset, List<List<Neighbour>> out) {
        String values = String.join(", ",
            Collections.nCopies(chunk.size(), "(CAST(? AS int), CAST(? AS text), CAST(? AS text))"));
        String sql = "SELECT v.i, n.category, n.sim FROM (VALUES " + values + ") AS v(i, m, d) "
            + "CROSS JOIN LATERAL ("
            + "  SELECT t.category, CASE WHEN COALESCE(t.merchant, '') <> '' AND v.m <> ''"
            + "    THEN similarity(t.merchant, v.m) ELSE similarity(t.description, v.d) END AS sim"
            + "  FROM transactions t"
            + "  WHERE t.user_id = ? AND t.is_reviewed AND t.source <> 'SAMPLE'"
            + "    AND t.category IS NOT NULL AND t.category <> 'UNCATEGORIZED'"
            + "    AND (t.merchant % v.m OR t.description % v.d)"
            + "  ORDER BY sim DESC, t.updated_at DESC"
            + "  LIMIT " + LIMIT
            + ") n ORDER BY v.i, n.sim DESC";
        List<Object> args = new ArrayList<>();
        for (int i = 0; i < chunk.size(); i++) {
            args.add(offset + i);
            args.add(nullToEmpty(chunk.get(i).merchant()));
            args.add(nullToEmpty(chunk.get(i).description()));
        }
        args.add(userId);
        jdbc.query(sql, rs -> {
            TransactionCategory category = parse(rs.getString(2));
            if (category != null) out.get(rs.getInt(1)).add(new Neighbour(category, rs.getDouble(3)));
        }, args.toArray());
    }

    private static TransactionCategory parse(String name) {
        try {
            return TransactionCategory.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
