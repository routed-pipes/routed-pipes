package routedpipes.domain.logistics;

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

    @Test
    void remoteControlGoesIntoEveryPipeAndIntoNoModule() {
        for (PipeKind pipe : PipeKind.values()) {
            assertThat(UpgradeKind.CC_REMOTE_CONTROL.fitsPipe(pipe)).as(pipe.name()).isTrue();
        }
        for (ModuleKind module : ModuleKind.values()) {
            assertThat(UpgradeKind.CC_REMOTE_CONTROL.fitsModule(module)).as(module.name()).isFalse();
        }
        assertThat(UpgradeKind.CC_REMOTE_CONTROL.most()).isEqualTo(1);
    }
}
