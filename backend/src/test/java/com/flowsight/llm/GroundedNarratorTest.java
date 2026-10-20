package com.flowsight.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GroundedNarratorTest {

    private static final String TEMPLATE = "You spent ₹12,500 this month.";
    private final FactSet facts = new FactSet()
        .amount("total_spend", new BigDecimal("12500"))
        .percent("dining_share", 31.0)
        .text("goal", "Trip with Meera Iyer, call 98450 12345");
    private final PersonalDataRedactor redactor = PersonalDataRedactor.forIdentifiers(List.of("Meera Iyer"));

    private GroundedNarrator.Narrative narrate(StubChatProvider stub) {
        return new GroundedNarrator(stub, new ObjectMapper()).narrate("Summarise.", facts, TEMPLATE, redactor, 300);
    }

    @Test
    void groundedModelTextIsUsed() {
        GroundedNarrator.Narrative n = narrate(new StubChatProvider(true)
            .reply("This month you spent ₹12.5k, with dining at 31% of it."));

        assertThat(n.source()).isEqualTo(GroundedNarrator.Source.LLM);
        assertThat(n.text()).contains("₹12.5k");
    }

    @Test
    void inventedNumberFallsBackToTemplate() {
        GroundedNarrator.Narrative n = narrate(new StubChatProvider(true)
            .reply("You spent ₹12,500, which is ₹1,50,000 a year."));

        assertThat(n.source()).isEqualTo(GroundedNarrator.Source.TEMPLATE);
        assertThat(n.text()).isEqualTo(TEMPLATE);
        assertThat(n.fallbackReason()).isEqualTo("rejected_ungrounded_numbers");
    }

    @Test
    void providerErrorTimeoutAndMissingModelFallBack() {
        for (LlmException.Reason reason : List.of(LlmException.Reason.TIMEOUT,
                LlmException.Reason.MODEL_UNAVAILABLE, LlmException.Reason.HTTP_ERROR)) {
            GroundedNarrator.Narrative n = narrate(new StubChatProvider(true).fail(reason));

            assertThat(n.source()).isEqualTo(GroundedNarrator.Source.TEMPLATE);
            assertThat(n.text()).isEqualTo(TEMPLATE);
            assertThat(n.fallbackReason()).isEqualTo("llm_" + reason.name().toLowerCase());
        }
    }

    @Test
    void unavailableProviderIsNeverCalled() {
        StubChatProvider stub = new StubChatProvider(false).reply("unused");

        GroundedNarrator.Narrative n = narrate(stub);

        assertThat(n.source()).isEqualTo(GroundedNarrator.Source.TEMPLATE);
        assertThat(stub.requests).isEmpty();
    }

    @Test
    void personalDataIsRedactedBeforeTheCall() {
        StubChatProvider stub = new StubChatProvider(true).reply("You spent ₹12,500.");

        narrate(stub);

        assertThat(stub.allSentText()).doesNotContain("Meera").doesNotContain("98450").contains("12500");
    }

    @Test
    void echoingRedactedTextOrOverlongOutputFallsBack() {
        assertThat(narrate(new StubChatProvider(true).reply("Your goal [redacted] is on track.")).source())
            .isEqualTo(GroundedNarrator.Source.TEMPLATE);
        assertThat(narrate(new StubChatProvider(true).reply("x".repeat(400))).fallbackReason())
            .isEqualTo("rejected_length");
    }
}
