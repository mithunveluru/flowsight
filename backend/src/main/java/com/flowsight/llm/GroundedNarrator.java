package com.flowsight.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Structured facts in, prose out. The model only sees redacted facts and its answer is kept
 * only if every number and date in it is one of those facts; otherwise, or when no model is
 * configured or the call fails, the caller's deterministic template is returned.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class GroundedNarrator {

    public enum Source { LLM, TEMPLATE }

    public record Narrative(String text, Source source, String fallbackReason) {
    }

    static final String INSTRUCTIONS = """
        You write short, plain-language summaries of one person's finances for a personal finance app.
        Use only the facts in the JSON you are given. Every number, amount, percentage and date you
        write must appear in the facts; never calculate, convert, round to new units, or estimate.
        Amounts are in Indian rupees; write them with the ₹ sign. Text marked [redacted] is private:
        do not mention it. No greetings, no advice beyond what the facts support, no markdown.
        """;

    private static final int MAX_TOKENS = 350;

    private final ChatProvider provider;
    private final ObjectMapper mapper;

    /**
     * @param task        what to write, e.g. "Write a 2-3 sentence summary of this period."
     * @param facts       everything the prose may state
     * @param template    deterministic text used whenever the model's text is not usable
     * @param redactor    masks the user's identifiers in text facts before they leave
     * @param maxChars    longer model output is rejected
     */
    public Narrative narrate(String task, FactSet facts, String template, PersonalDataRedactor redactor, int maxChars) {
        if (!provider.isAvailable()) return fallback(template, "llm_unavailable");
        FactSet outbound = facts.mapText(redactor::redactLabel);
        String text;
        try {
            String factsJson = mapper.writeValueAsString(outbound.asMap());
            ChatCompletion completion = provider.complete(List.of(
                ChatMessage.system(INSTRUCTIONS + "\n" + task),
                ChatMessage.user("Facts:\n" + factsJson)), List.of(), MAX_TOKENS);
            text = completion.content() == null ? "" : completion.content().trim();
        } catch (LlmException e) {
            log.info("Narrative fell back to template: {}", e.reason());
            return fallback(template, "llm_" + e.reason().name().toLowerCase());
        } catch (Exception e) {
            log.warn("Narrative fell back to template: {}", e.getClass().getSimpleName());
            return fallback(template, "llm_error");
        }

        if (text.isEmpty() || text.length() > maxChars) return fallback(template, "rejected_length");
        if (text.contains(PersonalDataRedactor.MASK)) return fallback(template, "rejected_redacted_text");
        GroundingValidator.Result check = GroundingValidator.check(text, outbound);
        if (!check.grounded()) {
            log.info("Narrative rejected: {} ungrounded value(s)", check.ungrounded().size());
            return fallback(template, "rejected_ungrounded_numbers");
        }
        return new Narrative(text, Source.LLM, null);
    }

    private static Narrative fallback(String template, String reason) {
        return new Narrative(template, Source.TEMPLATE, reason);
    }
}
