package com.flowsight.merchant;

import com.flowsight.analytics.MerchantNormalizationService;
import com.flowsight.entity.TransactionCategory;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MerchantResolverTest {

    private final MerchantNormalizationService normalizer = new MerchantNormalizationService();
    private final UUID userId = UUID.randomUUID();
    private final FakeUsers users = new FakeUsers();
    private final MerchantEmbeddingMatcher embeddings = mock(MerchantEmbeddingMatcher.class);
    private final MerchantLlmNamer llm = mock(MerchantLlmNamer.class);

    private MerchantResolver resolver() {
        when(embeddings.match(any(), anyString())).thenReturn(Optional.empty());
        when(llm.name(any(), anyString(), anyString())).thenReturn(Optional.empty());
        return new MerchantResolver(normalizer, AliasCatalog.inMemory(MerchantNormalizationService.builtInIndex()),
            users, embeddings, llm);
    }

    @Test
    void userRuleWinsOverAlias() {
        users.rule(normalizer.normalizeKey("STARBUCKS #12"), "Office Coffee", TransactionCategory.FOOD_DINING);

        MerchantResolution r = resolver().resolve(userId, "STARBUCKS #99 SEATTLE WA");

        assertThat(r.step()).isEqualTo(ResolutionStep.USER_RULE);
        assertThat(r.name()).isEqualTo("Office Coffee");
        assertThat(r.category()).isEqualTo(TransactionCategory.FOOD_DINING);
    }

    @Test
    void personalizationOffSkipsUserRules() {
        users.rule(normalizer.normalizeKey("STARBUCKS"), "Office Coffee", null);
        users.personalized = false;

        MerchantResolution r = resolver().resolve(userId, "STARBUCKS #99");

        assertThat(r.step()).isEqualTo(ResolutionStep.ALIAS);
        assertThat(r.name()).isEqualTo("Starbucks");
        assertThat(users.similarCalls).isZero();
    }

    @Test
    void categoryOnlyRuleCarriesItsCategoryToTheAliasName() {
        users.rule(normalizer.normalizeKey("NETFLIX"), null, TransactionCategory.ENTERTAINMENT);

        MerchantResolution r = resolver().resolve(userId, "NETFLIX.COM");

        assertThat(r.step()).isEqualTo(ResolutionStep.ALIAS);
        assertThat(r.name()).isEqualTo("Netflix");
        assertThat(r.category()).isEqualTo(TransactionCategory.ENTERTAINMENT);
    }

    @Test
    void aliasBeatsFuzzyAndLaterSteps() {
        MerchantResolution r = resolver().resolve(userId, "POS 532122XXXXXX3307 DOMINOS PIZZA HYDERABAD");

        assertThat(r.step()).isEqualTo(ResolutionStep.ALIAS);
        assertThat(r.name()).isEqualTo("Domino's");
        assertThat(r.key()).isEqualTo("dominos");
        verify(embeddings, never()).match(any(), anyString());
    }

    @Test
    void typoFallsToFuzzyMatch() {
        MerchantResolution r = resolver().resolve(userId, "POS DEBIT STARBUKS 0042 TX");

        assertThat(r.step()).isEqualTo(ResolutionStep.FUZZY);
        assertThat(r.name()).isEqualTo("Starbucks");
        assertThat(r.key()).isEqualTo("starbucks");
    }

    @Test
    void fuzzyDoesNotStretchToDifferentMerchants() {
        for (String raw : new String[] {"AMERICAN EAGLE OUTFITTERS", "DUBAI DUTY FREE", "APOLLO CHEMIST", "UNITED WAY"}) {
            assertThat(resolver().resolve(userId, raw).step()).as(raw).isNotEqualTo(ResolutionStep.FUZZY);
        }
    }

    @Test
    void userConfirmedNamesAreFuzzyCandidates() {
        users.names.add("Ravi Restaurant");

        MerchantResolution r = resolver().resolve(userId, "DEBIT CARD PURCHASE RAVI RESTAURNT SHARJAH");

        assertThat(r.step()).isEqualTo(ResolutionStep.FUZZY);
        assertThat(r.name()).isEqualTo("Ravi Restaurant");
    }

    @Test
    void embeddingThenLlmThenCleanedText() {
        MerchantResolver resolver = resolver();
        when(embeddings.match(any(), anyString()))
            .thenReturn(Optional.of(new MerchantEmbeddingMatcher.Match("Kibsons", 0.9)))
            .thenReturn(Optional.empty());
        when(llm.name(any(), anyString(), anyString())).thenReturn(Optional.of("Moonlight Grill"));

        MerchantResolution first = resolver.resolve(userId, "POS PURCHASE KBSNS FRESH 4411");
        MerchantResolution second = resolver.resolve(userId, "POS PURCHASE MOONLIGHT GRILL 4411");

        assertThat(first.step()).isEqualTo(ResolutionStep.EMBEDDING);
        assertThat(first.name()).isEqualTo("Kibsons");
        assertThat(second.step()).isEqualTo(ResolutionStep.LLM);
        assertThat(second.name()).isEqualTo("Moonlight Grill");
    }

    @Test
    void withMlAndLlmOffTheCleanedDescriptorIsUsed() {
        MerchantResolver offline = MerchantResolver.offline(normalizer);

        MerchantResolution r = offline.resolve(userId, "DEBIT CARD PURCHASE MOONLIGHT GRILL SHARJAH");

        assertThat(r.step()).isEqualTo(ResolutionStep.CLEANED);
        assertThat(r.name()).isEqualTo("Moonlight Grill");
        assertThat(r.key()).isEqualTo(normalizer.normalizeKey("DEBIT CARD PURCHASE MOONLIGHT GRILL SHARJAH"));
    }

    @Test
    void csvSessionsNeverAskTheLlm() {
        MerchantResolver resolver = resolver();

        resolver.session(userId, false).resolve("POS PURCHASE MOONLIGHT GRILL 4411");

        verify(llm, never()).name(any(), anyString(), anyString());
    }

    @Test
    void typedNamesAreKeptUnlessAnAliasOrRuleNamesThem() {
        MerchantResolver resolver = resolver();

        assertThat(resolver.resolveTyped(userId, "Joe's Corner").name()).isEqualTo("Joe's Corner");
        assertThat(resolver.resolveTyped(userId, "Joe's Corner").step()).isEqualTo(ResolutionStep.TYPED);
        assertThat(resolver.resolveTyped(userId, "starbucks").name()).isEqualTo("Starbucks");
        assertThat(resolver.resolveTyped(userId, "starbuks").step()).isEqualTo(ResolutionStep.TYPED);
    }

    @Test
    void blankInputResolvesToNone() {
        assertThat(resolver().resolve(userId, "  ")).isEqualTo(MerchantResolution.NONE);
        assertThat(resolver().resolve(userId, "123456789").step()).isEqualTo(ResolutionStep.NONE);
    }

    private static final class FakeUsers implements UserMerchantStore {
        boolean personalized = true;
        final Map<String, UserMerchantRule> rules = new HashMap<>();
        final List<String> names = new ArrayList<>();
        int similarCalls;

        void rule(String key, String name, TransactionCategory category) {
            rules.put(key, UserMerchantRule.builder().matchKey(key).canonicalName(name).category(category).build());
        }

        @Override
        public boolean personalized(UUID userId) {
            return personalized;
        }

        @Override
        public Map<String, UserMerchantRule> rules(UUID userId) {
            return rules;
        }

        @Override
        public List<AliasCatalog.Similar> similarNames(UUID userId, List<String> windows, double minSimilarity) {
            similarCalls++;
            List<AliasCatalog.Similar> out = new ArrayList<>();
            for (String name : names) {
                for (String w : windows) {
                    double sim = Trigram.similarity(name, w);
                    if (sim >= minSimilarity) out.add(new AliasCatalog.Similar(name, name, null, w, sim));
                }
            }
            return out;
        }
    }
}
