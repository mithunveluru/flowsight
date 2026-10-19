package com.flowsight.ml.categorization;

import com.flowsight.entity.TransactionCategory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Mixes the global model's probabilities with the categories of the user's similar reviewed
 * transactions: p = (1 - w) * global + w * neighbours, where w = s / (s + PRIOR) and s is the
 * summed similarity of the neighbours that count. One identical reviewed row gets half the say.
 */
public final class PersonalBlend {

    static final double MIN_SIMILARITY = 0.5;
    static final double PRIOR = 1.0;

    // the categories the model can return; UNCATEGORIZED is a rules-only answer
    private static final List<TransactionCategory> LABELS = Arrays.stream(TransactionCategory.values())
        .filter(c -> c != TransactionCategory.UNCATEGORIZED).toList();

    private PersonalBlend() {
    }

    public record Result(TransactionCategory category, double probability, List<CategoryAlternative> alternatives,
                         boolean personal) {
    }

    /** global: the model's top categories, best first (the rest of the mass is spread evenly). */
    public static Result blend(List<CategoryAlternative> global, List<Neighbour> neighbours) {
        Map<TransactionCategory, Double> votes = new EnumMap<>(TransactionCategory.class);
        double weight = 0;
        for (Neighbour n : neighbours) {
            if (n.similarity() < MIN_SIMILARITY || n.category() == TransactionCategory.UNCATEGORIZED) continue;
            votes.merge(n.category(), n.similarity(), Double::sum);
            weight += n.similarity();
        }
        if (weight == 0) {
            CategoryAlternative top = global.get(0);
            return new Result(top.category(), top.probability(), global, false);
        }

        Map<TransactionCategory, Double> p = new EnumMap<>(TransactionCategory.class);
        double listed = 0;
        for (CategoryAlternative a : global) {
            p.put(a.category(), a.probability());
            listed += a.probability();
        }
        long unlisted = LABELS.stream().filter(c -> !p.containsKey(c)).count();
        double rest = unlisted == 0 ? 0 : Math.max(0, 1 - listed) / unlisted;
        double w = weight / (weight + PRIOR);
        List<CategoryAlternative> mixed = new ArrayList<>();
        for (TransactionCategory c : LABELS) {
            double g = p.getOrDefault(c, rest);
            mixed.add(new CategoryAlternative(c, (1 - w) * g + w * votes.getOrDefault(c, 0.0) / weight));
        }
        // stable order: probability, then the category's declaration order
        mixed.sort(Comparator.comparingDouble(CategoryAlternative::probability).reversed());
        List<CategoryAlternative> top = List.copyOf(mixed.subList(0, Math.min(3, mixed.size())));
        return new Result(top.get(0).category(), top.get(0).probability(), top, true);
    }
}
