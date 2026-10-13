package routedpipes.domain.sign;

import static org.assertj.core.api.Assertions.assertThat;

import routedpipes.domain.logistics.PipeKind;
import org.junit.jupiter.api.Test;

class SignKindTest {

    @Test
    void aCraftingSignOnlyGoesOnACraftingPipe() {
        for (PipeKind pipe : PipeKind.values()) {
            assertThat(SignKind.CRAFTING.allowedOn(pipe)).as(pipe.name()).isEqualTo(pipe == PipeKind.CRAFTING);
        }
    }

    @Test
    void aChassisWithACrafterModuleIsNoCraftingPipe() {
        assertThat(SignKind.CRAFTING.allowedOn(PipeKind.CHASSIS)).isFalse();
    }

    @Test
    void anItemAmountSignGoesOnEveryRoutedPipe() {
        for (PipeKind pipe : PipeKind.values()) {
            assertThat(SignKind.ITEM_AMOUNT.allowedOn(pipe)).as(pipe.name()).isEqualTo(pipe != PipeKind.REQUEST_TABLE);
        }
    }

    @Test
    void plainPipesTakeNoSign() {
        assertThat(SignKind.CRAFTING.allowedOn(null)).isFalse();
        assertThat(SignKind.ITEM_AMOUNT.allowedOn(null)).isFalse();
    }

    @Test
    void theKindsComeRoundAgain() {
        assertThat(SignKind.CRAFTING.next()).isEqualTo(SignKind.ITEM_AMOUNT);
        assertThat(SignKind.ITEM_AMOUNT.next()).isEqualTo(SignKind.CRAFTING);
    }
}
