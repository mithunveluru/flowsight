package com.flowsight.merchant;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Priority-ordered alias table matched on whole tokens: an alias fires only when its
 * tokens appear contiguously in the text (so "emi" never matches "emirates"), when a
 * multi-word alias appears written as one word ("youtubepremium"), or when the last word
 * of a multi-word alias starts a longer word ("anytime fit" in "anytime fitness"). The
 * first alias in priority order wins.
 */
public final class AliasIndex {

    public record Entry(String alias, String canonicalName, String canonicalKey, String region, int priority) {
    }

    private record Compiled(Entry entry, String padded, String compact, String[] words) {
    }

    private final List<Entry> entries;
    private final List<Compiled> compiled;
    private final Map<String, Entry> byCanonical = new HashMap<>();

    public AliasIndex(List<Entry> entries) {
        List<Entry> sorted = new ArrayList<>(entries);
        sorted.sort(Comparator.comparingInt(Entry::priority));
        this.entries = List.copyOf(sorted);
        List<Compiled> c = new ArrayList<>(sorted.size());
        for (Entry e : sorted) {
            String compact = e.alias().contains(" ") ? e.alias().replace(" ", "") : null;
            c.add(new Compiled(e, " " + e.alias() + " ", compact, e.alias().split(" ")));
            byCanonical.putIfAbsent(e.canonicalName().toLowerCase(Locale.ROOT), e);
        }
        this.compiled = List.copyOf(c);
    }

    /** An entry built from raw alias text; the alias is stored in match form. */
    public static Entry entry(String region, String alias, String canonicalName, int priority) {
        return new Entry(MerchantText.matchText(alias), canonicalName, keyOf(canonicalName), region, priority);
    }

    // Short canonical keys would be dropped by recurring detection's minimum key length.
    public static String keyOf(String canonicalName) {
        String key = canonicalName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        return key.length() < 3 ? "brand-" + key : key;
    }

    public List<Entry> entries() {
        return entries;
    }

    /** The entry whose canonical name is exactly this text (ignoring case), so stored names map to themselves. */
    public Entry canonical(String name) {
        return name == null ? null : byCanonical.get(name.trim().toLowerCase(Locale.ROOT));
    }

    /** text must already be in {@link MerchantText#matchText} form. */
    public Entry match(String text) {
        if (text == null || text.isEmpty()) return null;
        String padded = " " + text + " ";
        String[] tokens = text.split(" ");
        for (Compiled c : compiled) {
            if (padded.contains(c.padded())) return c.entry();
            if (c.compact() != null && (containsToken(tokens, c.compact()) || lastWordPrefix(tokens, c.words()))) {
                return c.entry();
            }
        }
        return null;
    }

    private static boolean containsToken(String[] tokens, String token) {
        for (String t : tokens) {
            if (t.equals(token)) return true;
        }
        return false;
    }

    // Multi-word aliases were written as prefixes ("adani electric", "anytime fit"): the last
    // word may begin a longer word, every earlier word must match exactly.
    private static boolean lastWordPrefix(String[] tokens, String[] words) {
        int last = words.length - 1;
        // one- and two-letter endings ("at t", "pg e") would start too many words
        if (words[last].length() < 3) return false;
        for (int i = 0; i + words.length <= tokens.length; i++) {
            boolean match = tokens[i + last].startsWith(words[last]);
            for (int j = 0; match && j < last; j++) match = tokens[i + j].equals(words[j]);
            if (match) return true;
        }
        return false;
    }
}
