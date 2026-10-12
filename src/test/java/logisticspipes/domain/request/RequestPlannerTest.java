package logisticspipes.domain.request;

import static logisticspipes.testing.Fixtures.batch;
import static logisticspipes.testing.Fixtures.router;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import logisticspipes.domain.crafting.Recipe;
import logisticspipes.domain.item.ItemType;
import logisticspipes.domain.routing.RouterId;
import logisticspipes.testing.Fixtures.TestItem;
import org.junit.jupiter.api.Test;

class RequestPlannerTest {

    private static final RouterId REQUESTER = router(1);
    private static final RouterId NEAR = router(2);
    private static final RouterId FAR = router(3);
    private static final RouterId CRAFTER = router(4);
    private static final RouterId OTHER_CRAFTER = router(5);
    private static final RouterId SATELLITE = router(6);

    private static ItemType item(String name) {
        return new TestItem(name);
    }

    private static Stock stock(RouterId provider, String name, int count) {
        return new Stock(provider, Map.of(item(name), count));
    }

    private static Crafter crafter(RouterId router, Recipe recipe) {
        return new Crafter(router, recipe, router);
    }

    private static final Recipe PLANKS = new Recipe(List.of(batch("log", 1)), batch("planks", 4));
    private static final Recipe STICKS = new Recipe(List.of(batch("planks", 2)), batch("stick", 4));

    @Test
    void takesItemsFromStock() {
        RequestPlan plan = new RequestPlanner(List.of(stock(NEAR, "stone", 64)), List.of())
                .plan(REQUESTER, batch("stone", 10));

        assertThat(plan.complete()).isTrue();
        assertThat(plan.provisions()).containsExactly(new Provision(NEAR, REQUESTER, batch("stone", 10)));
        assertThat(plan.crafts()).isEmpty();
    }

    @Test
    void emptiesProvidersInTheGivenOrder() {
        RequestPlan plan = new RequestPlanner(List.of(stock(NEAR, "stone", 6), stock(FAR, "stone", 64)), List.of())
                .plan(REQUESTER, batch("stone", 10));

        assertThat(plan.provisions()).containsExactly(
                new Provision(NEAR, REQUESTER, batch("stone", 6)),
                new Provision(FAR, REQUESTER, batch("stone", 4)));
    }

    @Test
    void reportsWhatIsMissing() {
        RequestPlan plan = new RequestPlanner(List.of(stock(NEAR, "stone", 6)), List.of())
                .plan(REQUESTER, batch("stone", 10));

        assertThat(plan.complete()).isFalse();
        assertThat(plan.missing()).containsOnly(Map.entry(item("stone"), 4));
    }

    @Test
    void neverTakesFromTheDestinationItself() {
        RequestPlan plan = new RequestPlanner(List.of(stock(REQUESTER, "stone", 64)), List.of())
                .plan(REQUESTER, batch("stone", 10));

        assertThat(plan.missing()).containsOnly(Map.entry(item("stone"), 10));
    }

    @Test
    void craftsWhatIsNotInStock() {
        RequestPlan plan = new RequestPlanner(List.of(stock(NEAR, "log", 64)), List.of(crafter(CRAFTER, PLANKS)))
                .plan(REQUESTER, batch("planks", 8));

        assertThat(plan.complete()).isTrue();
        assertThat(plan.provisions()).containsExactly(new Provision(NEAR, CRAFTER, batch("log", 2)));
        assertThat(plan.crafts()).containsExactly(new CraftJob(CRAFTER, REQUESTER, batch("planks", 8)));
    }

    @Test
    void roundsCraftingRunsUp() {
        RequestPlan plan = new RequestPlanner(List.of(stock(NEAR, "log", 64)), List.of(crafter(CRAFTER, PLANKS)))
                .plan(REQUESTER, batch("planks", 5));

        assertThat(plan.provisions()).containsExactly(new Provision(NEAR, CRAFTER, batch("log", 2)));
        assertThat(plan.crafts()).containsExactly(new CraftJob(CRAFTER, REQUESTER, batch("planks", 5)));
    }

    @Test
    void usesStockBeforeCraftingTheRest() {
        RequestPlan plan = new RequestPlanner(
                List.of(new Stock(NEAR, Map.of(item("planks"), 3, item("log"), 64))),
                List.of(crafter(CRAFTER, PLANKS)))
                .plan(REQUESTER, batch("planks", 7));

        assertThat(plan.provisions()).containsExactly(
                new Provision(NEAR, REQUESTER, batch("planks", 3)),
                new Provision(NEAR, CRAFTER, batch("log", 1)));
        assertThat(plan.crafts()).containsExactly(new CraftJob(CRAFTER, REQUESTER, batch("planks", 4)));
    }

    @Test
    void craftsIngredientsRecursively() {
        RequestPlan plan = new RequestPlanner(List.of(stock(NEAR, "log", 64)),
                List.of(crafter(CRAFTER, PLANKS), crafter(OTHER_CRAFTER, STICKS)))
                .plan(REQUESTER, batch("stick", 8));

        assertThat(plan.complete()).isTrue();
        assertThat(plan.provisions()).containsExactly(new Provision(NEAR, CRAFTER, batch("log", 1)));
        assertThat(plan.crafts()).containsExactly(
                new CraftJob(CRAFTER, OTHER_CRAFTER, batch("planks", 4)),
                new CraftJob(OTHER_CRAFTER, REQUESTER, batch("stick", 8)));
    }

    @Test
    void sendsIngredientsToTheSatellite() {
        RequestPlan plan = new RequestPlanner(List.of(stock(NEAR, "log", 64)),
                List.of(new Crafter(CRAFTER, PLANKS, SATELLITE)))
                .plan(REQUESTER, batch("planks", 4));

        assertThat(plan.provisions()).containsExactly(new Provision(NEAR, SATELLITE, batch("log", 1)));
        assertThat(plan.crafts()).containsExactly(new CraftJob(CRAFTER, REQUESTER, batch("planks", 4)));
    }

    @Test
    void reportsMissingIngredientsInsteadOfTheCraftedItem() {
        RequestPlan plan = new RequestPlanner(List.of(stock(NEAR, "log", 1)), List.of(crafter(CRAFTER, PLANKS)))
                .plan(REQUESTER, batch("planks", 12));

        assertThat(plan.complete()).isFalse();
        assertThat(plan.missing()).containsOnly(Map.entry(item("log"), 2));
    }

    @Test
    void fallsBackToAnotherCrafterWhenIngredientsAreShort() {
        Recipe fromBamboo = new Recipe(List.of(batch("bamboo", 2)), batch("planks", 1));
        RequestPlan plan = new RequestPlanner(List.of(stock(NEAR, "bamboo", 64)),
                List.of(crafter(CRAFTER, PLANKS), crafter(OTHER_CRAFTER, fromBamboo)))
                .plan(REQUESTER, batch("planks", 3));

        assertThat(plan.complete()).isTrue();
        assertThat(plan.provisions()).containsExactly(new Provision(NEAR, OTHER_CRAFTER, batch("bamboo", 6)));
        assertThat(plan.crafts()).containsExactly(new CraftJob(OTHER_CRAFTER, REQUESTER, batch("planks", 3)));
    }

    @Test
    void sharedStockIsNotCountedTwice() {
        Recipe table = new Recipe(List.of(batch("planks", 4)), batch("table", 1));
        Recipe chest = new Recipe(List.of(batch("planks", 8), batch("table", 1)), batch("workbench chest", 1));
        RequestPlan plan = new RequestPlanner(List.of(stock(NEAR, "planks", 10)),
                List.of(crafter(CRAFTER, table), crafter(OTHER_CRAFTER, chest)))
                .plan(REQUESTER, batch("workbench chest", 1));

        assertThat(plan.complete()).isFalse();
        assertThat(plan.missing()).containsOnly(Map.entry(item("planks"), 2));
    }

    @Test
    void addsUpRepeatedInputsOfARecipe() {
        Recipe door = new Recipe(List.of(batch("planks", 2), batch("planks", 2), batch("planks", 2)), batch("door", 3));
        RequestPlan plan = new RequestPlanner(List.of(stock(NEAR, "planks", 64)), List.of(crafter(CRAFTER, door)))
                .plan(REQUESTER, batch("door", 3));

        assertThat(plan.provisions()).containsExactly(new Provision(NEAR, CRAFTER, batch("planks", 6)));
    }

    @Test
    void doesNotLoopOnRecipesThatNeedEachOther() {
        Recipe blockFromIngots = new Recipe(List.of(batch("ingot", 9)), batch("block", 1));
        Recipe ingotsFromBlock = new Recipe(List.of(batch("block", 1)), batch("ingot", 9));
        RequestPlan plan = new RequestPlanner(List.of(),
                List.of(crafter(CRAFTER, blockFromIngots), crafter(OTHER_CRAFTER, ingotsFromBlock)))
                .plan(REQUESTER, batch("block", 1));

        assertThat(plan.complete()).isFalse();
        assertThat(plan.missing()).containsOnlyKeys(item("block"));
    }
}
