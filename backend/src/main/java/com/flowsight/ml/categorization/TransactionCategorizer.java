package com.flowsight.ml.categorization;

import com.flowsight.entity.Transaction;
import com.flowsight.entity.TransactionCategory;
import com.flowsight.ml.MlCapability;
import com.flowsight.ml.MlClient;
import com.flowsight.ml.MlPrediction;
import com.flowsight.ml.PersonalizationService;
import com.flowsight.ml.PredictionContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Upgrades rule-categorized transactions to the ml-service categorizer's answer, blended with
 * the user's own reviewed transactions when personalization is on. Each row keeps its rules
 * result whenever the categorizer is off, in shadow mode, slow or failing (MlClient fallback),
 * so callers categorize with rules first and then call {@link #refine}.
 */
@Service
@RequiredArgsConstructor
public class TransactionCategorizer {

    private final MlClient               ml;
    private final PersonalizationService personalization;
    private final ReviewedHistory        history;

    /** Refines rows whose category source is RULES; other rows are left alone. Not persisted here. */
    public void refine(UUID userId, List<Transaction> transactions) {
        if (!ml.isActive(MlCapability.CATEGORIZER)) return;
        List<Transaction> pending = transactions.stream()
            .filter(t -> t.getCategorySource() == CategorySource.RULES)
            .toList();
        if (pending.isEmpty()) return;

        Boolean personalized = null;
        for (int start = 0; start < pending.size(); start += CategorizerRequest.MAX_ITEMS) {
            List<Transaction> chunk = pending.subList(start, Math.min(pending.size(), start + CategorizerRequest.MAX_ITEMS));
            List<CategorizerRequest.Item> items = chunk.stream().map(CategorizerRequest.Item::of).toList();
            MlPrediction<CategorizerResponse> prediction = ml.predict(MlCapability.CATEGORIZER,
                new CategorizerRequest(items), CategorizerResponse.class, PredictionContext.forUser(userId), () -> null);
            CategorizerResponse response = prediction.value();
            if (!prediction.fromModel() || response == null || response.predictions() == null
                || response.predictions().size() != chunk.size()) {
                continue;
            }
            if (personalized == null) personalized = personalization.isEnabled(userId);
            List<List<Neighbour>> neighbours = personalized
                ? history.nearest(userId, items)
                : items.stream().map(i -> List.<Neighbour>of()).toList();
            for (int i = 0; i < chunk.size(); i++) {
                apply(chunk.get(i), response.predictions().get(i), neighbours.get(i), prediction.modelVersion());
            }
        }
    }

    static void apply(Transaction tx, CategorizerResponse.Prediction prediction, List<Neighbour> neighbours,
                      String modelVersion) {
        List<CategoryAlternative> global = alternatives(prediction);
        if (global.isEmpty()) return;
        PersonalBlend.Result result = PersonalBlend.blend(global, neighbours);
        tx.setCategory(result.category());
        tx.setConfidenceScore(BigDecimal.valueOf(result.probability()).setScale(4, RoundingMode.HALF_UP));
        tx.setCategorySource(result.personal() ? CategorySource.PERSONAL : CategorySource.MODEL);
        tx.setCategoryModelVersion(modelVersion);
        tx.setCategoryAlternatives(result.alternatives());
    }

    // An unknown category from a newer model drops that entry; an unknown top answer keeps rules.
    private static List<CategoryAlternative> alternatives(CategorizerResponse.Prediction prediction) {
        if (parse(prediction.category()) == null || prediction.alternatives() == null) return List.of();
        List<CategoryAlternative> out = new ArrayList<>();
        for (CategorizerResponse.Alternative a : prediction.alternatives()) {
            TransactionCategory c = parse(a.category());
            if (c != null && Double.isFinite(a.probability())) {
                out.add(new CategoryAlternative(c, Math.max(0, Math.min(1, a.probability()))));
            }
        }
        if (out.isEmpty() || out.get(0).category() != parse(prediction.category())) return List.of();
        return List.copyOf(out);
    }

    private static TransactionCategory parse(String name) {
        if (name == null) return null;
        try {
            return TransactionCategory.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
