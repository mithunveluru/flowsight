package com.flowsight.ml.categorization;

import com.flowsight.dto.merchant.ApplyPreviewResponse;
import com.flowsight.dto.transaction.CreateTransactionRequest;
import com.flowsight.dto.transaction.TransactionResponse;
import com.flowsight.dto.transaction.UpdateTransactionRequest;
import com.flowsight.entity.Role;
import com.flowsight.entity.TransactionCategory;
import com.flowsight.entity.TransactionType;
import com.flowsight.entity.User;
import com.flowsight.merchant.MerchantRuleService;
import com.flowsight.ml.MlCapability;
import com.flowsight.ml.MlClient;
import com.flowsight.ml.MlPrediction;
import com.flowsight.ml.PersonalizationService;
import com.flowsight.repository.UserRepository;
import com.flowsight.service.TransactionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

// One user's corrections change that user's later predictions, through the merchant rule and the
// personal layer, and never anyone else's. The global model is a fixed stand-in that always says
// FOOD_DINING; everything else (resolver, rules, pg_trgm neighbours, Postgres) is real.
@SpringBootTest
@ActiveProfiles("test")
class PersonalizationIntegrationTest {

    private static final String COFFEE = "BLUE TOKAI COFFEE ROASTERS";

    @Autowired private TransactionService     transactions;
    @Autowired private MerchantRuleService    rules;
    @Autowired private PersonalizationService personalization;
    @Autowired private UserRepository         users;
    @MockitoBean private MlClient             ml;

    private User user;
    private User other;

    @BeforeEach
    void setUp() {
        user  = users.save(newUser());
        other = users.save(newUser());
        when(ml.isActive(MlCapability.CATEGORIZER)).thenReturn(true);
        when(ml.predict(eq(MlCapability.CATEGORIZER), any(), eq(CategorizerResponse.class), any(), any()))
            .thenAnswer(call -> {
                int items = ((CategorizerRequest) call.getArgument(1)).items().size();
                CategorizerResponse.Prediction p = new CategorizerResponse.Prediction("FOOD_DINING", 0.70, List.of(
                    new CategorizerResponse.Alternative("FOOD_DINING", 0.70),
                    new CategorizerResponse.Alternative("SHOPPING", 0.15),
                    new CategorizerResponse.Alternative("GROCERIES", 0.10)));
                return new MlPrediction<>(new CategorizerResponse(Collections.nCopies(items, p)),
                    MlPrediction.Source.MODEL, "stub-1", 0.70);
            });
    }

    @AfterEach
    void tearDown() {
        users.deleteAllById(List.of(user.getId(), other.getId()));
    }

    @Test
    void aCorrectionTeachesThatUserOnly() {
        TransactionResponse older1 = add(user, COFFEE);
        TransactionResponse older2 = add(user, COFFEE);
        TransactionResponse corrected = add(user, COFFEE);
        assertThat(corrected.getCategory()).isEqualTo(TransactionCategory.FOOD_DINING);
        assertThat(corrected.getCategorySource()).isEqualTo(CategorySource.MODEL);

        TransactionResponse fixed = transactions.update(corrected.getId(), user.getId(),
            correction("Blue Tokai", TransactionCategory.SHOPPING));
        assertThat(fixed.getRuleSuggestion()).isNotNull();
        assertThat(fixed.getRuleSuggestion().getSimilarCount()).isEqualTo(2);

        // the same merchant again: the user's rule answers
        TransactionResponse again = add(user, COFFEE);
        assertThat(again.getMerchant()).isEqualTo("Blue Tokai");
        assertThat(again.getCategory()).isEqualTo(TransactionCategory.SHOPPING);
        assertThat(again.getCategorySource()).isEqualTo(CategorySource.PERSONAL);
        assertThat(again.getCategoryModelVersion()).isNull();

        // a close variant resolves to the corrected merchant, so the rule answers there too
        TransactionResponse variant = add(user, "BLUE TOKAI COFFEE");
        assertThat(variant.getMerchant()).isEqualTo("Blue Tokai");
        assertThat(variant.getCategory()).isEqualTo(TransactionCategory.SHOPPING);

        // another user is untouched
        TransactionResponse theirs = add(other, COFFEE);
        assertThat(theirs.getCategory()).isEqualTo(TransactionCategory.FOOD_DINING);
        assertThat(theirs.getCategorySource()).isEqualTo(CategorySource.MODEL);
        assertThat(theirs.getMerchant()).isNotEqualTo("Blue Tokai");

        // "apply to similar" fixes the user's two older rows and nothing else
        ApplyPreviewResponse preview = rules.preview(fixed.getRuleSuggestion().getRuleId(), user);
        assertThat(preview.getMatchCount()).isEqualTo(2);
        assertThat(rules.apply(fixed.getRuleSuggestion().getRuleId(), user).getUpdated()).isEqualTo(2);
        assertThat(transactions.getById(older1.getId(), user.getId()).getCategory()).isEqualTo(TransactionCategory.SHOPPING);
        assertThat(transactions.getById(older2.getId(), user.getId()).getCategory()).isEqualTo(TransactionCategory.SHOPPING);
        assertThat(transactions.getById(theirs.getId(), other.getId()).getCategory()).isEqualTo(TransactionCategory.FOOD_DINING);
    }

    @Test
    void reviewedHistoryMovesSimilarButDifferentMerchants() {
        String store = "GREEN LEAF ORGANIC MART HSR";
        add(user, store);
        add(user, store);
        TransactionResponse corrected = add(user, store);
        TransactionResponse fixed = transactions.update(corrected.getId(), user.getId(),
            correction("Green Leaf Organic Mart", TransactionCategory.GROCERIES));
        rules.apply(fixed.getRuleSuggestion().getRuleId(), user);

        // a different merchant key, so no rule; three similar reviewed rows outweigh the model's FOOD_DINING
        String nearby = "SHREE GREEN LEAF ORGANICS HSR";
        TransactionResponse similar = add(user, nearby);
        assertThat(similar.getMerchant()).isNotEqualTo("Green Leaf Organic Mart");
        assertThat(similar.getCategory()).isEqualTo(TransactionCategory.GROCERIES);
        assertThat(similar.getCategorySource()).isEqualTo(CategorySource.PERSONAL);
        assertThat(similar.getCategoryModelVersion()).isEqualTo("stub-1");

        TransactionResponse theirs = add(other, nearby);
        assertThat(theirs.getCategory()).isEqualTo(TransactionCategory.FOOD_DINING);
        assertThat(theirs.getCategorySource()).isEqualTo(CategorySource.MODEL);
    }

    @Test
    void turningPersonalizationOffReturnsToTheGlobalModel() {
        TransactionResponse corrected = add(user, COFFEE);
        transactions.update(corrected.getId(), user.getId(), correction("Blue Tokai", TransactionCategory.SHOPPING));
        assertThat(add(user, COFFEE).getCategory()).isEqualTo(TransactionCategory.SHOPPING);

        personalization.setEnabled(user.getId(), false);

        TransactionResponse after = add(user, "BLUE TOKAI COFFEE");
        assertThat(after.getCategory()).isEqualTo(TransactionCategory.FOOD_DINING);
        assertThat(after.getCategorySource()).isEqualTo(CategorySource.MODEL);
    }

    private TransactionResponse add(User owner, String description) {
        CreateTransactionRequest r = new CreateTransactionRequest();
        r.setAmount(new BigDecimal("320.00"));
        r.setTransactionDate(LocalDate.now().minusDays(1));
        r.setDescription(description);
        r.setType(TransactionType.DEBIT);
        return transactions.create(r, owner.getId());
    }

    private static UpdateTransactionRequest correction(String merchant, TransactionCategory category) {
        UpdateTransactionRequest r = new UpdateTransactionRequest();
        r.setMerchant(merchant);
        r.setCategory(category);
        return r;
    }

    private static User newUser() {
        return User.builder()
            .email("personal-" + UUID.randomUUID() + "@example.com")
            .passwordHash("x")
            .role(Role.USER)
            .build();
    }
}
