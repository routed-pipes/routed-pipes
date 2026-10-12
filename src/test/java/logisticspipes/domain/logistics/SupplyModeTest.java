package logisticspipes.domain.logistics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SupplyModeTest {

    private static final int ROOM = 64;

    @Test
    void partialTopsUpToTheTarget() {
        assertThat(SupplyMode.PARTIAL.amount(16, 15, 0, ROOM)).isEqualTo(1);
        assertThat(SupplyMode.PARTIAL.amount(16, 10, 4, ROOM)).isEqualTo(2);
        assertThat(SupplyMode.PARTIAL.amount(16, 16, 0, ROOM)).isZero();
        assertThat(SupplyMode.PARTIAL.amount(16, 20, 0, ROOM)).isZero();
    }

    @Test
    void fullAsksForAllOrNothing() {
        assertThat(SupplyMode.FULL.amount(16, 10, 0, ROOM)).isEqualTo(6);
        assertThat(SupplyMode.FULL.allOrNothing()).isTrue();
        assertThat(SupplyMode.PARTIAL.allOrNothing()).isFalse();
    }

    @Test
    void bulk50WaitsUntilHalfIsGone() {
        assertThat(SupplyMode.BULK50.amount(16, 9, 0, ROOM)).isZero();
        assertThat(SupplyMode.BULK50.amount(16, 8, 0, ROOM)).isEqualTo(8);
        assertThat(SupplyMode.BULK50.amount(5, 3, 0, ROOM)).isZero();
        assertThat(SupplyMode.BULK50.amount(5, 2, 0, ROOM)).isEqualTo(3);
    }

    @Test
    void bulk100WaitsUntilNothingIsLeft() {
        assertThat(SupplyMode.BULK100.amount(16, 1, 0, ROOM)).isZero();
        assertThat(SupplyMode.BULK100.amount(16, 0, 0, ROOM)).isEqualTo(16);
        assertThat(SupplyMode.BULK100.amount(16, 0, 16, ROOM)).isZero();
    }

    @Test
    void infiniteFillsTheRoomWhateverTheTarget() {
        assertThat(SupplyMode.INFINITE.amount(1, 500, 0, 64)).isEqualTo(64);
        assertThat(SupplyMode.INFINITE.amount(1, 500, 24, 64)).isEqualTo(40);
        assertThat(SupplyMode.INFINITE.amount(1, 0, 64, 10)).isZero();
    }

    @Test
    void nothingIsOrderedWithoutRoom() {
        for (SupplyMode mode : SupplyMode.values()) {
            assertThat(mode.amount(16, 0, 0, 0)).isZero();
        }
    }

    @Test
    void modesCycle() {
        assertThat(SupplyMode.INFINITE.next()).isEqualTo(SupplyMode.PARTIAL);
    }
}
