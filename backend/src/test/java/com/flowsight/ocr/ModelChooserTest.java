package com.flowsight.ocr;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ModelChooserTest {

    private final AtomicReference<Instant> now = new AtomicReference<>(Instant.parse("2026-10-20T00:00:00Z"));
    private final Clock clock = new Clock() {
        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now.get(); }
    };

    @Test
    void skipsConfiguredModelsTheKeyCannotUse() {
        ModelChooser chooser = new ModelChooser("llama-3.1-8b-instant, openai/gpt-oss-20b",
            () -> Optional.of(Set.of("openai/gpt-oss-20b", "allam-2-7b")), clock);
        assertThat(chooser.choose()).contains("openai/gpt-oss-20b");
    }

    @Test
    void nothingUsableMeansNoCall() {
        ModelChooser chooser = new ModelChooser("llama-3.1-8b-instant", () -> Optional.of(Set.of("whisper-large-v3")), clock);
        assertThat(chooser.choose()).isEmpty();
    }

    @Test
    void anUnreachableModelListFallsBackToTheConfiguredOrder() {
        ModelChooser chooser = new ModelChooser("a, b", Optional::empty, clock);
        assertThat(chooser.choose()).contains("a");
    }

    @Test
    void aRejectedModelIsSkippedUntilTheNextRefresh() {
        ModelChooser chooser = new ModelChooser("a, b", Optional::empty, clock);
        assertThat(chooser.choose()).contains("a");
        now.set(now.get().plusSeconds(1));
        chooser.reject("a");
        assertThat(chooser.choose()).contains("b");
        chooser.reject("b");
        assertThat(chooser.choose()).isEmpty();

        now.set(now.get().plus(ModelChooser.FAILED_LOOKUP_TTL).plusSeconds(1));
        assertThat(chooser.choose()).contains("a");
    }

    @Test
    void theModelListIsCached() {
        AtomicInteger lookups = new AtomicInteger();
        ModelChooser chooser = new ModelChooser("a", () -> {
            lookups.incrementAndGet();
            return Optional.of(Set.of("a"));
        }, clock);
        chooser.choose();
        chooser.choose();
        assertThat(lookups).hasValue(1);

        now.set(now.get().plus(ModelChooser.TTL).plus(Duration.ofSeconds(1)));
        chooser.choose();
        assertThat(lookups).hasValue(2);
    }

    @Test
    void parsesACommaSeparatedPreferenceList() {
        assertThat(ModelChooser.parse(" a ,, b , a ")).containsExactly("a", "b");
        assertThat(ModelChooser.parse(null)).isEmpty();
    }
}
