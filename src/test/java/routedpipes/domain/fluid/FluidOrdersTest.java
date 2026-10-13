package routedpipes.domain.fluid;

import static routedpipes.testing.Fixtures.fluid;
import static routedpipes.testing.Fixtures.router;
import static org.assertj.core.api.Assertions.assertThat;

import routedpipes.domain.fluid.FluidOrders.Order;
import org.junit.jupiter.api.Test;

class FluidOrdersTest {

    private static final FluidType WATER = fluid("water", 1).type();
    private static final FluidType LAVA = fluid("lava", 1).type();

    private final FluidOrders orders = new FluidOrders();

    @Test
    void ordersAreWorkedOffInTheOrderTheyCameIn() {
        orders.add(new Order(WATER, 3000, router(1)));
        orders.add(new Order(LAVA, 1000, router(2)));

        assertThat(orders.first()).contains(new Order(WATER, 3000, router(1)));
        orders.sent(3000);
        assertThat(orders.first()).contains(new Order(LAVA, 1000, router(2)));
    }

    @Test
    void anOrderThatWasPartlySentStaysFirstWithWhatIsLeft() {
        orders.add(new Order(WATER, 7000, router(1)));
        orders.add(new Order(LAVA, 1000, router(2)));

        orders.sent(5000);

        assertThat(orders.first()).contains(new Order(WATER, 2000, router(1)));
    }

    @Test
    void givingUpDropsTheFirstOrderOnly() {
        orders.add(new Order(WATER, 7000, router(1)));
        orders.add(new Order(LAVA, 1000, router(2)));

        assertThat(orders.giveUp()).contains(new Order(WATER, 7000, router(1)));
        assertThat(orders.first()).contains(new Order(LAVA, 1000, router(2)));
    }

    @Test
    void knowsHowMuchOfAFluidIsPromisedAndToWhom() {
        orders.add(new Order(WATER, 7000, router(1)));
        orders.add(new Order(WATER, 500, router(2)));
        orders.add(new Order(LAVA, 1000, router(2)));

        assertThat(orders.owed(WATER)).isEqualTo(7500);
        assertThat(orders.owedTo(router(2), WATER)).isEqualTo(500);
        assertThat(orders.owedTo(router(1), LAVA)).isZero();
    }

    @Test
    void anEmptyListHasNoFirstOrder() {
        assertThat(orders.isEmpty()).isTrue();
        assertThat(orders.first()).isEmpty();
        assertThat(orders.giveUp()).isEmpty();
    }
}
