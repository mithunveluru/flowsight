package com.flowsight.ml.categorization;

import com.flowsight.entity.TransactionCategory;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.flowsight.entity.TransactionCategory.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class PersonalBlendTest {

    private static final List<CategoryAlternative> GLOBAL = List.of(
        new CategoryAlternative(FOOD_DINING, 0.70),
        new CategoryAlternative(GROCERIES, 0.20),
        new CategoryAlternative(SHOPPING, 0.05));

    @Test
    void withoutNeighboursTheGlobalAnswerStands() {
        PersonalBlend.Result r = PersonalBlend.blend(GLOBAL, List.of());

        assertThat(r.personal()).isFalse();
        assertThat(r.category()).isEqualTo(FOOD_DINING);
        assertThat(r.probability()).isEqualTo(0.70);
        assertThat(r.alternatives()).isEqualTo(GLOBAL);
    }

    @Test
    void weakNeighboursAreIgnored() {
        PersonalBlend.Result r = PersonalBlend.blend(GLOBAL, List.of(new Neighbour(GROCERIES, 0.49)));

        assertThat(r.personal()).isFalse();
        assertThat(r.category()).isEqualTo(FOOD_DINING);
    }

    @Test
    void oneIdenticalReviewedRowGetsHalfTheSay() {
        PersonalBlend.Result r = PersonalBlend.blend(GLOBAL, List.of(new Neighbour(GROCERIES, 1.0)));

        assertThat(r.personal()).isTrue();
        assertThat(r.category()).isEqualTo(GROCERIES);
        assertThat(r.probability()).isCloseTo(0.5 * 0.20 + 0.5, within(1e-9));
        assertThat(r.alternatives().get(1)).isEqualTo(new CategoryAlternative(FOOD_DINING, 0.35));
    }

    @Test
    void aSingleLooseMatchDoesNotOverturnAConfidentModel() {
        PersonalBlend.Result r = PersonalBlend.blend(GLOBAL, List.of(new Neighbour(TRAVEL, 0.55)));

        assertThat(r.personal()).isTrue();
        assertThat(r.category()).isEqualTo(FOOD_DINING);
        assertThat(r.alternatives()).extracting(CategoryAlternative::category).contains(TRAVEL);
    }

    @Test
    void probabilitiesStayADistributionAndTiesAreStable() {
        List<CategoryAlternative> flat = List.of(
            new CategoryAlternative(SHOPPING, 0.1), new CategoryAlternative(OTHER, 0.1),
            new CategoryAlternative(FOOD_DINING, 0.1));
        List<Neighbour> split = List.of(new Neighbour(TRAVEL, 0.8), new Neighbour(GROCERIES, 0.8));

        PersonalBlend.Result first = PersonalBlend.blend(flat, split);

        assertThat(first.category()).isEqualTo(GROCERIES);   // GROCERIES is declared before TRAVEL
        assertThat(first.alternatives()).extracting(CategoryAlternative::category).containsExactly(GROCERIES, TRAVEL, FOOD_DINING);
        assertThat(PersonalBlend.blend(flat, split)).isEqualTo(first);
        double top3 = first.alternatives().stream().mapToDouble(CategoryAlternative::probability).sum();
        assertThat(top3).isLessThanOrEqualTo(1.0 + 1e-9);
        assertThat(first.alternatives()).noneMatch(a -> a.category() == TransactionCategory.UNCATEGORIZED);
    }
}
