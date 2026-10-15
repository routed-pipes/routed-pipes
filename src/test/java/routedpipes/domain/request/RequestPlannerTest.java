package routedpipes.domain.request;

import static routedpipes.testing.Fixtures.batch;
import static routedpipes.testing.Fixtures.router;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import routedpipes.domain.crafting.Recipe;
import routedpipes.domain.item.ItemType;
import routedpipes.domain.routing.RouterId;
import routedpipes.testing.Fixtures.TestItem;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

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
        return Crafter.of(router, recipe);
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
    void sendsOnlyTheSatellitePartOfARecipeToTheSatellite() {
        Recipe smelting = new Recipe(List.of(batch("coal", 1), batch("ore", 8)), batch("ingot", 8));
        Crafter furnace = Crafter.of(CRAFTER, smelting, SATELLITE, Map.of(item("ore"), 8), 0);
        RequestPlan plan = new RequestPlanner(List.of(new Stock(NEAR, Map.of(item("coal"), 64, item("ore"), 64))), List.of(furnace))
                .plan(REQUESTER, batch("ingot", 16));

        assertThat(plan.provisions()).containsExactlyInAnyOrder(
                new Provision(NEAR, CRAFTER, batch("coal", 2)),
                new Provision(NEAR, SATELLITE, batch("ore", 16)));
        assertThat(plan.crafts()).containsExactly(new CraftJob(CRAFTER, REQUESTER, batch("ingot", 16)));
    }

    @Test
    void splitsAnInputBetweenPipeAndSatelliteWhenBothListIt() {
        Recipe recipe = new Recipe(List.of(batch("log", 3)), batch("planks", 4));
        Crafter crafter = Crafter.of(CRAFTER, recipe, SATELLITE, Map.of(item("log"), 1), 0);

        assertThat(crafter.ownInputs()).containsOnly(Map.entry(item("log"), 2));
        assertThat(crafter.satelliteInputs().get(SATELLITE)).containsOnly(Map.entry(item("log"), 1));
    }

    @Test
    void usesCraftersInTheGivenOrder() {
        RequestPlan plan = new RequestPlanner(List.of(stock(NEAR, "log", 64)),
                List.of(crafter(OTHER_CRAFTER, PLANKS), crafter(CRAFTER, PLANKS)))
                .plan(REQUESTER, batch("planks", 4));

        assertThat(plan.crafts()).containsExactly(new CraftJob(OTHER_CRAFTER, REQUESTER, batch("planks", 4)));
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

    @Test
    void sharesWorkBetweenCraftersOfTheSamePriority() {
        RequestPlan plan = new RequestPlanner(List.of(stock(NEAR, "log", 64)),
                List.of(crafter(CRAFTER, PLANKS), crafter(OTHER_CRAFTER, PLANKS)))
                .plan(REQUESTER, batch("planks", 16));

        assertThat(plan.crafts()).containsExactlyInAnyOrder(new CraftJob(CRAFTER, REQUESTER, batch("planks", 8)),
                new CraftJob(OTHER_CRAFTER, REQUESTER, batch("planks", 8)));
    }

    @Test
    void fillsTheLeastBusyCrafterUpToTheOthersFirst() {
        RequestPlan plan = new RequestPlanner(List.of(stock(NEAR, "log", 64)),
                List.of(crafter(CRAFTER, PLANKS).withBacklog(8), crafter(OTHER_CRAFTER, PLANKS)))
                .plan(REQUESTER, batch("planks", 16));

        assertThat(plan.crafts()).containsExactlyInAnyOrder(new CraftJob(CRAFTER, REQUESTER, batch("planks", 4)),
                new CraftJob(OTHER_CRAFTER, REQUESTER, batch("planks", 12)));
    }

    @Test
    void usesUpTheHigherPriorityBeforeAskingAnyoneElse() {
        Recipe fromBamboo = new Recipe(List.of(batch("bamboo", 2)), batch("planks", 1));
        RequestPlan plan = new RequestPlanner(List.of(stock(NEAR, "log", 2), stock(FAR, "bamboo", 64)),
                List.of(crafter(OTHER_CRAFTER, fromBamboo), crafter(CRAFTER, PLANKS).withPriority(1)))
                .plan(REQUESTER, batch("planks", 12));

        assertThat(plan.complete()).isTrue();
        assertThat(plan.crafts()).containsExactlyInAnyOrder(new CraftJob(CRAFTER, REQUESTER, batch("planks", 8)),
                new CraftJob(OTHER_CRAFTER, REQUESTER, batch("planks", 4)));
    }

    @Test
    void plansTheRunsThereAreIngredientsForEvenWhenNotAllCanBeHad() {
        RequestPlan plan = new RequestPlanner(List.of(stock(NEAR, "log", 2)), List.of(crafter(CRAFTER, PLANKS)))
                .plan(REQUESTER, batch("planks", 20));

        assertThat(plan.complete()).isFalse();
        assertThat(plan.crafts()).containsExactly(new CraftJob(CRAFTER, REQUESTER, batch("planks", 8)));
        assertThat(plan.provisions()).containsExactly(new Provision(NEAR, CRAFTER, batch("log", 2)));
        assertThat(plan.missing()).containsOnly(Map.entry(item("log"), 3));
    }

    @Test
    void sendsEveryIngredientToTheSatelliteSetForIt() {
        Recipe alloy = new Recipe(List.of(batch("ore", 2), batch("coal", 1), batch("sand", 1)), batch("alloy", 1));
        Crafter smelter = Crafter.of(CRAFTER, alloy, Map.of(SATELLITE, Map.of(item("ore"), 2), FAR, Map.of(item("coal"), 1)), 0);
        RequestPlan plan = new RequestPlanner(List.of(stock(NEAR, "ore", 8), stock(NEAR, "coal", 8), stock(NEAR, "sand", 8)),
                List.of(smelter)).plan(REQUESTER, batch("alloy", 2));

        assertThat(plan.complete()).isTrue();
        assertThat(plan.provisions()).containsExactlyInAnyOrder(new Provision(NEAR, SATELLITE, batch("ore", 4)),
                new Provision(NEAR, FAR, batch("coal", 2)), new Provision(NEAR, CRAFTER, batch("sand", 2)));
    }

    @Test
    void aHugeRequestReportsWhatIsMissingInsteadOfOverflowing() {
        RequestPlan plan = new RequestPlanner(List.of(stock(NEAR, "log", 64)), List.of(crafter(CRAFTER, PLANKS)))
                .plan(REQUESTER, batch("planks", Integer.MAX_VALUE));

        assertThat(plan.complete()).isFalse();
        assertThat(plan.missing().values()).allMatch(count -> count > 0);
    }

    @Test
    void ingredientsOfAHugeRequestStayPositive() {
        Recipe dense = new Recipe(List.of(batch("log", 1000)), batch("block", 1));
        RequestPlan plan = new RequestPlanner(List.of(stock(NEAR, "log", 64)), List.of(crafter(CRAFTER, dense)))
                .plan(REQUESTER, batch("block", 5_000_000));

        assertThat(plan.complete()).isFalse();
        assertThat(plan.missing().values()).allMatch(count -> count > 0);
    }

    // every part is made of two of the part below it, with that many crafters for each, part0 is the raw material
    private static List<Crafter> chain(int depth, int craftersPerPart) {
        List<Crafter> crafters = new ArrayList<>();
        for (int level = 1; level <= depth; level++) {
            Recipe recipe = new Recipe(List.of(batch("part" + (level - 1), 2)), batch("part" + level, 1));
            for (int copy = 0; copy < craftersPerPart; copy++) {
                crafters.add(crafter(router(100 + level * 10 + copy), recipe));
            }
        }
        return crafters;
    }

    // a base full of providers that hold other things, and one with the raw material
    private static List<Stock> bigBase(int raw) {
        List<Stock> stock = new ArrayList<>();
        for (int provider = 0; provider < 1600; provider++) {
            stock.add(stock(router(1000 + provider), "other" + provider, 64));
        }
        stock.add(stock(NEAR, "part0", raw));
        return stock;
    }

    // used to try every number of runs again on every level, which took longer than anyone would wait
    @Test
    @Timeout(30)
    void aLongChainThatRunsShortOfRawMaterialIsPlannedQuickly() {
        // enough for 5 of the 64, and a bit
        RequestPlan plan = new RequestPlanner(bigBase(4096 * 5 + 100), chain(12, 1)).plan(REQUESTER, batch("part12", 64));

        assertThat(plan.crafts()).contains(new CraftJob(router(220), REQUESTER, batch("part12", 5)));
        assertThat(plan.missing()).containsExactly(Map.entry(item("part0"), 59 * 4096 - 100));
    }

    @Test
    @Timeout(30)
    void aLongChainWithTwoCraftersForEveryPartIsPlannedQuicklyToo() {
        RequestPlan plan = new RequestPlanner(bigBase(4096 * 5 + 100), chain(12, 2)).plan(REQUESTER, batch("part12", 64));

        assertThat(plan.crafts().stream().filter(job -> job.output().type().equals(item("part12")))
                .mapToInt(job -> job.output().count()).sum()).isEqualTo(5);
        assertThat(plan.missing()).containsExactly(Map.entry(item("part0"), 59 * 4096 - 100));
    }

    // two ways to make every part that need different things, so neither can stand in for the other
    // how many runs each could do is then asked level by level, and the planner has to stop asking at some point
    @Test
    @Timeout(30)
    void aLongChainWithTwoRecipesForEveryPartGivesUpOnTheExactAmounts() {
        List<Crafter> crafters = new ArrayList<>();
        for (int level = 1; level <= 12; level++) {
            crafters.add(crafter(router(100 + level * 10), new Recipe(List.of(batch("part" + (level - 1), 2)), batch("part" + level, 1))));
            crafters.add(crafter(router(101 + level * 10),
                    new Recipe(List.of(batch("part" + (level - 1), 2), batch("flux", 1)), batch("part" + level, 1))));
        }
        List<Stock> stock = bigBase(4096 * 5 + 100);
        stock.add(stock(FAR, "flux", 1_000_000));

        RequestPlan plan = new RequestPlanner(stock, crafters).plan(REQUESTER, batch("part12", 64));

        assertThat(plan.complete()).isFalse();
        // whatever it settled for, it does not hand out more than there is
        assertThat(plan.provisions().stream().filter(provision -> provision.batch().type().equals(item("part0")))
                .mapToInt(provision -> provision.batch().count()).sum()).isLessThanOrEqualTo(4096 * 5 + 100);
        int made = plan.crafts().stream().filter(job -> job.output().type().equals(item("part12")))
                .mapToInt(job -> job.output().count()).sum();
        assertThat(made).isLessThanOrEqualTo(5);
    }

    private static final Recipe PROGRAMMED = new Recipe(List.of(batch("chip", 1)), batch("pipe", 1), List.of(batch("programmer", 1)));

    @Test
    void bringsAKeptIngredientOnceForAllRuns() {
        RequestPlan plan = new RequestPlanner(List.of(new Stock(NEAR, Map.of(item("chip"), 64, item("programmer"), 5))),
                List.of(crafter(CRAFTER, PROGRAMMED)))
                .plan(REQUESTER, batch("pipe", 6));

        assertThat(plan.complete()).isTrue();
        assertThat(plan.provisions()).containsExactlyInAnyOrder(
                new Provision(NEAR, CRAFTER, batch("programmer", 1)),
                new Provision(NEAR, CRAFTER, batch("chip", 6)));
    }

    @Test
    void aMissingKeptIngredientIsReportedOnce() {
        RequestPlan plan = new RequestPlanner(List.of(stock(NEAR, "chip", 64)), List.of(crafter(CRAFTER, PROGRAMMED)))
                .plan(REQUESTER, batch("pipe", 6));

        assertThat(plan.missing()).containsOnly(Map.entry(item("programmer"), 1));
    }

    @Test
    void everyCrafterNeedsItsOwnKeptIngredient() {
        RequestPlan plan = new RequestPlanner(List.of(new Stock(NEAR, Map.of(item("chip"), 64, item("programmer"), 5))),
                List.of(crafter(CRAFTER, PROGRAMMED), crafter(OTHER_CRAFTER, PROGRAMMED)))
                .plan(REQUESTER, batch("pipe", 6));

        assertThat(plan.provisions()).filteredOn(provision -> provision.batch().type().equals(item("programmer")))
                .containsExactlyInAnyOrder(
                        new Provision(NEAR, CRAFTER, batch("programmer", 1)),
                        new Provision(NEAR, OTHER_CRAFTER, batch("programmer", 1)));
    }

    @Test
    void aSecondItemInTheSamePlanDoesNotBringTheKeptIngredientAgain() {
        RequestPlan plan = new RequestPlanner(List.of(new Stock(NEAR, Map.of(item("chip"), 64, item("programmer"), 5))),
                List.of(crafter(CRAFTER, PROGRAMMED)))
                .plan(REQUESTER, List.of(batch("pipe", 2), batch("pipe", 3)));

        assertThat(plan.provisions()).containsExactlyInAnyOrder(
                new Provision(NEAR, CRAFTER, batch("programmer", 1)),
                new Provision(NEAR, CRAFTER, batch("chip", 5)));
    }
}
