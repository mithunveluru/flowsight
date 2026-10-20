package com.flowsight.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowsight.ocr.AIInterpretation;
import com.flowsight.ocr.GroqAIProvider;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/** Calls the real Groq API; skipped unless -Deval.groqKey is set. Synthetic receipt text only. */
class GroqLiveCheckTest {

    @Test
    void retiredModelNamesFallThroughToOneTheKeyServes() {
        String key = System.getProperty("eval.groqKey", "");
        assumeTrue(!key.isBlank(), "set -Deval.groqKey to run");

        GroqAIProvider provider = new GroqAIProvider(key, "llama-3.1-8b-instant,openai/gpt-oss-20b", new ObjectMapper());
        Optional<AIInterpretation> answer = provider.interpret(
            "ORIGINAL FOR RECIPIENT\nTAX INVOICE\nHALDIRAM'S SNACKS PVT LTD\nMG Road, Bengaluru\nTOTAL 412.00",
            List.of("ORIGINAL FOR RECIPIENT", "HALDIRAM'S SNACKS PVT LTD"));

        assertThat(answer).isPresent();
        assertThat(answer.get().getMerchant()).containsIgnoringCase("haldiram");
        System.out.println("groq live check: merchant=" + answer.get().getMerchant());
    }
}
