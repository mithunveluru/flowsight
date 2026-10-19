package com.flowsight.merchant;

import com.flowsight.analytics.MerchantNormalizationService;
import com.flowsight.entity.TransactionCategory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * The one merchant resolver for every entry path (CSV import, manual entry, receipts).
 * Cascade, stopping at the first confident answer: the user's own rules, exact alias
 * (whole tokens), trigram + edit-distance match against aliases and the user's confirmed
 * merchants, embedding neighbour (ml-service, flagged), LLM naming (flagged), and finally
 * the cleaned descriptor itself.
 */
@Service
public class MerchantResolver {

    static final double TRIGRAM_MIN = 0.45;
    static final double EDIT_MIN    = 0.80;
    private static final int MAX_WINDOW_TOKENS = 3;

    private final MerchantNormalizationService normalizer;
    private final AliasCatalog                 aliases;
    private final UserMerchantStore            users;
    private final MerchantEmbeddingMatcher     embeddings;
    private final MerchantLlmNamer             llm;

    @Autowired
    public MerchantResolver(
        MerchantNormalizationService normalizer, AliasCatalog aliases, UserMerchantStore users,
        MerchantEmbeddingMatcher embeddings, MerchantLlmNamer llm
    ) {
        this.normalizer = normalizer;
        this.aliases    = aliases;
        this.users      = users;
        this.embeddings = embeddings;
        this.llm        = llm;
    }

    /** Built-in aliases only: no database, no user memory, no ML or LLM. */
    public static MerchantResolver offline(MerchantNormalizationService normalizer) {
        return new MerchantResolver(normalizer, AliasCatalog.inMemory(MerchantNormalizationService.builtInIndex()),
            UserMerchantStore.NONE, MerchantEmbeddingMatcher.disabled(), MerchantLlmNamer.disabled());
    }

    /**
     * Resolves merchant text that came from a document: a statement descriptor or the
     * merchant line a receipt OCR produced. userId may be null (no user rules).
     */
    public MerchantResolution resolve(UUID userId, String rawText) {
        return session(userId, true).resolve(rawText);
    }

    /** Resolves a merchant name the user typed: only their rules and exact aliases may rename it. */
    public MerchantResolution resolveTyped(UUID userId, String typedName) {
        return session(userId, false).resolveTyped(typedName);
    }

    /** For batches (CSV import): loads the user's rules once and caches repeated descriptors. */
    public Session session(UUID userId, boolean allowLlm) {
        return new Session(userId, allowLlm);
    }

    public final class Session {

        private final UUID    userId;
        private final boolean allowLlm;
        private final boolean personalized;
        private final Map<String, UserMerchantRule>   rules;
        private final Map<String, MerchantResolution> cache = new HashMap<>();

        private Session(UUID userId, boolean allowLlm) {
            this.userId       = userId;
            this.allowLlm     = allowLlm;
            this.personalized = userId != null && users.personalized(userId);
            this.rules        = personalized ? users.rules(userId) : Map.of();
        }

        public MerchantResolution resolve(String rawText) {
            if (rawText == null || rawText.isBlank()) return MerchantResolution.NONE;
            String raw = rawText.trim();
            MerchantResolution known = cache.get(raw);
            if (known == null) {
                known = cascade(raw);
                cache.put(raw, known);
            }
            return known;
        }

        public MerchantResolution resolveTyped(String typedName) {
            if (typedName == null || typedName.isBlank()) return MerchantResolution.NONE;
            String typed = typedName.trim();
            UserMerchantRule rule = ruleFor(typed);
            TransactionCategory category = rule == null ? null : rule.getCategory();
            if (rule != null && rule.getCanonicalName() != null) {
                return named(rule.getCanonicalName(), ResolutionStep.USER_RULE, 0.99, category);
            }
            AliasIndex.Entry alias = exactAlias(typed);
            if (alias != null) return fromAlias(alias, category);
            return new MerchantResolution(typed, normalizer.normalizeKey(typed), ResolutionStep.TYPED, 1.0, category);
        }

        private MerchantResolution cascade(String raw) {
            UserMerchantRule rule = ruleFor(raw);
            TransactionCategory category = rule == null ? null : rule.getCategory();
            if (rule != null && rule.getCanonicalName() != null) {
                return named(rule.getCanonicalName(), ResolutionStep.USER_RULE, 0.99, category);
            }

            AliasIndex.Entry alias = exactAlias(raw);
            if (alias != null) return fromAlias(alias, category);

            String text = normalizer.matchText(raw);
            String cleaned = MerchantText.clean(raw);
            if (text.isEmpty() || cleaned.isEmpty()) return MerchantResolution.NONE;

            MerchantResolution fuzzy = fuzzy(text, category);
            if (fuzzy != null) return fuzzy;

            Optional<MerchantEmbeddingMatcher.Match> neighbour = embeddings.match(userId, cleaned);
            if (neighbour.isPresent()) {
                return named(neighbour.get().canonicalName(), ResolutionStep.EMBEDDING,
                    round(0.9 * neighbour.get().similarity()), category);
            }

            if (allowLlm) {
                Optional<String> named = llm.name(userId, raw, cleaned);
                if (named.isPresent()) return named(named.get(), ResolutionStep.LLM, 0.6, category);
            }

            return named(cleaned, ResolutionStep.CLEANED, 0.4, category);
        }

        private UserMerchantRule ruleFor(String raw) {
            if (rules.isEmpty()) return null;
            String key = normalizer.normalizeKey(raw);
            return key.isEmpty() ? null : rules.get(key);
        }

        private AliasIndex.Entry exactAlias(String raw) {
            AliasIndex index = aliases.index();
            AliasIndex.Entry stored = index.canonical(raw);
            return stored != null ? stored : index.match(normalizer.matchText(raw));
        }

        private MerchantResolution fuzzy(String text, TransactionCategory category) {
            List<String> windows = windows(text);
            if (windows.isEmpty()) return null;
            List<AliasCatalog.Similar> candidates = new ArrayList<>(aliases.similar(windows, TRIGRAM_MIN));
            if (personalized) candidates.addAll(users.similarNames(userId, windows, TRIGRAM_MIN));

            AliasCatalog.Similar best = null;
            double bestEdit = 0;
            for (AliasCatalog.Similar c : candidates) {
                double edit = Trigram.editSimilarity(MerchantText.matchText(c.text()), c.window());
                if (edit >= EDIT_MIN && (best == null || edit + c.similarity() > bestEdit + best.similarity())) {
                    best = c;
                    bestEdit = edit;
                }
            }
            if (best == null) return null;
            String key = best.canonicalKey() != null ? best.canonicalKey() : normalizer.normalizeKey(best.canonicalName());
            return new MerchantResolution(best.canonicalName(), key, ResolutionStep.FUZZY, round(0.9 * bestEdit), category);
        }

        private MerchantResolution fromAlias(AliasIndex.Entry alias, TransactionCategory category) {
            return new MerchantResolution(alias.canonicalName(), alias.canonicalKey(), ResolutionStep.ALIAS, 0.95, category);
        }

        private MerchantResolution named(String name, ResolutionStep step, double confidence, TransactionCategory category) {
            return new MerchantResolution(name, normalizer.normalizeKey(name), step, confidence, category);
        }
    }

    // Contiguous runs of up to three tokens plus the whole text, so a brand inside a longer
    // descriptor can be compared on its own.
    static List<String> windows(String text) {
        String[] tokens = text.split(" ");
        Set<String> out = new LinkedHashSet<>();
        if (text.length() >= AliasCatalog.MIN_FUZZY_LENGTH) out.add(text);
        for (int size = 1; size <= MAX_WINDOW_TOKENS; size++) {
            for (int i = 0; i + size <= tokens.length; i++) {
                String w = String.join(" ", Arrays.copyOfRange(tokens, i, i + size));
                if (w.length() >= AliasCatalog.MIN_FUZZY_LENGTH) out.add(w);
            }
        }
        return List.copyOf(out);
    }

    private static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
