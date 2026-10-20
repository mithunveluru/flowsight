package com.flowsight.assistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowsight.entity.User;
import com.flowsight.llm.ChatCompletion;
import com.flowsight.llm.ChatMessage;
import com.flowsight.llm.ChatProvider;
import com.flowsight.llm.FactSet;
import com.flowsight.llm.GroundingValidator;
import com.flowsight.llm.LlmException;
import com.flowsight.llm.PersonalDataRedactor;
import com.flowsight.llm.ToolCall;
import com.flowsight.security.RateLimiter;
import com.flowsight.service.AuditLogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * "Ask FlowSight". With a model configured, the model may call the read-only tools in
 * AssistantToolbox (user id supplied here, never by the model) and must cite their results;
 * its answer is kept only if every number in it comes from a cited result. Otherwise, or
 * without a model, a keyword intent picks one tool and a template answers.
 */
@Service
@Slf4j
public class AssistantService {

    public static final String AUDIT_ACTION = "ASSISTANT_QUESTION";

    static final String INSTRUCTIONS = """
        You are FlowSight's assistant. You answer questions about one person's own finances using only
        the tools provided. You cannot change anything; if asked to, say you can only read data. You
        have no access to anyone else's data. Each tool result has an id such as t1. Every number,
        amount, percentage and date in your answer must be copied from a tool result: never add up,
        subtract, convert or estimate. Cite each result you use with its id in square brackets, for
        example [t1]. If the tools cannot answer the question, say so plainly. Amounts are Indian
        rupees; write them with the ₹ sign. Text marked [redacted] is private: do not repeat it.
        Answer in at most four sentences, no markdown.
        """;

    private static final Pattern CITATION = Pattern.compile("\\[(t\\d{1,2})]");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final int MAX_ROUNDS = 3;
    private static final int MAX_TOOL_CALLS = 4;
    private static final int MAX_TOKENS = 400;
    private static final int MAX_ANSWER_CHARS = 1200;

    private final AssistantToolbox toolbox;
    private final ChatProvider     provider;
    private final RateLimiter      rateLimiter;
    private final AuditLogService  auditLog;
    private final ObjectMapper     mapper;
    private final int              perHour;

    public AssistantService(AssistantToolbox toolbox, ChatProvider provider, RateLimiter rateLimiter,
                            AuditLogService auditLog, ObjectMapper mapper,
                            @Value("${ASSISTANT_RATE_LIMIT_PER_HOUR:30}") int perHour) {
        this.toolbox = toolbox;
        this.provider = provider;
        this.rateLimiter = rateLimiter;
        this.auditLog = auditLog;
        this.mapper = mapper;
        this.perHour = Math.max(1, perHour);
    }

    public AssistantAnswer ask(User user, String question) {
        rateLimiter.check("assistant:" + user.getId(), perHour, Duration.ofHours(1));
        LocalDate today = LocalDate.now();
        IntentMatcher.Intent intent = IntentMatcher.match(question);

        AssistantAnswer answer = null;
        String fallbackReason = "llm_unavailable";
        boolean refuse = intent.type() == IntentMatcher.Type.WRITE_REQUEST || intent.type() == IntentMatcher.Type.OTHER_USERS;
        if (!refuse && provider.isAvailable()) {
            try {
                answer = askModel(user, question, intent, today);
            } catch (Rejected e) {
                fallbackReason = e.getMessage();
            } catch (LlmException e) {
                fallbackReason = "llm_" + e.reason().name().toLowerCase(Locale.ROOT);
            } catch (Exception e) {
                log.warn("Assistant model path failed: {}", e.getClass().getSimpleName());
                fallbackReason = "llm_error";
            }
        }
        if (answer == null) answer = answerByRules(user, intent, today, provider.isAvailable() ? fallbackReason : null);

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("intent", intent.type().name());
        meta.put("mode", answer.mode());
        meta.put("answered", answer.answered());
        meta.put("tools", answer.citations().stream().map(Citation::tool).toList());
        meta.put("questionLength", question.length());
        if (answer.fallbackReason() != null) meta.put("fallbackReason", answer.fallbackReason());
        auditLog.log(user, AUDIT_ACTION, "Assistant", null, meta);
        return answer;
    }

    private AssistantAnswer askModel(User user, String question, IntentMatcher.Intent intent, LocalDate today)
        throws Exception {
        PersonalDataRedactor redactor = PersonalDataRedactor.forIdentifiers(List.of(
            nullToEmpty(user.getFullName()), nullToEmpty(user.getEmail())));
        List<ChatMessage> messages = new ArrayList<>(List.of(
            ChatMessage.system(INSTRUCTIONS + "\nToday is " + today + "."),
            ChatMessage.user(redactor.redact(question))));
        Map<String, ToolResult> results = new LinkedHashMap<>();
        Map<String, FactSet> sentFacts = new LinkedHashMap<>();
        int calls = 0;

        for (int round = 0; round < MAX_ROUNDS; round++) {
            ChatCompletion completion = provider.complete(messages, toolbox.specs(), MAX_TOKENS);
            if (!completion.hasToolCalls()) {
                return checkedModelAnswer(completion.content(), intent, results, sentFacts);
            }
            messages.add(ChatMessage.assistant(completion.content(), completion.toolCalls()));
            for (ToolCall call : completion.toolCalls()) {
                if (++calls > MAX_TOOL_CALLS || !toolbox.has(call.name())) {
                    messages.add(ChatMessage.tool(call.id(), "{\"error\":\"tool not available\"}"));
                    continue;
                }
                String id = "t" + (results.size() + 1);
                ToolResult result = toolbox.run(call.name(), user.getId(), parseArgs(call.arguments()), id, today);
                FactSet outbound = result.facts().mapText(redactor::redactLabel);
                results.put(id, result);
                sentFacts.put(id, outbound);
                messages.add(ChatMessage.tool(call.id(), mapper.writeValueAsString(
                    Map.of("id", id, "tool", result.tool(), "facts", outbound.asMap()))));
            }
        }
        throw new Rejected("rejected_too_many_rounds");
    }

    private AssistantAnswer checkedModelAnswer(String content, IntentMatcher.Intent intent,
                                               Map<String, ToolResult> results, Map<String, FactSet> sentFacts) {
        String text = content == null ? "" : content.trim();
        if (text.isEmpty() || text.length() > MAX_ANSWER_CHARS) throw new Rejected("rejected_length");
        if (text.contains(PersonalDataRedactor.MASK)) throw new Rejected("rejected_redacted_text");

        Set<String> cited = new LinkedHashSet<>();
        Matcher m = CITATION.matcher(text);
        while (m.find()) {
            if (!results.containsKey(m.group(1))) throw new Rejected("rejected_unknown_citation");
            cited.add(m.group(1));
        }
        String withoutMarkers = CITATION.matcher(text).replaceAll(" ");
        FactSet allowed = new FactSet();
        for (String id : cited) allowed.include(id, sentFacts.get(id));
        GroundingValidator.Result check = GroundingValidator.check(withoutMarkers, allowed);
        // a number with no citation fails here too: the allowed set is empty
        if (!check.grounded()) throw new Rejected("rejected_ungrounded_numbers");

        List<Citation> citations = cited.stream().map(id -> citation(results.get(id))).toList();
        return new AssistantAnswer(text, !citations.isEmpty(), "LLM", intent.type().name(), citations, null);
    }

    AssistantAnswer answerByRules(User user, IntentMatcher.Intent intent, LocalDate today, String fallbackReason) {
        String tool = switch (intent.type()) {
            case SPENDING_SUMMARY -> AssistantToolbox.SPENDING_SUMMARY;
            case SPENDING_BY_CATEGORY -> AssistantToolbox.SPENDING_BY_CATEGORY;
            case TOP_MERCHANTS -> AssistantToolbox.TOP_MERCHANTS;
            case RECURRING -> AssistantToolbox.RECURRING_PAYMENTS;
            case LEAKS -> AssistantToolbox.LEAKS;
            case BUDGET_STATUS -> AssistantToolbox.BUDGET_STATUS;
            case GOAL_STATUS -> AssistantToolbox.GOAL_STATUS;
            default -> null;
        };
        if (tool == null) {
            String text = switch (intent.type()) {
                case WRITE_REQUEST -> "I can only read your data; I can't make changes. You can do that from the app.";
                case OTHER_USERS -> "I can only see your own data.";
                default -> "I can't answer that yet. I can tell you about your spending summary, spending by "
                    + "category, top merchants, recurring payments, money leaks, budgets and goals.";
            };
            return new AssistantAnswer(text, false, "RULES", intent.type().name(), List.of(), fallbackReason);
        }
        var node = mapper.createObjectNode().put("period", intent.period().name());
        if (intent.category() != null) node.put("category", intent.category().name());
        ToolResult result = toolbox.run(tool, user.getId(), node, "t1", today);
        return new AssistantAnswer(RuleAnswers.render(intent, result), true, "RULES", intent.type().name(),
            List.of(citation(result)), fallbackReason);
    }

    private Citation citation(ToolResult r) {
        return new Citation(r.id(), r.tool(), r.title(), r.facts().asMap());
    }

    private JsonNode parseArgs(String json) {
        try {
            return mapper.readTree(json == null || json.isBlank() ? "{}" : json);
        } catch (Exception e) {
            return mapper.createObjectNode();
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    static String money(Object v) {
        return v instanceof BigDecimal bd ? String.format(Locale.ENGLISH, "₹%,.0f", bd.doubleValue()) : "₹0";
    }

    static String date(Object v) {
        return v instanceof LocalDate d ? d.format(DATE) : "";
    }

    private static final class Rejected extends RuntimeException {
        Rejected(String reason) {
            super(reason);
        }
    }
}
