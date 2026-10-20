package com.flowsight.llm;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

/**
 * The only facts a piece of generated prose may state. Keys are stable snake_case names;
 * numbers keep their kind so the grounding check can tell a percentage from an amount.
 */
public final class FactSet {

    public enum Kind { AMOUNT, PERCENT, COUNT, DATE, TEXT }

    public record Fact(String key, Kind kind, Object value) {
    }

    private final Map<String, Fact> facts = new LinkedHashMap<>();

    public FactSet amount(String key, BigDecimal value) {
        if (value != null) put(key, Kind.AMOUNT, value.setScale(2, RoundingMode.HALF_UP).stripTrailingZeros());
        return this;
    }

    public FactSet amount(String key, double value) {
        return amount(key, BigDecimal.valueOf(value));
    }

    public FactSet percent(String key, double value) {
        put(key, Kind.PERCENT, BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).stripTrailingZeros());
        return this;
    }

    public FactSet count(String key, long value) {
        put(key, Kind.COUNT, BigDecimal.valueOf(value));
        return this;
    }

    public FactSet date(String key, LocalDate value) {
        if (value != null) put(key, Kind.DATE, value);
        return this;
    }

    public FactSet text(String key, String value) {
        if (value != null && !value.isBlank()) put(key, Kind.TEXT, value.trim());
        return this;
    }

    /** Copies every fact of another set under prefix + "." + key. */
    public FactSet include(String prefix, FactSet other) {
        for (Fact f : other.facts.values()) put(prefix + "." + f.key(), f.kind(), f.value());
        return this;
    }

    public Object get(String key) {
        Fact f = facts.get(key);
        return f == null ? null : f.value();
    }

    public Collection<Fact> all() {
        return facts.values();
    }

    public boolean isEmpty() {
        return facts.isEmpty();
    }

    /** Text facts rewritten (for redaction); numbers and dates unchanged. */
    public FactSet mapText(UnaryOperator<String> fn) {
        FactSet out = new FactSet();
        for (Fact f : facts.values()) {
            out.facts.put(f.key(), f.kind() == Kind.TEXT ? new Fact(f.key(), Kind.TEXT, fn.apply((String) f.value())) : f);
        }
        return out;
    }

    /** Plain JSON-able view: numbers as plain decimals, dates ISO, text as is. */
    public Map<String, Object> asMap() {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Fact f : facts.values()) {
            Object v = f.value();
            if (v instanceof BigDecimal bd) {
                out.put(f.key(), bd.scale() <= 0 ? bd.toBigInteger() : bd);
            } else {
                out.put(f.key(), v.toString());
            }
        }
        return out;
    }

    List<BigDecimal> numbers(Kind kind) {
        List<BigDecimal> out = new ArrayList<>();
        for (Fact f : facts.values()) {
            if (f.kind() == kind) out.add(((BigDecimal) f.value()).abs());
        }
        return out;
    }

    List<LocalDate> dates() {
        List<LocalDate> out = new ArrayList<>();
        for (Fact f : facts.values()) {
            if (f.kind() == Kind.DATE) out.add((LocalDate) f.value());
        }
        return out;
    }

    List<String> texts() {
        List<String> out = new ArrayList<>();
        for (Fact f : facts.values()) {
            if (f.kind() == Kind.TEXT) out.add((String) f.value());
        }
        return out;
    }

    private void put(String key, Kind kind, Object value) {
        facts.put(key, new Fact(key, kind, value));
    }
}
