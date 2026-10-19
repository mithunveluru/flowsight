package com.flowsight.merchant;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Where the resolver looks up global aliases: exact (token) matches and trigram neighbours. */
public interface AliasCatalog {

    int MAX_SIMILAR = 10;
    // trigram similarity is noise on very short strings
    int MIN_FUZZY_LENGTH = 4;

    AliasIndex index();

    /** Aliases whose trigram similarity to any of the windows is at least minSimilarity, best first. */
    List<Similar> similar(List<String> windows, double minSimilarity);

    record Similar(String text, String canonicalName, String canonicalKey, String window, double similarity) {
    }

    static AliasCatalog inMemory(AliasIndex index) {
        return new AliasCatalog() {
            @Override
            public AliasIndex index() {
                return index;
            }

            @Override
            public List<Similar> similar(List<String> windows, double minSimilarity) {
                return scan(index, windows, minSimilarity);
            }
        };
    }

    static List<Similar> scan(AliasIndex index, List<String> windows, double minSimilarity) {
        List<Similar> out = new ArrayList<>();
        for (AliasIndex.Entry e : index.entries()) {
            if (e.alias().length() < MIN_FUZZY_LENGTH) continue;
            for (String w : windows) {
                double sim = Trigram.similarity(e.alias(), w);
                if (sim >= minSimilarity) out.add(new Similar(e.alias(), e.canonicalName(), e.canonicalKey(), w, sim));
            }
        }
        out.sort(Comparator.comparingDouble(Similar::similarity).reversed());
        return out.size() > MAX_SIMILAR ? List.copyOf(out.subList(0, MAX_SIMILAR)) : out;
    }
}
