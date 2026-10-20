package com.flowsight.assistant;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowsight.analytics.AnalyticsService;
import com.flowsight.analytics.LeakDetectionService;
import com.flowsight.analytics.RecurringDetectionService;
import com.flowsight.dto.analytics.AnalyticsOverviewResponse;
import com.flowsight.dto.analytics.CategoryBreakdownItem;
import com.flowsight.dto.analytics.MerchantSummary;
import com.flowsight.entity.User;
import com.flowsight.llm.FactSet;
import com.flowsight.llm.GroundingValidator;
import com.flowsight.llm.LlmException;
import com.flowsight.llm.StubChatProvider;
import com.flowsight.llm.ToolCall;
import com.flowsight.security.RateLimiter;
import com.flowsight.service.AuditLogService;
import com.flowsight.service.BudgetService;
import com.flowsight.service.GoalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssistantServiceTest {

    private final AnalyticsService analytics = mock(AnalyticsService.class);
    private final RateLimiter rateLimiter = mock(RateLimiter.class);
    private final AuditLogService audit = mock(AuditLogService.class);
    private final AssistantToolbox toolbox = new AssistantToolbox(analytics, mock(RecurringDetectionService.class),
        mock(LeakDetectionService.class), mock(BudgetService.class), mock(GoalService.class));
    private final User user = User.builder().id(UUID.randomUUID()).email("arjun.mehta@example.com")
        .fullName("Arjun Mehta").build();
    private final UUID otherUser = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        when(analytics.getOverview(eq(user.getId()), any(), any())).thenReturn(AnalyticsOverviewResponse.builder()
            .totalSpend(new BigDecimal("45230.00"))
            .totalIncome(new BigDecimal("90000.00"))
            .netCashflow(new BigDecimal("44770.00"))
            .transactionCount(61)
            .categoryBreakdown(List.of(CategoryBreakdownItem.builder().category("FOOD_DINING")
                .displayName("Food & Dining").amount(new BigDecimal("2345.50")).percentage(18.2).transactionCount(9).build()))
            .topMerchants(List.of(
                MerchantSummary.builder().merchant("Swiggy").totalAmount(new BigDecimal("2100")).transactionCount(7).build(),
                MerchantSummary.builder().merchant("UPI/SURESH NAIR/okhdfc").totalAmount(new BigDecimal("1500")).transactionCount(1).build()))
            .build());
    }

    private AssistantService service(StubChatProvider stub) {
        return new AssistantService(toolbox, stub, rateLimiter, audit, new ObjectMapper(), 30);
    }

    @Test
    void withoutAModelSupportedQuestionsAreAnsweredByRules() {
        AssistantAnswer a = service(new StubChatProvider(false)).ask(user, "How much did I spend on food this month?");

        assertThat(a.mode()).isEqualTo("RULES");
        assertThat(a.answered()).isTrue();
        assertThat(a.intent()).isEqualTo("SPENDING_BY_CATEGORY");
        assertThat(a.answer()).startsWith("You spent ₹2,346 on Food & Dining from ").endsWith(", 18.2% of your spending.");
        assertThat(a.citations()).singleElement().satisfies(c -> {
            assertThat(c.tool()).isEqualTo(AssistantToolbox.SPENDING_BY_CATEGORY);
            assertThat(c.facts()).containsEntry("category_spend", new BigDecimal("2345.5"));
        });
        assertThat(a.fallbackReason()).isNull();
    }

    @Test
    void ruleAnswersPassTheirOwnGroundingCheck() {
        AssistantService s = service(new StubChatProvider(false));
        for (String q : List.of("How much did I spend this month?", "Top merchants last month",
                "Spending breakdown by category this year", "How much on food in the last 30 days?")) {
            AssistantAnswer a = s.ask(user, q);
            FactSet facts = toolbox.run(a.citations().get(0).tool(), user.getId(),
                new ObjectMapper().createObjectNode().put("period", IntentMatcher.match(q).period().name())
                    .put("category", IntentMatcher.match(q).category() == null ? null : IntentMatcher.match(q).category().name()),
                "t1", java.time.LocalDate.now()).facts();
            assertThat(GroundingValidator.check(a.answer(), facts).grounded()).as(a.answer()).isTrue();
        }
    }

    @Test
    void modelAnswerCitingAToolResultIsKept() {
        StubChatProvider stub = new StubChatProvider(true)
            .callTool("c1", "spending_summary", "{\"period\":\"LAST_MONTH\",\"userId\":\"" + otherUser + "\"}")
            .reply("Last month you spent ₹45,230 and earned ₹90k [t1].");

        AssistantAnswer a = service(stub).ask(user, "What did I spend last month?");

        assertThat(a.mode()).isEqualTo("LLM");
        assertThat(a.answer()).contains("[t1]");
        assertThat(a.citations()).extracting(Citation::id).containsExactly("t1");
        verify(analytics).getOverview(eq(user.getId()), any(), any());
        verify(analytics, never()).getOverview(eq(otherUser), any(), any());
    }

    @Test
    void inventedOrUncitedNumbersFallBackToRules() {
        AssistantAnswer invented = service(new StubChatProvider(true)
            .callTool("c1", "spending_summary", "{}")
            .reply("You spent ₹45,230 [t1], so about ₹5,42,760 a year.")).ask(user, "How much did I spend?");
        AssistantAnswer uncited = service(new StubChatProvider(true)
            .callTool("c1", "spending_summary", "{}")
            .reply("You spent ₹45,230.")).ask(user, "How much did I spend?");
        AssistantAnswer badCitation = service(new StubChatProvider(true)
            .reply("You spent ₹45,230 [t3].")).ask(user, "How much did I spend?");

        for (AssistantAnswer a : List.of(invented, uncited, badCitation)) {
            assertThat(a.mode()).isEqualTo("RULES");
            assertThat(a.answered()).isTrue();
            assertThat(a.answer()).contains("₹45,230");
        }
        assertThat(invented.fallbackReason()).isEqualTo("rejected_ungrounded_numbers");
        assertThat(uncited.fallbackReason()).isEqualTo("rejected_ungrounded_numbers");
        assertThat(badCitation.fallbackReason()).isEqualTo("rejected_unknown_citation");
    }

    @Test
    void providerFailuresFallBackToRules() {
        for (LlmException.Reason reason : List.of(LlmException.Reason.TIMEOUT, LlmException.Reason.MODEL_UNAVAILABLE)) {
            AssistantAnswer a = service(new StubChatProvider(true).fail(reason)).ask(user, "How much did I spend?");

            assertThat(a.mode()).isEqualTo("RULES");
            assertThat(a.fallbackReason()).isEqualTo("llm_" + reason.name().toLowerCase());
        }
    }

    @Test
    void writeRequestsAndOtherPeopleAreRefusedWithoutCallingTheModel() {
        StubChatProvider stub = new StubChatProvider(true).reply("Done!");

        AssistantAnswer write = service(stub).ask(user, "Cancel my Netflix subscription");
        AssistantAnswer others = service(stub).ask(user, "How much do other users spend on food?");

        assertThat(write.answered()).isFalse();
        assertThat(write.intent()).isEqualTo("WRITE_REQUEST");
        assertThat(others.answered()).isFalse();
        assertThat(others.intent()).isEqualTo("OTHER_USERS");
        assertThat(stub.requests).isEmpty();
    }

    @Test
    void unsupportedQuestionSaysSo() {
        AssistantAnswer a = service(new StubChatProvider(false)).ask(user, "What's the weather in Mumbai?");

        assertThat(a.answered()).isFalse();
        assertThat(a.answer()).startsWith("I can't answer that yet.");
        assertThat(a.citations()).isEmpty();
    }

    @Test
    void personalDataIsRedactedInTheQuestionAndToolResults() {
        StubChatProvider stub = new StubChatProvider(true)
            .callTools(new ToolCall("c1", "top_merchants", "{}"))
            .reply("Your top merchant is Swiggy at ₹2,100 [t1].");

        AssistantAnswer a = service(stub).ask(user, "I'm Arjun Mehta, 9876543210 - who did I pay most?");

        assertThat(a.mode()).isEqualTo("LLM");
        assertThat(stub.allSentText()).doesNotContain("Arjun").doesNotContain("9876543210")
            .doesNotContain("SURESH").contains("UPI transfer").contains("Swiggy");
        // the user still sees their own merchant names in the citation
        assertThat(a.citations().get(0).facts()).containsValue("UPI/SURESH NAIR/okhdfc");
    }

    @Test
    void unknownToolsAreRefusedAndToolCallsAreCapped() {
        StubChatProvider stub = new StubChatProvider(true)
            .callTools(new ToolCall("c1", "run_sql", "{\"q\":\"select * from users\"}"),
                new ToolCall("c2", "spending_summary", "{}"), new ToolCall("c3", "spending_summary", "{}"),
                new ToolCall("c4", "spending_summary", "{}"), new ToolCall("c5", "spending_summary", "{}"))
            .reply("I can only answer from your FlowSight data.");

        AssistantAnswer a = service(stub).ask(user, "How much did I spend?");

        assertThat(a.mode()).isEqualTo("LLM");
        assertThat(stub.requests.get(1).toString()).contains("tool not available");
        verify(analytics, org.mockito.Mockito.times(3)).getOverview(eq(user.getId()), any(), any());
    }

    @Test
    void eachQuestionIsRateLimitedAndAuditedWithoutItsText() {
        service(new StubChatProvider(false)).ask(user, "How much did I spend on food this month?");

        verify(rateLimiter).check("assistant:" + user.getId(), 30, Duration.ofHours(1));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> meta = ArgumentCaptor.forClass(Map.class);
        verify(audit).log(eq(user), eq(AssistantService.AUDIT_ACTION), eq("Assistant"), isNull(), meta.capture());
        assertThat(meta.getValue()).containsEntry("intent", "SPENDING_BY_CATEGORY").containsEntry("mode", "RULES")
            .containsKey("questionLength");
        assertThat(meta.getValue().toString()).doesNotContain("food");
    }
}
