package com.flowsight.merchant;

import com.flowsight.ml.PersonalizationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@RequiredArgsConstructor
@Slf4j
public class DbUserMerchantStore implements UserMerchantStore {

    private final PersonalizationService     personalization;
    private final UserMerchantRuleRepository rules;
    private final JdbcTemplate               jdbc;

    @Override
    public boolean personalized(UUID userId) {
        return personalization.isEnabled(userId);
    }

    @Override
    public Map<String, UserMerchantRule> rules(UUID userId) {
        Map<String, UserMerchantRule> byKey = new HashMap<>();
        for (UserMerchantRule rule : rules.findByUserIdOrderByUpdatedAtDesc(userId)) {
            byKey.putIfAbsent(rule.getMatchKey(), rule);
        }
        return byKey;
    }

    @Override
    public List<AliasCatalog.Similar> similarNames(UUID userId, List<String> windows, double minSimilarity) {
        if (windows.isEmpty()) return List.of();
        String values = String.join(", ", Collections.nCopies(windows.size(), "(CAST(? AS text))"));
        String sql = "SELECT r.canonical_name, w.q, similarity(r.canonical_name, w.q) AS sim "
            + "FROM (VALUES " + values + ") AS w(q) "
            + "JOIN user_merchant_rules r ON r.user_id = ? AND r.canonical_name % w.q "
            + "WHERE similarity(r.canonical_name, w.q) >= ? "
            + "ORDER BY sim DESC LIMIT " + AliasCatalog.MAX_SIMILAR;
        List<Object> args = new ArrayList<>(windows);
        args.add(userId);
        args.add(minSimilarity);
        try {
            return jdbc.query(sql, (rs, i) -> new AliasCatalog.Similar(
                rs.getString(1), rs.getString(1), null, rs.getString(2), rs.getDouble(3)), args.toArray());
        } catch (DataAccessException e) {
            log.warn("Trigram lookup of confirmed merchants failed: {}", e.getClass().getSimpleName());
            return List.of();
        }
    }
}
