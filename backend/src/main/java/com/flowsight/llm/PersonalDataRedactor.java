package com.flowsight.llm;

import com.flowsight.ocr.PiiRedactor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Masks personal identifiers in text bound for an external model: the user's own name and
 * e-mail, payment handles (name@bank), the counterparty of person-to-person transfers, and
 * everything PiiRedactor covers (phones, cards, tax IDs, long digit runs).
 */
public final class PersonalDataRedactor {

    public static final String MASK = "[redacted]";

    private static final Pattern HANDLE = Pattern.compile("[\\w.+-]+@[\\w.-]+");
    // Indian mobile written as two groups of five ("98450 12345")
    private static final Pattern MOBILE_IN = Pattern.compile("(?<![\\d.,])[6-9]\\d{4}[\\s-]\\d{5}(?![\\d.,])");
    private static final Pattern PERSON_RAIL =
        Pattern.compile("^\\s*(UPI|IMPS|NEFT|RTGS|MMT|P2P)\\b.*", Pattern.CASE_INSENSITIVE);
    private static final int MIN_NAME_PART = 3;

    private final List<Pattern> identifiers;

    private PersonalDataRedactor(List<Pattern> identifiers) {
        this.identifiers = identifiers;
    }

    /** identifiers: the user's full name and e-mail (null entries are ignored). */
    public static PersonalDataRedactor forIdentifiers(Collection<String> identifiers) {
        List<String> parts = new ArrayList<>();
        for (String id : identifiers) {
            if (id == null || id.isBlank()) continue;
            String v = id.trim();
            parts.add(v);
            int at = v.indexOf('@');
            // the e-mail's domain is not personal; its local part is
            String personal = at > 0 ? v.substring(0, at) : v;
            parts.add(personal);
            for (String p : personal.split("[\\s._-]+")) {
                if (p.length() >= MIN_NAME_PART) parts.add(p);
            }
        }
        parts.sort(Comparator.comparingInt(String::length).reversed());
        List<Pattern> patterns = parts.stream()
            .distinct()
            .map(p -> Pattern.compile("(?<![\\w])" + Pattern.quote(p) + "(?![\\w])",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE))
            .toList();
        return new PersonalDataRedactor(patterns);
    }

    public String redact(String text) {
        if (text == null || text.isEmpty()) return text;
        String out = HANDLE.matcher(text).replaceAll(MASK);
        out = MOBILE_IN.matcher(out).replaceAll(MASK);
        for (Pattern p : identifiers) out = p.matcher(out).replaceAll(MASK);
        return PiiRedactor.redact(out);
    }

    /** For counterparty labels (merchant names): a person-to-person transfer keeps only its rail. */
    public String redactLabel(String label) {
        if (label == null) return null;
        var m = PERSON_RAIL.matcher(label);
        if (m.matches()) return m.group(1).toUpperCase() + " transfer";
        return redact(label);
    }
}
