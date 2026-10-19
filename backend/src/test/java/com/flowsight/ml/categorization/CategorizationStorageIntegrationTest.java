package com.flowsight.ml.categorization;

import com.flowsight.dto.transaction.CreateTransactionRequest;
import com.flowsight.dto.transaction.TransactionResponse;
import com.flowsight.dto.transaction.UpdateTransactionRequest;
import com.flowsight.entity.Role;
import com.flowsight.entity.Transaction;
import com.flowsight.entity.TransactionCategory;
import com.flowsight.entity.TransactionSource;
import com.flowsight.entity.TransactionType;
import com.flowsight.entity.User;
import com.flowsight.repository.TransactionRepository;
import com.flowsight.repository.UserRepository;
import com.flowsight.service.TransactionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// V18 columns, pg_trgm neighbours and the review queue against Postgres, always per user.
@SpringBootTest
@ActiveProfiles("test")
class CategorizationStorageIntegrationTest {

    @Autowired private TransactionService    transactions;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private DbReviewedHistory     history;
    @Autowired private UserRepository        users;

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
    void categoryProvenanceRoundTrips() {
        Transaction tx = row(user, "Moonlight Grill", "POS MOONLIGHT GRILL DUBAI", false, "0.6100");
        tx.setCategorySource(CategorySource.MODEL);
        tx.setCategoryModelVersion("tfidf-lr-1");
        tx.setCategoryAlternatives(List.of(
            new CategoryAlternative(TransactionCategory.FOOD_DINING, 0.61),
            new CategoryAlternative(TransactionCategory.GROCERIES, 0.2)));
        UUID id = transactionRepository.save(tx).getId();

        TransactionResponse loaded = transactions.getById(id, user.getId());

        assertThat(loaded.getCategorySource()).isEqualTo(CategorySource.MODEL);
        assertThat(loaded.getCategoryModelVersion()).isEqualTo("tfidf-lr-1");
        assertThat(loaded.getCategoryAlternatives()).containsExactly(
            new CategoryAlternative(TransactionCategory.FOOD_DINING, 0.61),
            new CategoryAlternative(TransactionCategory.GROCERIES, 0.2));
    }

    @Test
    void manualEntryWithoutTheModelIsMarkedRulesAndACorrectionIsMarkedUser() {
        CreateTransactionRequest r = new CreateTransactionRequest();
        r.setAmount(new BigDecimal("12.00"));
        r.setTransactionDate(LocalDate.now().minusDays(1));
        r.setDescription("ZOMATO ORDER");
        r.setType(TransactionType.DEBIT);
        TransactionResponse created = transactions.create(r, user.getId());
        assertThat(created.getCategorySource()).isEqualTo(CategorySource.RULES);

        UpdateTransactionRequest edit = new UpdateTransactionRequest();
        edit.setCategory(TransactionCategory.GROCERIES);
        TransactionResponse corrected = transactions.update(created.getId(), user.getId(), edit);

        assertThat(corrected.getCategorySource()).isEqualTo(CategorySource.USER);
        assertThat(corrected.getCategoryAlternatives()).isNull();
    }

    @Test
    void neighboursComeOnlyFromTheUsersOwnReviewedRows() {
        transactionRepository.save(row(user, "Moonlight Grill", "POS MOONLIGHT GRILL DUBAI", true, "1.0000"));
        transactionRepository.save(row(user, "Moonlight Grocer", "POS MOONLIGHT GROCER", false, "0.4000"));
        Transaction sample = row(user, "Moonlight Grill", "POS MOONLIGHT GRILL", true, "1.0000");
        sample.setSource(TransactionSource.SAMPLE);
        transactionRepository.save(sample);
        transactionRepository.save(row(other, "Moonlight Grill", "POS MOONLIGHT GRILL DUBAI", true, "1.0000"));

        List<CategorizerRequest.Item> query = List.of(
            CategorizerRequest.Item.of("POS MOONLIGHT GRILL DUBAI", "Moonlight Grill", TransactionType.DEBIT),
            CategorizerRequest.Item.of("SOMETHING ELSE", "Unrelated", null));
        List<List<Neighbour>> mine = history.nearest(user.getId(), query);

        assertThat(mine).hasSize(2);
        assertThat(mine.get(0)).hasSize(1);
        assertThat(mine.get(0).get(0).category()).isEqualTo(TransactionCategory.FOOD_DINING);
        assertThat(mine.get(0).get(0).similarity()).isEqualTo(1.0);
        assertThat(mine.get(1)).isEmpty();

        UUID stranger = UUID.randomUUID();
        assertThat(history.nearest(stranger, query)).allMatch(List::isEmpty);
    }

    @Test
    void reviewQueueListsTheUsersLeastCertainUnreviewedRows() {
        Transaction sure = row(user, "A", "A", false, "0.9000");
        Transaction unsure = row(user, "B", "B", false, "0.3000");
        Transaction tie = row(user, "C", "C", false, "0.3000");
        tie.setCategoryAlternatives(List.of(
            new CategoryAlternative(TransactionCategory.FOOD_DINING, 0.30),
            new CategoryAlternative(TransactionCategory.GROCERIES, 0.29)));
        unsure.setCategoryAlternatives(List.of(
            new CategoryAlternative(TransactionCategory.FOOD_DINING, 0.30),
            new CategoryAlternative(TransactionCategory.GROCERIES, 0.10)));
        Transaction reviewed = row(user, "D", "D", true, "0.1000");
        Transaction sample = row(user, "E", "E", false, "0.0500");
        sample.setSource(TransactionSource.SAMPLE);
        Transaction foreign = row(other, "F", "F", false, "0.0100");
        transactionRepository.saveAll(List.of(sure, unsure, tie, reviewed, sample, foreign));

        List<TransactionResponse> queue = transactions.reviewQueue(user.getId(), 10);

        assertThat(queue).extracting(TransactionResponse::getMerchant).containsExactly("C", "B", "A");
        assertThat(transactions.reviewQueue(user.getId(), 1)).hasSize(1);
        assertThat(transactions.reviewQueue(other.getId(), 10)).extracting(TransactionResponse::getMerchant)
            .containsExactly("F");
    }

    private static Transaction row(User owner, String merchant, String description, boolean reviewed, String confidence) {
        return Transaction.builder()
            .user(owner)
            .amount(new BigDecimal("20.00"))
            .transactionDate(LocalDate.now().minusDays(2))
            .description(description)
            .merchant(merchant)
            .category(TransactionCategory.FOOD_DINING)
            .type(TransactionType.DEBIT)
            .source(TransactionSource.CSV)
            .confidenceScore(new BigDecimal(confidence))
            .categorySource(reviewed ? CategorySource.USER : CategorySource.RULES)
            .reviewed(reviewed)
            .build();
    }

    private static User newUser() {
        return User.builder()
            .email("categorizer-" + UUID.randomUUID() + "@example.com")
            .passwordHash("x")
            .role(Role.USER)
            .build();
    }
}
