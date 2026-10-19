package com.flowsight.merchant;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** A user's own merchant memory: their rules and the merchant names they have confirmed. */
public interface UserMerchantStore {

    boolean personalized(UUID userId);

    /** The user's rules keyed by match key. */
    Map<String, UserMerchantRule> rules(UUID userId);

    /** The user's confirmed merchant names trigram-similar to any window, best first. */
    List<AliasCatalog.Similar> similarNames(UUID userId, List<String> windows, double minSimilarity);

    UserMerchantStore NONE = new UserMerchantStore() {
        @Override
        public boolean personalized(UUID userId) {
            return false;
        }

        @Override
        public Map<String, UserMerchantRule> rules(UUID userId) {
            return Map.of();
        }

        @Override
        public List<AliasCatalog.Similar> similarNames(UUID userId, List<String> windows, double minSimilarity) {
            return List.of();
        }
    };
}
