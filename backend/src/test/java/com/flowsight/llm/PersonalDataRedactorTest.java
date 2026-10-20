package com.flowsight.llm;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PersonalDataRedactorTest {

    private final PersonalDataRedactor redactor =
        PersonalDataRedactor.forIdentifiers(Arrays.asList("Priya Raman", "priya.raman88@example.com", null));

    @Test
    void masksTheUsersOwnNameAndEmail() {
        String out = redactor.redact("Hi, I'm Priya Raman (priya.raman88@example.com). How much did PRIYA spend?");

        assertThat(out).doesNotContainIgnoringCase("priya").doesNotContain("raman88").doesNotContain("example.com");
    }

    @Test
    void masksPhonesCardsHandlesAndTaxIds() {
        String out = redactor.redact("Call +91 98765 43210, card 4111 1111 1111 1111 or ****1234, UPI rahul.k@okaxis, GSTIN 29ABCDE1234F1Z5");

        assertThat(out).doesNotContain("98765").doesNotContain("4111").doesNotContain("1234")
            .doesNotContain("rahul.k@okaxis").doesNotContain("29ABCDE1234F1Z5");
    }

    @Test
    void keepsAmountsDatesAndMerchantNames() {
        String out = redactor.redact("Spent ₹1,234.50 at Swiggy on 2026-09-14");

        assertThat(out).isEqualTo("Spent ₹1,234.50 at Swiggy on 2026-09-14");
    }

    @Test
    void emailDomainIsNotTreatedAsAName() {
        assertThat(redactor.redact("Bought on example store")).isEqualTo("Bought on example store");
    }

    @Test
    void personToPersonCounterpartiesKeepOnlyTheRail() {
        assertThat(redactor.redactLabel("UPI/RAHUL KUMAR/9876543210@ybl")).isEqualTo("UPI transfer");
        assertThat(redactor.redactLabel("NEFT-ANITA SHARMA-HDFC")).isEqualTo("NEFT transfer");
        assertThat(redactor.redactLabel("Netflix")).isEqualTo("Netflix");
    }

    @Test
    void factTextIsRedactedButNumbersAreNot() {
        FactSet facts = new FactSet().text("goal", "Priya's wedding").amount("target", 500000.0);

        FactSet out = facts.mapText(redactor::redactLabel);

        assertThat(out.get("goal")).isEqualTo(PersonalDataRedactor.MASK + "'s wedding");
        assertThat(out.asMap().get("target").toString()).isEqualTo("500000");
        assertThat(List.copyOf(facts.all())).hasSize(2);
    }
}
