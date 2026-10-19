package com.flowsight.ml.categorization;

import com.flowsight.entity.TransactionCategory;
import com.flowsight.merchant.Trigram;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The user's own reviewed transactions, searched by merchant and description similarity. */
public interface ReviewedHistory {

    int LIMIT = 10;
    // pg_trgm's default similarity_threshold, which the % operator applies
    double CANDIDATE_SIMILARITY = 0.3;

    /** For each item, in order: the user's most similar reviewed transactions, best first. */
    List<List<Neighbour>> nearest(UUID userId, List<CategorizerRequest.Item> items);

    record Reviewed(String merchant, String description, TransactionCategory category) {
    }

    /** Same scoring as the database store, over rows already in memory (tests and the eval). */
    static ReviewedHistory inMemory(Map<UUID, List<Reviewed>> byUser) {
        return (userId, items) -> {
            List<Reviewed> rows = byUser.getOrDefault(userId, List.of());
            List<List<Neighbour>> out = new ArrayList<>();
            for (CategorizerRequest.Item item : items) {
                List<Neighbour> found = new ArrayList<>();
                for (Reviewed r : rows) {
                    double sim = similarity(r, item);
                    if (sim >= CANDIDATE_SIMILARITY) found.add(new Neighbour(r.category(), sim));
                }
                found.sort(Comparator.comparingDouble(Neighbour::similarity).reversed());
                out.add(List.copyOf(found.subList(0, Math.min(LIMIT, found.size()))));
            }
            return out;
        };
    }

    /**
     * Merchant similarity when both sides name a merchant; description similarity only when one
     * does not. Statement boilerplate (POS PURCHASE ... DUBAI AE) makes unrelated descriptions
     * look alike, so descriptions are a fallback, not a peer.
     */
    static double similarity(Reviewed r, CategorizerRequest.Item item) {
        String a = nullToEmpty(r.merchant());
        String b = nullToEmpty(item.merchant());
        if (!a.isEmpty() && !b.isEmpty()) return Trigram.similarity(a, b);
        return Trigram.similarity(nullToEmpty(r.description()), nullToEmpty(item.description()));
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
