package com.flowsight.ml.recurring;

import com.flowsight.ml.MlCapability;
import com.flowsight.ml.MlClient;
import com.flowsight.ml.MlPrediction;
import com.flowsight.ml.MlProperties;
import com.flowsight.ml.PredictionContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Asks ml-service's recurring-scorer about a user's merchant groups. Empty whenever the rules
 * should be served instead: capability off or in shadow mode, ml-service slow, failing or
 * unreachable (MlClient fallback), or an answer that does not line up with the request.
 */
@Service
@RequiredArgsConstructor
public class RecurringScorer {

    private final MlClient     ml;
    private final MlProperties properties;

    public record Scores(List<RecurringScorerResponse.Score> scores, String modelVersion) {
    }

    /** True when the capability is switched on to serve (not only shadow). */
    public boolean serving() {
        return properties.configured() && properties.flags(MlCapability.RECURRING_SCORER).enabled();
    }

    public boolean active() {
        return ml.isActive(MlCapability.RECURRING_SCORER);
    }

    public Optional<Scores> score(UUID userId, RecurringScorerRequest request) {
        if (!active() || request.groups().isEmpty()) return Optional.empty();
        List<RecurringScorerResponse.Score> all = new ArrayList<>();
        String version = null;
        List<RecurringScorerRequest.Group> groups = request.groups();
        for (int start = 0; start < groups.size(); start += RecurringScorerRequest.MAX_GROUPS) {
            List<RecurringScorerRequest.Group> chunk =
                groups.subList(start, Math.min(groups.size(), start + RecurringScorerRequest.MAX_GROUPS));
            MlPrediction<RecurringScorerResponse> p = ml.predict(MlCapability.RECURRING_SCORER,
                new RecurringScorerRequest(request.asOf(), chunk), RecurringScorerResponse.class,
                PredictionContext.forUser(userId), () -> null);
            RecurringScorerResponse r = p.value();
            if (!p.fromModel() || r == null || r.scores() == null || r.scores().size() != chunk.size()) {
                return Optional.empty();
            }
            all.addAll(r.scores());
            version = p.modelVersion();
        }
        return Optional.of(new Scores(all, version));
    }
}
