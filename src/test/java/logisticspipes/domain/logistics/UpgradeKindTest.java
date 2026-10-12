package logisticspipes.domain.logistics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class UpgradeKindTest {

    @Test
    void opaqueUpgradeFitsEveryRoutedPipeAndNoModule() {
        for (PipeKind pipe : PipeKind.values()) {
            assertThat(UpgradeKind.OPAQUE.fitsPipe(pipe)).as("%s", pipe).isTrue();
        }
        for (ModuleKind module : ModuleKind.values()) {
            assertThat(UpgradeKind.OPAQUE.fitsModule(module)).as("%s", module).isFalse();
        }
    }

    @Test
    void oneOpaqueUpgradeIsEnough() {
        assertThat(UpgradeKind.OPAQUE.most()).isEqualTo(1);
    }
}
