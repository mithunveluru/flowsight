package com.flowsight.merchant;

import com.flowsight.analytics.MerchantNormalizationService;
import com.flowsight.entity.Role;
import com.flowsight.entity.User;
import com.flowsight.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

// Runs V16 against Postgres: the alias seed, pg_trgm parity with the in-memory matcher, and
// trigram matching through the resolver bean.
@SpringBootTest
@ActiveProfiles("test")
class MerchantStorageIntegrationTest {

    @Autowired private JdbcTemplate               jdbc;
    @Autowired private DbAliasCatalog             catalog;
    @Autowired private MerchantResolver           resolver;
    @Autowired private UserMerchantRuleRepository rules;
    @Autowired private UserRepository             users;

    private User user;
    private User other;

    @BeforeEach
    void setUp() {
        user  = users.save(newUser());
        other = users.save(newUser());
    }

    @AfterEach
    void tearDown() {
        users.deleteAllById(List.of(user.getId(), other.getId()));
    }

    @Test
    void seedEqualsTheBuiltInAliases() {
        List<AliasIndex.Entry> stored = jdbc.query(
            "SELECT alias, canonical_name, canonical_key, region, priority FROM merchant_aliases ORDER BY priority",
            (rs, i) -> new AliasIndex.Entry(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getInt(5)));

        assertThat(stored).containsExactlyElementsOf(MerchantNormalizationService.builtInAliases());
    }

    @Test
    void inMemoryTrigramSimilarityMatchesPgTrgm() {
        String[][] pairs = {
            {"starbucks", "starbuks"}, {"netflix", "netflx"}, {"dubai taxi", "dubai"}, {"apollo pharmacy", "apollo chemist"},
            {"ola", "olaplex"}, {"Ravi Restaurant", "ravi restaurnt"}, {"lulu hypermarket", "LULU HYPER MKT"},
            {"al baik", "albaik"}, {"7 eleven", "7-Eleven #33"}, {"x", "y"}, {"", "abc"}};
        for (String[] p : pairs) {
            Double pg = jdbc.queryForObject("SELECT similarity(?, ?)::float8", Double.class, p[0], p[1]);
            assertThat(Trigram.similarity(p[0], p[1])).as(p[0] + " / " + p[1]).isCloseTo(pg, within(1e-6));
        }
    }

    @Test
    void catalogFindsTrigramNeighboursInPostgres() {
        List<AliasCatalog.Similar> found = catalog.similar(List.of("starbuks"), MerchantResolver.TRIGRAM_MIN);

        assertThat(found).isNotEmpty();
        assertThat(found.get(0).canonicalName()).isEqualTo("Starbucks");
        assertThat(found).allSatisfy(s -> assertThat(s.text().length()).isGreaterThanOrEqualTo(AliasCatalog.MIN_FUZZY_LENGTH));
    }

    @Test
    void resolverUsesPgTrgmForAliasesAndTheUsersConfirmedNames() {
        rules.save(UserMerchantRule.builder().userId(user.getId()).matchKey("ravirestaurant")
            .canonicalName("Ravi Restaurant").build());

        MerchantResolution typo = resolver.resolve(user.getId(), "POS DEBIT STARBUKS 0042 TX");
        MerchantResolution mine = resolver.resolve(user.getId(), "DEBIT CARD PURCHASE RAVI RESTAURNT SHARJAH");
        MerchantResolution theirs = resolver.resolve(other.getId(), "DEBIT CARD PURCHASE RAVI RESTAURNT SHARJAH");

        assertThat(typo.step()).isEqualTo(ResolutionStep.FUZZY);
        assertThat(typo.name()).isEqualTo("Starbucks");
        assertThat(mine.step()).isEqualTo(ResolutionStep.FUZZY);
        assertThat(mine.name()).isEqualTo("Ravi Restaurant");
        assertThat(theirs.step()).isEqualTo(ResolutionStep.CLEANED);
        assertThat(theirs.name()).isEqualTo("Ravi Restaurnt");
    }

    @Test
    void rulesAreDeletedWithTheirUser() {
        rules.save(UserMerchantRule.builder().userId(other.getId()).matchKey("swiggy").canonicalName("Food").build());

        users.deleteById(other.getId());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_merchant_rules WHERE user_id = ?",
            Integer.class, other.getId())).isZero();
        other = users.save(newUser());
    }

    private static User newUser() {
        return User.builder()
            .email("merchant-" + UUID.randomUUID() + "@example.com")
            .passwordHash("x")
            .role(Role.USER)
            .build();
    }
}
