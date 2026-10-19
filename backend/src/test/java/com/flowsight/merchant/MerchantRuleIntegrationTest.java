package com.flowsight.merchant;

import com.flowsight.dto.merchant.ApplyPreviewResponse;
import com.flowsight.dto.merchant.ApplyResultResponse;
import com.flowsight.dto.transaction.CreateTransactionRequest;
import com.flowsight.dto.transaction.TransactionResponse;
import com.flowsight.dto.transaction.UpdateTransactionRequest;
import com.flowsight.entity.Role;
import com.flowsight.entity.TransactionCategory;
import com.flowsight.entity.TransactionType;
import com.flowsight.entity.User;
import com.flowsight.exception.FlowsightException;
import com.flowsight.exception.ResourceNotFoundException;
import com.flowsight.ml.PersonalizationService;
import com.flowsight.repository.TransactionRepository;
import com.flowsight.repository.UserRepository;
import com.flowsight.service.TransactionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Corrections become feedback and rules; preview/apply only ever touch the owner's rows.
@SpringBootTest
@ActiveProfiles("test")
class MerchantRuleIntegrationTest {

    @Autowired private TransactionService         transactions;
    @Autowired private TransactionRepository      transactionRepository;
    @Autowired private MerchantRuleService        ruleService;
    @Autowired private UserMerchantRuleRepository rules;
    @Autowired private PersonalizationService     personalization;
    @Autowired private UserRepository             users;
    @Autowired private JdbcTemplate               jdbc;

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
    void manualEntryStoresTheResolvedNameAndKeepsTheRawText() {
        TransactionResponse tx = create(user, "POS 532122XXXXXX3307 DOMINOS PIZZA PUNE", null);

        assertThat(tx.getMerchant()).isEqualTo("Domino's");
        assertThat(transactionRepository.findById(tx.getId()).orElseThrow().getMerchantRaw())
            .isEqualTo("POS 532122XXXXXX3307 DOMINOS PIZZA PUNE");
    }

    @Test
    void correctionRecordsFeedbackSavesARuleAndCountsSimilarRows() {
        TransactionResponse first = create(user, "POS 4111XXXXXX1111 MOONLIGHT GRILL DUBAI", null);
        create(user, "POS 4111XXXXXX2222 MOONLIGHT GRILL DUBAI", null);
        create(user, "POS 4111XXXXXX3333 MOONLIGHT GRILL DUBAI", null);

        TransactionResponse edited = transactions.update(first.getId(), user.getId(),
            edit("Moonlight Grill & Bar", TransactionCategory.FOOD_DINING));

        assertThat(edited.getMerchant()).isEqualTo("Moonlight Grill & Bar");
        assertThat(edited.getRuleSuggestion()).isNotNull();
        assertThat(edited.getRuleSuggestion().getSimilarCount()).isEqualTo(2);
        assertThat(jdbc.queryForList("SELECT field FROM user_feedback WHERE user_id = ? AND subject_id = ?",
            String.class, user.getId(), first.getId())).containsExactlyInAnyOrder("merchant", "category");
        assertThat(rules.findByUserIdOrderByUpdatedAtDesc(user.getId())).singleElement().satisfies(r -> {
            assertThat(r.getCanonicalName()).isEqualTo("Moonlight Grill & Bar");
            assertThat(r.getCategory()).isEqualTo(TransactionCategory.FOOD_DINING);
        });
    }

    @Test
    void applyToSimilarIsScopedToTheOwner() {
        TransactionResponse first = create(user, "POS 4111XXXXXX1111 MOONLIGHT GRILL DUBAI", null);
        create(user, "POS 4111XXXXXX2222 MOONLIGHT GRILL DUBAI", null);
        TransactionResponse foreign = create(other, "POS 4111XXXXXX9999 MOONLIGHT GRILL DUBAI", null);
        UUID ruleId = transactions.update(first.getId(), user.getId(),
            edit("Moonlight Grill & Bar", TransactionCategory.FOOD_DINING)).getRuleSuggestion().getRuleId();

        ApplyPreviewResponse preview = ruleService.preview(ruleId, user);
        ApplyResultResponse applied = ruleService.apply(ruleId, user);

        assertThat(preview.getMatchCount()).isEqualTo(1);
        assertThat(preview.getSample()).singleElement().satisfies(s -> assertThat(s.getMerchant()).isEqualTo("Moonlight Grill"));
        assertThat(applied.getUpdated()).isEqualTo(1);
        assertThat(transactionRepository.findAll().stream()
            .filter(t -> t.getUser().getId().equals(user.getId()))
            .map(t -> t.getMerchant())).containsOnly("Moonlight Grill & Bar");
        assertThat(transactions.getById(foreign.getId(), other.getId()).getMerchant()).isEqualTo("Moonlight Grill");
        assertThat(ruleService.preview(ruleId, user).getMatchCount()).isZero();
        assertThat(jdbc.queryForList("SELECT action FROM audit_logs WHERE user_id = ?", String.class, user.getId()))
            .contains(MerchantRuleService.ACTION_RULE_PREVIEWED, MerchantRuleService.ACTION_RULE_APPLIED);
    }

    @Test
    void anotherUsersRuleIsNotFound() {
        TransactionResponse first = create(user, "POS MOONLIGHT GRILL DUBAI", null);
        UUID ruleId = transactions.update(first.getId(), user.getId(), edit("Moonlight", null))
            .getRuleSuggestion().getRuleId();

        assertThatThrownBy(() -> ruleService.preview(ruleId, other)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> ruleService.apply(ruleId, other)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> ruleService.delete(ruleId, other)).isInstanceOf(ResourceNotFoundException.class);
        assertThat(ruleService.list(other.getId())).isEmpty();
        assertThat(rules.findById(ruleId)).isPresent();
    }

    @Test
    void laterImportsAndEntriesUseTheRule() throws Exception {
        TransactionResponse first = create(user, "POS 4111XXXXXX1111 MOONLIGHT GRILL DUBAI", null);
        transactions.update(first.getId(), user.getId(), edit("Moonlight Grill & Bar", TransactionCategory.FOOD_DINING));

        String csv = "date,description,amount,type\n2026-04-01,POS 4111XXXXXX4444 MOONLIGHT GRILL DUBAI,80,DEBIT\n";
        transactions.importCsv(new MockMultipartFile("file", "a.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)),
            user.getId());
        TransactionResponse manual = create(user, "POS 4111XXXXXX5555 MOONLIGHT GRILL DUBAI", null);
        TransactionResponse theirs = create(other, "POS 4111XXXXXX6666 MOONLIGHT GRILL DUBAI", null);

        assertThat(transactionRepository.findAll().stream()
            .filter(t -> t.getUser().getId().equals(user.getId()))
            .map(t -> t.getMerchant() + "/" + t.getCategory()))
            .containsOnly("Moonlight Grill & Bar/FOOD_DINING");
        assertThat(manual.isReviewed()).isFalse();
        assertThat(theirs.getMerchant()).isEqualTo("Moonlight Grill");
    }

    @Test
    void withPersonalizationOffEditsApplyButNoRuleIsLearned() {
        personalization.setEnabled(user.getId(), false);
        TransactionResponse first = create(user, "POS MOONLIGHT GRILL DUBAI", null);

        TransactionResponse edited = transactions.update(first.getId(), user.getId(),
            edit("Moonlight Grill & Bar", TransactionCategory.FOOD_DINING));

        assertThat(edited.getMerchant()).isEqualTo("Moonlight Grill & Bar");
        assertThat(edited.getCategory()).isEqualTo(TransactionCategory.FOOD_DINING);
        assertThat(edited.getRuleSuggestion()).isNull();
        assertThat(rules.findByUserIdOrderByUpdatedAtDesc(user.getId())).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_feedback WHERE user_id = ?", Integer.class,
            user.getId())).isEqualTo(2);
    }

    @Test
    void previewAndApplyShareARateLimit() {
        TransactionResponse first = create(user, "POS MOONLIGHT GRILL DUBAI", null);
        UUID ruleId = transactions.update(first.getId(), user.getId(), edit("Moonlight", null))
            .getRuleSuggestion().getRuleId();

        for (int i = 0; i < 30; i++) ruleService.preview(ruleId, user);

        assertThatThrownBy(() -> ruleService.apply(ruleId, user))
            .isInstanceOfSatisfying(FlowsightException.class,
                e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value()));
        // the bucket is per user
        assertThat(ruleService.preview(ruleIdFor(other), other).getMatchCount()).isZero();
    }

    private UUID ruleIdFor(User owner) {
        TransactionResponse tx = create(owner, "POS SUNRISE CAFE DUBAI", null);
        return transactions.update(tx.getId(), owner.getId(), edit("Sunrise", null)).getRuleSuggestion().getRuleId();
    }

    private TransactionResponse create(User owner, String description, String merchant) {
        CreateTransactionRequest r = new CreateTransactionRequest();
        r.setAmount(new BigDecimal("80.00"));
        r.setTransactionDate(LocalDate.now().minusDays(1));
        r.setDescription(description);
        r.setMerchant(merchant);
        r.setType(TransactionType.DEBIT);
        return transactions.create(r, owner.getId());
    }

    private static UpdateTransactionRequest edit(String merchant, TransactionCategory category) {
        UpdateTransactionRequest r = new UpdateTransactionRequest();
        r.setMerchant(merchant);
        r.setCategory(category);
        return r;
    }

    private static User newUser() {
        return User.builder()
            .email("rules-" + UUID.randomUUID() + "@example.com")
            .passwordHash("x")
            .role(Role.USER)
            .build();
    }
}
