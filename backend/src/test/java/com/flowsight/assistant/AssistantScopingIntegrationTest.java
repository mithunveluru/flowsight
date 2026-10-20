package com.flowsight.assistant;

import com.flowsight.entity.Role;
import com.flowsight.entity.Transaction;
import com.flowsight.entity.TransactionCategory;
import com.flowsight.entity.TransactionSource;
import com.flowsight.entity.TransactionType;
import com.flowsight.entity.User;
import com.flowsight.llm.ChatProvider;
import com.flowsight.llm.StubChatProvider;
import com.flowsight.llm.ToolCall;
import com.flowsight.onboarding.SampleDataService;
import com.flowsight.repository.AuditLogRepository;
import com.flowsight.repository.TransactionRepository;
import com.flowsight.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

// The model asks for another user's data by id; every tool still runs for the signed-in user only.
@SpringBootTest
@ActiveProfiles("test")
class AssistantScopingIntegrationTest {

    static StubChatProvider stub = new StubChatProvider(true);

    @TestConfiguration
    static class StubModel {
        @Bean
        @Primary
        ChatProvider scriptedProvider() {
            return new ChatProvider() {
                @Override
                public boolean isAvailable() {
                    return stub.isAvailable();
                }

                @Override
                public com.flowsight.llm.ChatCompletion complete(List<com.flowsight.llm.ChatMessage> messages,
                        List<com.flowsight.llm.ToolSpec> tools, int maxTokens) throws com.flowsight.llm.LlmException {
                    return stub.complete(messages, tools, maxTokens);
                }
            };
        }
    }

    @Autowired private AssistantService      assistant;
    @Autowired private SampleDataService     sampleData;
    @Autowired private UserRepository        users;
    @Autowired private TransactionRepository transactions;
    @Autowired private AuditLogRepository    auditLogs;

    private User me;
    private User victim;

    @BeforeEach
    void setUp() {
        me = users.save(newUser());
        victim = users.save(newUser());
        sampleData.load(victim.getId());
        transactions.save(Transaction.builder().user(me).amount(new BigDecimal("250.00"))
            .transactionDate(LocalDate.now()).description("Lunch").merchant("Cafe Aster")
            .category(TransactionCategory.FOOD_DINING).type(TransactionType.DEBIT)
            .source(TransactionSource.MANUAL).build());
    }

    @AfterEach
    void tearDown() {
        users.deleteAllById(List.of(me.getId(), victim.getId()));
    }

    @Test
    void toolsNeverReachAnotherUsersData() {
        String foreign = "{\"period\":\"THIS_YEAR\",\"user_id\":\"" + victim.getId() + "\",\"userId\":\"" + victim.getId() + "\"}";
        stub = new StubChatProvider(true)
            .callTools(new ToolCall("c1", "spending_summary", foreign), new ToolCall("c2", "top_merchants", foreign),
                new ToolCall("c3", "recurring_payments", foreign), new ToolCall("c4", "goal_status", foreign))
            .reply("This year you spent ₹250 [t1], all at Cafe Aster [t2].");

        AssistantAnswer a = assistant.ask(me, "How much did I spend this year?");

        assertThat(a.mode()).isEqualTo("LLM");
        assertThat(a.citations()).extracting(Citation::tool).containsExactly("spending_summary", "top_merchants");
        assertThat(a.citations().get(0).facts().get("total_spend")).hasToString("250");
        List<String> victimMerchants = transactions.topMerchantsRaw(victim.getId(), LocalDate.now().minusYears(1),
            LocalDate.now(), PageRequest.of(0, 5)).stream().map(r -> (String) r[0]).toList();
        assertThat(victimMerchants).isNotEmpty();
        String sent = stub.allSentText();
        for (String merchant : victimMerchants) assertThat(sent).doesNotContain(merchant);
        assertThat(sent).contains("Cafe Aster").doesNotContain(victim.getEmail());
        assertThat(auditLogs.findByUserIdOrderByCreatedAtDesc(me.getId(), PageRequest.of(0, 5)).getContent())
            .anyMatch(l -> AssistantService.AUDIT_ACTION.equals(l.getAction()));
    }

    @Test
    void withoutAModelTheRulesPathIsScopedToo() {
        stub = new StubChatProvider(false);

        AssistantAnswer a = assistant.ask(me, "Who did I pay the most this year?");

        assertThat(a.mode()).isEqualTo("RULES");
        assertThat(a.answer()).contains("Cafe Aster").contains("₹250");
        assertThat(a.citations().get(0).facts()).containsOnlyKeys("period", "from", "to",
            "merchants.1.name", "merchants.1.spend", "merchants.1.transaction_count");
    }

    private static User newUser() {
        return User.builder()
            .email("assistant-" + UUID.randomUUID() + "@example.com")
            .passwordHash("x")
            .role(Role.USER)
            .build();
    }
}
