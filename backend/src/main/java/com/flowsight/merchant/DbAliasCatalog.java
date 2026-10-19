package com.flowsight.merchant;

import com.flowsight.analytics.MerchantNormalizationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// merchant_aliases, cached in memory for exact matching; trigram neighbours come from pg_trgm.
// Falls back to the built-in aliases if the table cannot be read.
@Component
@Slf4j
public class DbAliasCatalog implements AliasCatalog {

    private static final long CACHE_MILLIS = 10 * 60 * 1000L;

    private final JdbcTemplate jdbc;
    private volatile AliasIndex cached;
    private volatile long loadedAt;

    public DbAliasCatalog(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public AliasIndex index() {
        long now = System.currentTimeMillis();
        AliasIndex current = cached;
        if (current != null && now - loadedAt < CACHE_MILLIS) return current;
        try {
            List<AliasIndex.Entry> rows = jdbc.query(
                "SELECT alias, canonical_name, canonical_key, region, priority FROM merchant_aliases ORDER BY priority, id",
                (rs, i) -> new AliasIndex.Entry(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getInt(5)));
            current = rows.isEmpty() ? MerchantNormalizationService.builtInIndex() : new AliasIndex(rows);
        } catch (DataAccessException e) {
            log.warn("Could not load merchant aliases, using built-in aliases: {}", e.getClass().getSimpleName());
            if (current == null) current = MerchantNormalizationService.builtInIndex();
        }
        cached = current;
        loadedAt = now;
        return current;
    }

    @Override
    public List<Similar> similar(List<String> windows, double minSimilarity) {
        if (windows.isEmpty()) return List.of();
        String values = String.join(", ", Collections.nCopies(windows.size(), "(CAST(? AS text))"));
        String sql = "SELECT a.alias, a.canonical_name, a.canonical_key, w.q, similarity(a.alias, w.q) AS sim "
            + "FROM (VALUES " + values + ") AS w(q) "
            + "JOIN merchant_aliases a ON a.alias % w.q "
            + "WHERE length(a.alias) >= ? AND similarity(a.alias, w.q) >= ? "
            + "ORDER BY sim DESC, a.priority LIMIT " + MAX_SIMILAR;
        List<Object> args = new ArrayList<>(windows);
        args.add(MIN_FUZZY_LENGTH);
        args.add(minSimilarity);
        try {
            return jdbc.query(sql, (rs, i) -> new Similar(
                rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getDouble(5)), args.toArray());
        } catch (DataAccessException e) {
            log.warn("Trigram alias lookup failed, scanning in memory: {}", e.getClass().getSimpleName());
            return AliasCatalog.scan(index(), windows, minSimilarity);
        }
    }
}
