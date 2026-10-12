package logisticspipes.domain.crafting;

import static logisticspipes.testing.Fixtures.batch;
import static logisticspipes.testing.Fixtures.router;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class OutputOrdersTest {

    private final OutputOrders orders = new OutputOrders();

    @Test
    void keepsOrderUntilEverythingIsShipped() {
        orders.add(new OutputOrder(router(1), batch("ingot", 8)));

        orders.ship(order -> 3);
        assertThat(orders.all()).containsExactly(new OutputOrder(router(1), batch("ingot", 5)));

        orders.ship(order -> 5);
        assertThat(orders.isEmpty()).isTrue();
    }

    @Test
    void leavesOrderAloneWhenNothingCouldBeShipped() {
        OutputOrder order = new OutputOrder(router(1), batch("ingot", 8));
        orders.add(order);

        orders.ship(ignored -> 0);

        assertThat(orders.all()).containsExactly(order);
    }

    @Test
    void offersEveryOrderInTurn() {
        orders.add(new OutputOrder(router(1), batch("ingot", 2)));
        orders.add(new OutputOrder(router(2), batch("ingot", 4)));

        orders.ship(order -> order.destination().equals(router(2)) ? 4 : 0);

        assertThat(orders.all()).containsExactly(new OutputOrder(router(1), batch("ingot", 2)));
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
}
