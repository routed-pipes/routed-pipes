package routedpipes.domain.fluid;

import static routedpipes.testing.Fixtures.fluid;
import static org.assertj.core.api.Assertions.assertThat;

import routedpipes.testing.FakeTank;
import org.junit.jupiter.api.Test;

class ExtractionBudgetTest {

    private final ExtractionBudget budget = new ExtractionBudget();
    private final Tank sideTank = new Tank(PipeTanks.SIDE_CAPACITY);
    private int paid;

    private boolean pay() {
        paid++;
        return true;
    }

    @Test
    void pullsOneHelpingAtATimeAndPaysForIt() {
        FakeTank source = new FakeTank(8000).holding(fluid("water", 8000));

        assertThat(budget.pull(sideTank, source, this::pay)).isEqualTo(ExtractionBudget.FLOW);

        assertThat(sideTank.amount()).isEqualTo(500);
        assertThat(source.tank.amount()).isEqualTo(7500);
        assertThat(paid).isEqualTo(1);
    }

    @Test
    void pullsNothingWithoutPower() {
        FakeTank source = new FakeTank(8000).holding(fluid("water", 8000));

        assertThat(budget.pull(sideTank, source, () -> false)).isZero();
        assertThat(source.tank.amount()).isEqualTo(8000);
    }

    @Test
    void whatWasPaidForAndNotPulledIsNotPaidForAgain() {
        FakeTank source = new FakeTank(8000).holding(fluid("water", 200));
        budget.pull(sideTank, source, this::pay);
        assertThat(sideTank.amount()).isEqualTo(200);
        source.tank.fill(fluid("water", 300), false);

        assertThat(budget.pull(sideTank, source, () -> false)).isEqualTo(300);
        assertThat(paid).isEqualTo(1);
    }

    @Test
    void anEmptySourceCostsOnlyTheFirstHelping() {
        FakeTank source = new FakeTank(8000);

        budget.pull(sideTank, source, this::pay);
        budget.pull(sideTank, source, this::pay);

        assertThat(paid).isEqualTo(1);
    }

    @Test
    void aFullSideTankPullsNothingAndCostsNothing() {
        sideTank.fill(fluid("water", PipeTanks.SIDE_CAPACITY), false);
        FakeTank source = new FakeTank(8000).holding(fluid("water", 8000));

        assertThat(budget.pull(sideTank, source, this::pay)).isZero();
        assertThat(paid).isZero();
    }

    @Test
    void pullsOnlyWhatStillFitsIntoTheSideTank() {
        sideTank.fill(fluid("water", PipeTanks.SIDE_CAPACITY - 120), false);
        FakeTank source = new FakeTank(8000).holding(fluid("water", 8000));

        assertThat(budget.pull(sideTank, source, this::pay)).isEqualTo(120);
        assertThat(sideTank.isFull()).isTrue();
    }

    @Test
    void anotherFluidThanTheOneInTheSideTankStaysWhereItIs() {
        sideTank.fill(fluid("lava", 100), false);
        FakeTank source = new FakeTank(8000).holding(fluid("water", 8000));

        assertThat(budget.pull(sideTank, source, this::pay)).isZero();
        assertThat(source.tank.amount()).isEqualTo(8000);
    }
}
