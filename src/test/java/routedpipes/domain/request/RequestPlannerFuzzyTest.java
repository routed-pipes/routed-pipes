package routedpipes.domain.request;

import static routedpipes.testing.Fixtures.batch;
import static routedpipes.testing.Fixtures.router;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import routedpipes.domain.crafting.Recipe;
import routedpipes.domain.item.FuzzyFlags;
import routedpipes.domain.item.FuzzyType;
import routedpipes.domain.item.ItemBatch;
import routedpipes.domain.item.ItemType;
import routedpipes.domain.routing.RouterId;
import routedpipes.testing.Fixtures.WornItem;
import org.junit.jupiter.api.Test;

class RequestPlannerFuzzyTest {

    private static final RouterId REQUESTER = router(1);
    private static final RouterId NEAR = router(2);
    private static final RouterId FAR = router(3);
    private static final RouterId CRAFTER = router(4);
    private static final RouterId SAWMILL = router(5);

    private static final FuzzyFlags TAG = new FuzzyFlags(true, false, false, false);
    private static final FuzzyFlags IGNORE_DAMAGE = new FuzzyFlags(false, true, false, false);

    private static final WornItem OAK = WornItem.fresh("oak_planks", "planks");
    private static final WornItem BIRCH = WornItem.fresh("birch_planks", "planks");
    private static final WornItem SPRUCE = WornItem.fresh("spruce_planks", "planks");
    private static final WornItem SWORD = WornItem.fresh("sword");
    private static final ItemType STICK = batch("stick", 1).type();
    private static final ItemType LOG = batch("log", 1).type();

    private static final ItemType ANY_PLANKS = new FuzzyType(OAK, TAG);
    // sticks from two planks of any kind
    private static final Crafter STICKS = Crafter.of(CRAFTER, new Recipe(List.of(new ItemBatch(ANY_PLANKS, 2)), batch("stick", 4)));

    private static Stock stock(RouterId provider, Object... typesAndCounts) {
        Map<ItemType, Integer> items = new LinkedHashMap<>();
        for (int index = 0; index < typesAndCounts.length; index += 2) {
            items.put((ItemType) typesAndCounts[index], (Integer) typesAndCounts[index + 1]);
        }
        return new Stock(provider, items);
    }

    @Test
    void anIngredientWithFlagsIsServedByAnyMatchingItemInStock() {
        RequestPlan plan = new RequestPlanner(List.of(stock(NEAR, BIRCH, 64)), List.of(STICKS)).plan(REQUESTER, batch("stick", 4));

        assertThat(plan.complete()).isTrue();
        assertThat(plan.provisions()).containsExactly(new Provision(NEAR, CRAFTER, new ItemBatch(BIRCH, 2)));
        assertThat(plan.crafts()).containsExactly(new CraftJob(CRAFTER, REQUESTER, batch("stick", 4)));
    }

    @Test
    void theItemOfTheSlotItselfIsUsedUpFirst() {
        RequestPlan plan = new RequestPlanner(List.of(stock(NEAR, BIRCH, 64, OAK, 3)), List.of(STICKS)).plan(REQUESTER, batch("stick", 8));

        assertThat(plan.provisions()).containsExactly(
                new Provision(NEAR, CRAFTER, new ItemBatch(OAK, 3)),
                new Provision(NEAR, CRAFTER, new ItemBatch(BIRCH, 1)));
    }

    @Test
    void severalKindsFromSeveralProvidersAddUp() {
        List<Stock> stock = List.of(stock(NEAR, BIRCH, 1), stock(FAR, SPRUCE, 1, SWORD, 5));

        RequestPlan plan = new RequestPlanner(stock, List.of(STICKS)).plan(REQUESTER, batch("stick", 4));

        assertThat(plan.complete()).isTrue();
        assertThat(plan.provisions()).containsExactlyInAnyOrder(
                new Provision(NEAR, CRAFTER, new ItemBatch(BIRCH, 1)),
                new Provision(FAR, CRAFTER, new ItemBatch(SPRUCE, 1)));
    }

    @Test
    void whatDoesNotMatchIsNotTaken() {
        RequestPlan plan = new RequestPlanner(List.of(stock(NEAR, SWORD, 64)), List.of(STICKS)).plan(REQUESTER, batch("stick", 4));

        assertThat(plan.complete()).isFalse();
        assertThat(plan.provisions()).isEmpty();
    }

    @Test
    void aMissingIngredientWithFlagsIsReportedAsTheItemOfItsSlot() {
        RequestPlan plan = new RequestPlanner(List.of(stock(NEAR, BIRCH, 1)), List.of(STICKS)).plan(REQUESTER, batch("stick", 4));

        assertThat(plan.missing()).containsOnly(Map.entry(OAK, 1));
    }

    @Test
    void withoutFlagsAnIngredientStaysExact() {
        Crafter exact = Crafter.of(CRAFTER, new Recipe(List.of(new ItemBatch(OAK, 2)), batch("stick", 4)));

        RequestPlan plan = new RequestPlanner(List.of(stock(NEAR, BIRCH, 64)), List.of(exact)).plan(REQUESTER, batch("stick", 4));

        assertThat(plan.complete()).isFalse();
        assertThat(plan.missing()).containsOnly(Map.entry(OAK, 2));
    }

    @Test
    void anIngredientWithFlagsIsAlsoCraftedByAnyCrafterMakingAMatchingItem() {
        Crafter sawmill = Crafter.of(SAWMILL, new Recipe(List.of(batch("log", 1)), new ItemBatch(BIRCH, 4)));

        RequestPlan plan = new RequestPlanner(List.of(stock(NEAR, LOG, 8)), List.of(STICKS, sawmill)).plan(REQUESTER, batch("stick", 4));

        assertThat(plan.complete()).isTrue();
        assertThat(plan.provisions()).containsExactly(new Provision(NEAR, SAWMILL, batch("log", 1)));
        assertThat(plan.crafts()).containsExactlyInAnyOrder(
                new CraftJob(SAWMILL, CRAFTER, new ItemBatch(BIRCH, 2)),
                new CraftJob(CRAFTER, REQUESTER, batch("stick", 4)));
    }

    @Test
    void stockIsUsedBeforeAMatchingItemIsCrafted() {
        Crafter sawmill = Crafter.of(SAWMILL, new Recipe(List.of(batch("log", 1)), new ItemBatch(BIRCH, 4)));

        RequestPlan plan = new RequestPlanner(List.of(stock(NEAR, LOG, 8, SPRUCE, 1)), List.of(STICKS, sawmill))
                .plan(REQUESTER, batch("stick", 4));

        assertThat(plan.provisions()).contains(new Provision(NEAR, CRAFTER, new ItemBatch(SPRUCE, 1)));
        assertThat(plan.crafts()).contains(new CraftJob(SAWMILL, CRAFTER, new ItemBatch(BIRCH, 1)));
    }

    @Test
    void aResultWithFlagsAnswersRequestsForAnythingMatching() {
        ItemType anySword = new FuzzyType(SWORD, IGNORE_DAMAGE);
        Crafter repair = Crafter.of(CRAFTER, new Recipe(List.of(batch("iron", 1)), new ItemBatch(anySword, 1)));
        List<Stock> stock = List.of(stock(NEAR, batch("iron", 1).type(), 8));

        RequestPlan worn = new RequestPlanner(stock, List.of(repair)).plan(REQUESTER, new ItemBatch(SWORD.worn(5), 2));
        RequestPlan other = new RequestPlanner(stock, List.of(repair)).plan(REQUESTER, new ItemBatch(OAK, 1));

        assertThat(worn.complete()).isTrue();
        // the crafter is asked for its result as it names it, flags and all
        assertThat(worn.crafts()).containsExactly(new CraftJob(CRAFTER, REQUESTER, new ItemBatch(anySword, 2)));
        assertThat(other.complete()).isFalse();
        assertThat(other.crafts()).isEmpty();
    }

    @Test
    void aRecipeThatNeedsWhatItMakesDoesNotCraftForItself() {
        Crafter loop = Crafter.of(CRAFTER, new Recipe(List.of(new ItemBatch(ANY_PLANKS, 1)), new ItemBatch(BIRCH, 2)));

        RequestPlan plan = new RequestPlanner(List.of(), List.of(loop)).plan(REQUESTER, new ItemBatch(BIRCH, 2));

        assertThat(plan.complete()).isFalse();
        assertThat(plan.crafts()).isEmpty();
    }

    @Test
    void stockForTheRequestItselfStaysExact() {
        RequestPlan plan = new RequestPlanner(List.of(stock(NEAR, BIRCH, 64)), List.of()).plan(REQUESTER, new ItemBatch(OAK, 1));

        assertThat(plan.complete()).isFalse();
        assertThat(STICK).isNotEqualTo(OAK);
    }
}
