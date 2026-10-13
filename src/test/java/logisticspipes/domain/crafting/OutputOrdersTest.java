package logisticspipes.domain.crafting;

import static logisticspipes.testing.Fixtures.batch;
import static logisticspipes.testing.Fixtures.router;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OutputOrdersTest {

    private static final long PATIENCE = 1000;

    private final OutputOrders orders = new OutputOrders();

    @Test
    void keepsOrderUntilEverythingIsShipped() {
        orders.add(new OutputOrder(router(1), batch("ingot", 8)));

        orders.ship(50, PATIENCE, order -> 3);
        assertThat(orders.all()).usingRecursiveFieldByFieldElementComparatorIgnoringFields("placed", "request")
                .containsExactly(new OutputOrder(router(1), batch("ingot", 5), 50));

        orders.ship(60, PATIENCE, order -> 5);
        assertThat(orders.isEmpty()).isTrue();
    }

    @Test
    void leavesOrderAloneWhenNothingCouldBeShipped() {
        OutputOrder order = new OutputOrder(router(1), batch("ingot", 8));
        orders.add(order);

        orders.ship(50, PATIENCE, ignored -> 0);

        assertThat(orders.all()).containsExactly(order);
    }

    @Test
    void offersEveryOrderInTurn() {
        orders.add(new OutputOrder(router(1), batch("ingot", 2)));
        orders.add(new OutputOrder(router(2), batch("ingot", 4)));

        orders.ship(50, PATIENCE, order -> order.destination().equals(router(2)) ? 4 : 0);

        // the crafter made progress, so the order still waiting is not stale either
        assertThat(orders.all()).usingRecursiveFieldByFieldElementComparatorIgnoringFields("placed", "request")
                .containsExactly(new OutputOrder(router(1), batch("ingot", 2), 50));
    }

    @Test
    void recipeAddsUpInputsPerType() {
        Recipe recipe = new Recipe(java.util.List.of(batch("planks", 2), batch("stick", 1), batch("planks", 3)), batch("thing", 1));

        assertThat(recipe.inputTotals()).containsExactly(
                java.util.Map.entry(batch("planks", 1).type(), 5),
                java.util.Map.entry(batch("stick", 1).type(), 1));
    }

    @Test
    void recipeRunsAsOftenAsTheScarcestInputAllows() {
        Recipe recipe = new Recipe(java.util.List.of(batch("planks", 2), batch("stick", 1), batch("planks", 1)), batch("thing", 1));

        assertThat(recipe.runsPossible(java.util.Map.of(batch("planks", 1).type(), 10, batch("stick", 1).type(), 5))).isEqualTo(3);
        assertThat(recipe.runsPossible(java.util.Map.of(batch("planks", 1).type(), 10))).isZero();
        assertThat(new Recipe(java.util.List.of(), batch("thing", 1)).runsPossible(java.util.Map.of())).isZero();
    }

    @Test
    void dropsAnOrderThatMadeNoProgressForTooLong() {
        orders.add(new OutputOrder(router(1), batch("ingot", 8), 100));

        orders.ship(100 + PATIENCE - 1, PATIENCE, ignored -> 0);
        assertThat(orders.all()).hasSize(1);

        java.util.concurrent.atomic.AtomicInteger asked = new java.util.concurrent.atomic.AtomicInteger();
        orders.ship(100 + PATIENCE, PATIENCE, ignored -> asked.incrementAndGet());
        assertThat(orders.isEmpty()).isTrue();
        assertThat(asked).hasValue(0);
    }

    @Test
    void progressGivesAnOrderMoreTime() {
        orders.add(new OutputOrder(router(1), batch("ingot", 8), 100));

        orders.ship(100 + PATIENCE - 1, PATIENCE, ignored -> 2);
        orders.ship(100 + PATIENCE + 500, PATIENCE, ignored -> 0);

        assertThat(orders.all()).extracting(order -> order.batch().count()).containsExactly(6);
    }

    @Test
    void knowsHowMuchIsOwed() {
        orders.add(new OutputOrder(router(1), batch("ingot", 8)));
        orders.add(new OutputOrder(router(2), batch("ingot", 3)));
        orders.add(new OutputOrder(router(2), batch("plate", 5)));

        assertThat(orders.owed(batch("ingot", 1).type())).isEqualTo(11);
        assertThat(orders.owedInTotal()).isEqualTo(16);
    }

    @Test
    void addsUpWhatIsOwedPerItem() {
        orders.add(new OutputOrder(router(1), batch("ingot", 8)));
        orders.add(new OutputOrder(router(2), batch("plate", 5)));
        orders.add(new OutputOrder(router(2), batch("ingot", 3)));

        assertThat(orders.owedByType()).containsExactly(
                java.util.Map.entry(batch("ingot", 1).type(), 11),
                java.util.Map.entry(batch("plate", 1).type(), 5));
    }
}
