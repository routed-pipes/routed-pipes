package logisticspipes.domain.fluid;

import static logisticspipes.testing.Fixtures.fluid;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumMap;
import java.util.Map;

import logisticspipes.domain.routing.Side;
import logisticspipes.testing.FakeTank;
import org.junit.jupiter.api.Test;

class PipeTanksTest {

    private static final FluidType WATER = fluid("water", 1).type();
    private static final FluidType LAVA = fluid("lava", 1).type();

    private final PipeTanks tanks = new PipeTanks();
    private final Map<Side, TankAccess> neighbours = new EnumMap<>(Side.class);

    @Test
    void aTankHoldsOneFluidAtATime() {
        Tank tank = new Tank(1000);

        assertThat(tank.fill(fluid("water", 600), false)).isEqualTo(600);
        assertThat(tank.fill(fluid("lava", 100), false)).isZero();
        assertThat(tank.fill(fluid("water", 600), false)).isEqualTo(400);
        assertThat(tank.isFull()).isTrue();
        assertThat(tank.drain(1000)).isEqualTo(1000);
        assertThat(tank.fill(fluid("lava", 100), false)).isEqualTo(100);
    }

    @Test
    void simulatedFillingChangesNothing() {
        Tank tank = new Tank(1000);

        assertThat(tank.fill(fluid("water", 600), true)).isEqualTo(600);
        assertThat(tank.isEmpty()).isTrue();
    }

    @Test
    void arrivedFluidGoesIntoTheTankNextToThePipeFirst() {
        FakeTank neighbour = new FakeTank(8000);
        neighbours.put(Side.UP, neighbour);

        assertThat(tanks.unpack(fluid("water", 3000), neighbours)).isZero();
        assertThat(neighbour.tank.amount()).isEqualTo(3000);
        assertThat(tanks.isEmpty()).isTrue();
    }

    @Test
    void whatTheNeighbourDoesNotTakeWaitsInTheSideTankAndThenTheCentre() {
        FakeTank neighbour = new FakeTank(1000);
        neighbours.put(Side.UP, neighbour);

        assertThat(tanks.unpack(fluid("water", 5000), neighbours)).isZero();
        assertThat(tanks.unpack(fluid("water", 5000), neighbours)).isZero();
        assertThat(neighbour.tank.amount()).isEqualTo(1000);
        assertThat(tanks.side(Side.UP).amount()).isEqualTo(5000);
        assertThat(tanks.centre().amount()).isEqualTo(4000);
    }

    @Test
    void withoutATankNextToItThePipeOnlyHasItsCentre() {
        assertThat(tanks.unpack(fluid("water", 4000), neighbours)).isZero();
        assertThat(tanks.unpack(fluid("water", 4000), neighbours)).isZero();
        assertThat(tanks.unpack(fluid("water", 4000), neighbours)).isEqualTo(2000);
        assertThat(tanks.centre().amount()).isEqualTo(PipeTanks.CENTRE_CAPACITY);
        assertThat(tanks.side(Side.UP).isEmpty()).isTrue();
    }

    @Test
    void whatFitsNowhereIsHandedBack() {
        tanks.centre().fill(fluid("lava", 1), false);

        assertThat(tanks.unpack(fluid("water", 500), neighbours)).isEqualTo(500);
    }

    @Test
    void fluidMovesFromTheCentreToTheSideAndThenIntoTheNeighbour() {
        tanks.centre().fill(fluid("water", 2000), false);
        FakeTank neighbour = new FakeTank(8000);
        neighbours.put(Side.EAST, neighbour);

        assertThat(tanks.pushOut(neighbours)).isTrue();
        assertThat(tanks.side(Side.EAST).amount()).isEqualTo(2000);
        assertThat(neighbour.tank.amount()).isZero();

        assertThat(tanks.pushOut(neighbours)).isTrue();
        assertThat(neighbour.tank.amount()).isEqualTo(2000);
        assertThat(tanks.isEmpty()).isTrue();
        assertThat(tanks.pushOut(neighbours)).isFalse();
    }

    @Test
    void nothingLeavesTheCentreWithoutATankNextToThePipe() {
        tanks.centre().fill(fluid("water", 2000), false);

        assertThat(tanks.pushOut(neighbours)).isFalse();
        assertThat(tanks.centre().amount()).isEqualTo(2000);
    }

    @Test
    void aSideThatLostItsTankGoesBackToTheCentre() {
        tanks.side(Side.EAST).fill(fluid("water", 3000), false);
        tanks.centre().fill(fluid("water", 8000), false);

        tanks.sideLost(Side.EAST);

        assertThat(tanks.side(Side.EAST).isEmpty()).isTrue();
        // what did not fit is gone
        assertThat(tanks.centre().amount()).isEqualTo(PipeTanks.CENTRE_CAPACITY);
    }

    @Test
    void roomCountsTheNeighbourAndTheSideTankTowardsItButNotTheCentre() {
        neighbours.put(Side.UP, new FakeTank(8000).holding(fluid("water", 2000)));
        tanks.side(Side.UP).fill(fluid("water", 1000), false);

        assertThat(tanks.roomFor(WATER, neighbours)).isEqualTo(6000 + 4000);
        assertThat(tanks.roomFor(LAVA, neighbours)).isZero();
    }

    @Test
    void aPipeWithoutATankNextToItHasNoRoom() {
        assertThat(tanks.roomFor(WATER, neighbours)).isZero();
    }
}
