package com.flowsight.merchant;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.flowsight.ml.MlCapability;
import com.flowsight.ml.MlClient;
import com.flowsight.ml.MlPrediction;
import com.flowsight.ml.PredictionContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

// Nearest alias by character n-gram embedding (ml-service "merchant-embeddings"). Empty when
// the capability is off, ml-service fails, or the neighbour is not close enough.
@Component
public class MerchantEmbeddingMatcher {

    public record Match(String canonicalName, double similarity) {
    }

    record Request(String text) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Result(String canonical, Double similarity) {
        static final Result NONE = new Result(null, null);
    }

    private final MlClient ml;
    private final double   minSimilarity;

    @Autowired
    public MerchantEmbeddingMatcher(MlClient ml, @Value("${MERCHANT_EMBEDDING_MIN_SIMILARITY:0.75}") double minSimilarity) {
        this.ml = ml;
        this.minSimilarity = minSimilarity;
    }

    public static MerchantEmbeddingMatcher disabled() {
        return new MerchantEmbeddingMatcher(null, 1.0);
    }

    public Optional<Match> match(UUID userId, String text) {
        if (ml == null || text == null || text.isBlank() || !ml.isActive(MlCapability.MERCHANT_EMBEDDINGS)) {
            return Optional.empty();
        }
        MlPrediction<Result> p = ml.predict(MlCapability.MERCHANT_EMBEDDINGS, new Request(text), Result.class,
            PredictionContext.forUser(userId), () -> Result.NONE);
        Result r = p.value();
        if (!p.fromModel() || r == null || r.canonical() == null || r.canonical().isBlank()
            || r.similarity() == null || r.similarity() < minSimilarity) {
            return Optional.empty();
        }
        return Optional.of(new Match(r.canonical().trim(), Math.min(1.0, r.similarity())));
    }
}
