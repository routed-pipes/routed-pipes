package logisticspipes.domain.logistics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ExtractionTest {

    private static Extraction upgraded(int actionSpeed, int itemExtraction, int stackExtraction) {
        return Extraction.of(ExtractionMode.EXTRACTOR).with(actionSpeed, itemExtraction, stackExtraction);
    }

    @Test
    void withoutUpgradesItWorksLikeItsMode() {
        Extraction plain = Extraction.of(ExtractionMode.EXTRACTOR);

        assertThat(plain.interval()).isEqualTo(ExtractionMode.EXTRACTOR.interval());
        assertThat(plain.amount()).isEqualTo(ExtractionMode.EXTRACTOR.amount());
        assertThat(plain.stacks()).isEqualTo(1);
        assertThat(plain.energyFor(10)).isEqualTo(ExtractionMode.EXTRACTOR.energyFor(10));
    }

    @Test
    void actionSpeedHalvesTheWaitDownToEveryTick() {
        assertThat(upgraded(1, 0, 0).interval()).isEqualTo(10);
        assertThat(upgraded(2, 0, 0).interval()).isEqualTo(5);
        assertThat(upgraded(8, 0, 0).interval()).isEqualTo(1);
    }

    @Test
    void extractionUpgradesAddItemsAndStacks() {
        assertThat(upgraded(0, 2, 0).amount()).isEqualTo(48);
        assertThat(upgraded(0, 0, 2).amount()).isEqualTo(16 + 128);
        assertThat(upgraded(0, 0, 2).stacks()).isEqualTo(3);
    }

    @Test
    void fasterExtractorsPayMorePerItem() {
        assertThat(upgraded(0, 1, 0).energyFor(10)).isEqualTo(110);
        assertThat(upgraded(0, 0, 1).energyFor(10)).isEqualTo(120);
        assertThat(upgraded(0, 1, 1).energyFor(10)).isEqualTo(132);
    }

    @Test
    void upgradesFitOnlyWhereTheyDoSomething() {
        assertThat(UpgradeKind.MODULE_UPGRADE.fitsPipe(PipeKind.CHASSIS)).isTrue();
        assertThat(UpgradeKind.MODULE_UPGRADE.fitsPipe(PipeKind.BASIC)).isFalse();
        assertThat(UpgradeKind.ACTION_SPEED.fitsPipe(PipeKind.CHASSIS)).isFalse();
        assertThat(UpgradeKind.ACTION_SPEED.fitsModule(ModuleKind.EXTRACTOR)).isTrue();
        assertThat(UpgradeKind.ACTION_SPEED.fitsModule(ModuleKind.ITEM_SINK)).isFalse();
        assertThat(UpgradeKind.SPEED.fitsPipe(PipeKind.REQUEST)).isTrue();
    }
}
