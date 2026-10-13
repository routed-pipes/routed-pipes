package routedpipes.domain.request;

import static routedpipes.testing.Fixtures.batch;
import static routedpipes.testing.Fixtures.router;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import routedpipes.domain.crafting.Recipe;
import routedpipes.domain.item.ItemType;
import routedpipes.domain.routing.RouterId;
import routedpipes.testing.Fixtures.TestItem;
import org.junit.jupiter.api.Test;

class RequestSummaryTest {

    private static final RouterId REQUESTER = router(1);
    private static final RouterId NEAR = router(2);
    private static final RouterId FAR = router(3);
    private static final RouterId SAWMILL = router(4);
    private static final RouterId WORKBENCH = router(5);
    private static final int PROVIDED = 2;
    private static final int CRAFTED = 20;

    private static ItemType item(String name) {
        return new TestItem(name);
    }

    private static RequestSummary summary(RequestPlan plan) {
        return RequestSummary.of(plan, PROVIDED, CRAFTED);
    }

    @Test
    void anEmptyPlanComesToNothing() {
        RequestSummary summary = summary(new RequestPlan(List.of(), List.of(), Map.of()));

        assertThat(summary.fromStock()).isEmpty();
        assertThat(summary.crafted()).isEmpty();
        assertThat(summary.missing()).isEmpty();
        assertThat(summary.energy()).isZero();
        assertThat(summary.complete()).isTrue();
    }

    @Test
    void whatSeveralProvidersHandOutIsAddedUpPerItem() {
        RequestPlan plan = new RequestPlan(List.of(
                new Provision(NEAR, REQUESTER, batch("stone", 40)),
                new Provision(FAR, REQUESTER, batch("stone", 24)),
                new Provision(FAR, REQUESTER, batch("coal", 3))), List.of(), Map.of());

        RequestSummary summary = summary(plan);

        assertThat(summary.fromStock()).containsExactly(Map.entry(item("stone"), 64), Map.entry(item("coal"), 3));
        assertThat(summary.energy()).isEqualTo(67 * PROVIDED);
    }

    @Test
    void whatSeveralCraftersMakeIsAddedUpPerItem() {
        RequestPlan plan = new RequestPlan(List.of(), List.of(
                new CraftJob(SAWMILL, REQUESTER, batch("planks", 8)),
                new CraftJob(WORKBENCH, REQUESTER, batch("planks", 4))), Map.of());

        RequestSummary summary = summary(plan);

        assertThat(summary.crafted()).containsExactly(Map.entry(item("planks"), 12));
        assertThat(summary.energy()).isEqualTo(12 * CRAFTED);
    }

    @Test
    void theIngredientsOfATreeOfCraftsAllShowUp() {
        // sticks out of planks out of logs, with some planks already lying around
        RequestPlanner planner = new RequestPlanner(
                List.of(new Stock(NEAR, Map.of(item("log"), 10, item("planks"), 2))),
                List.of(Crafter.of(SAWMILL, new Recipe(List.of(batch("log", 1)), batch("planks", 4))),
                        Crafter.of(WORKBENCH, new Recipe(List.of(batch("planks", 2)), batch("stick", 4)))));

        RequestSummary summary = summary(planner.plan(REQUESTER, batch("stick", 16)));

        // four runs of sticks need eight planks: two from stock, the other six out of two logs
        assertThat(summary.fromStock()).containsOnly(Map.entry(item("planks"), 2), Map.entry(item("log"), 2));
        assertThat(summary.crafted()).containsOnly(Map.entry(item("stick"), 16), Map.entry(item("planks"), 6));
        assertThat(summary.missing()).isEmpty();
        assertThat(summary.energy()).isEqualTo(4 * PROVIDED + 22 * CRAFTED);
    }

    @Test
    void whatCannotBeHadIsListedAsMissing() {
        RequestPlanner planner = new RequestPlanner(List.of(new Stock(NEAR, Map.of(item("stone"), 5))), List.of());

        RequestSummary summary = summary(planner.plan(REQUESTER, batch("stone", 8)));

        assertThat(summary.fromStock()).containsExactly(Map.entry(item("stone"), 5));
        assertThat(summary.missing()).containsExactly(Map.entry(item("stone"), 3));
        assertThat(summary.complete()).isFalse();
    }

    @Test
    void largeRequestsDoNotOverflowTheEnergy() {
        RequestPlan plan = new RequestPlan(List.of(), List.of(new CraftJob(SAWMILL, REQUESTER, batch("planks", Integer.MAX_VALUE))),
                Map.of());

        assertThat(summary(plan).energy()).isEqualTo((long) Integer.MAX_VALUE * CRAFTED);
    }
}
