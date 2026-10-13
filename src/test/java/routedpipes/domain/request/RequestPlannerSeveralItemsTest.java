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

class RequestPlannerSeveralItemsTest {

    private static final RouterId REQUESTER = router(1);
    private static final RouterId PROVIDER = router(2);
    private static final RouterId CRAFTER = router(3);

    private static ItemType item(String name) {
        return new TestItem(name);
    }

    @Test
    void plansEveryItemOfTheList() {
        RequestPlan plan = new RequestPlanner(List.of(new Stock(PROVIDER, Map.of(item("stone"), 10, item("coal"), 10))), List.of())
                .plan(REQUESTER, List.of(batch("stone", 3), batch("coal", 2)));

        assertThat(plan.complete()).isTrue();
        assertThat(plan.provisions()).containsExactly(
                new Provision(PROVIDER, REQUESTER, batch("stone", 3)),
                new Provision(PROVIDER, REQUESTER, batch("coal", 2)));
    }

    @Test
    void nothingWantedIsACompletePlanThatMovesNothing() {
        RequestPlan plan = new RequestPlanner(List.of(new Stock(PROVIDER, Map.of(item("stone"), 10))), List.of())
                .plan(REQUESTER, List.of());

        assertThat(plan.complete()).isTrue();
        assertThat(plan.provisions()).isEmpty();
        assertThat(plan.crafts()).isEmpty();
    }

    @Test
    void reportsEveryItemThatIsShort() {
        RequestPlan plan = new RequestPlanner(List.of(new Stock(PROVIDER, Map.of(item("stone"), 2))), List.of())
                .plan(REQUESTER, List.of(batch("stone", 3), batch("coal", 2)));

        assertThat(plan.missing()).containsOnly(Map.entry(item("stone"), 1), Map.entry(item("coal"), 2));
    }

    // planks are wanted as they are and as the ingredient of the sticks, there is only stock for one of the two
    @Test
    void laterItemsOnlyGetWhatEarlierOnesLeft() {
        Recipe sticks = new Recipe(List.of(batch("planks", 2)), batch("stick", 4));
        RequestPlanner planner = new RequestPlanner(List.of(new Stock(PROVIDER, Map.of(item("planks"), 3))),
                List.of(Crafter.of(CRAFTER, sticks)));

        RequestPlan plan = planner.plan(REQUESTER, List.of(batch("planks", 2), batch("stick", 4)));

        assertThat(plan.missing()).containsOnly(Map.entry(item("planks"), 1));
        assertThat(planner.plan(REQUESTER, List.of(batch("planks", 1), batch("stick", 4))).complete()).isTrue();
    }

    @Test
    void anItemThatIsShortDoesNotStopTheNextOneFromBeingCrafted() {
        Recipe planks = new Recipe(List.of(batch("log", 1)), batch("planks", 4));
        RequestPlan plan = new RequestPlanner(List.of(new Stock(PROVIDER, Map.of(item("log"), 1))), List.of(Crafter.of(CRAFTER, planks)))
                .plan(REQUESTER, List.of(batch("coal", 1), batch("planks", 4)));

        assertThat(plan.missing()).containsOnly(Map.entry(item("coal"), 1));
        assertThat(plan.crafts()).containsExactly(new CraftJob(CRAFTER, REQUESTER, batch("planks", 4)));
    }
}
