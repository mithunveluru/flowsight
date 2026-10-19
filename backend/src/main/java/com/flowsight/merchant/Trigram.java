package com.flowsight.merchant;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Trigram similarity computed the way Postgres pg_trgm's similarity() does it: lower-cased
 * words of letters and digits, each padded with two leading spaces and one trailing space,
 * and |shared| / |union| over the distinct trigrams. Used where rows are already in memory.
 */
public final class Trigram {

    private Trigram() {
    }

    public static double similarity(String a, String b) {
        Set<String> ta = trigrams(a);
        Set<String> tb = trigrams(b);
        if (ta.isEmpty() || tb.isEmpty()) return 0.0;
        int shared = 0;
        for (String t : ta) if (tb.contains(t)) shared++;
        return (double) shared / (ta.size() + tb.size() - shared);
    }

    static Set<String> trigrams(String s) {
        Set<String> out = new HashSet<>();
        if (s == null) return out;
        for (String word : s.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+")) {
            if (word.isEmpty()) continue;
            String padded = "  " + word + " ";
            int[] cps = padded.codePoints().toArray();
            for (int i = 0; i + 3 <= cps.length; i++) out.add(new String(cps, i, 3));
        }
        return out;
    }

    /** 1 - Levenshtein distance / longer length. */
    public static double editSimilarity(String a, String b) {
        int n = a.length();
        int m = b.length();
        if (n == 0 && m == 0) return 1.0;
        int[] prev = new int[m + 1];
        int[] cur = new int[m + 1];
        for (int j = 0; j <= m; j++) prev[j] = j;
        for (int i = 1; i <= n; i++) {
            cur[0] = i;
            for (int j = 1; j <= m; j++) {
                int cost = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                cur[j] = Math.min(Math.min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost);
            }
            int[] t = prev;
            prev = cur;
            cur = t;
        }
        return 1.0 - (double) prev[m] / Math.max(n, m);
    }
}
