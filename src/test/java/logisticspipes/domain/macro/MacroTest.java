package logisticspipes.domain.macro;

import static logisticspipes.testing.Fixtures.batch;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;

class MacroTest {

    @Test
    void entriesOfTheSameItemAreMerged() {
        Macro macro = new Macro("tools", List.of(batch("stick", 2), batch("iron", 3), batch("stick", 5)));

        assertThat(macro.entries()).containsExactly(batch("stick", 7), batch("iron", 3));
    }

    @Test
    void addingAnItemThatIsThereRaisesItsAmount() {
        Macro macro = Macro.EMPTY.with(batch("stick", 2)).with(batch("iron", 1)).with(batch("stick", 64));

        assertThat(macro.entries()).containsExactly(batch("stick", 66), batch("iron", 1));
    }

    @Test
    void amountsDoNotOverflow() {
        Macro macro = Macro.EMPTY.with(batch("stick", Integer.MAX_VALUE)).with(batch("stick", 5));

        assertThat(macro.entries()).containsExactly(batch("stick", Integer.MAX_VALUE));
    }

    @Test
    void anItemCanBeTakenOutAgain() {
        Macro macro = new Macro("tools", List.of(batch("stick", 2), batch("iron", 3))).without(batch("stick", 1).type());

        assertThat(macro.entries()).containsExactly(batch("iron", 3));
    }

    @Test
    void savingNeedsAName() {
        assertThat(new Macro("  ", List.of(batch("stick", 2))).problem()).contains(Macro.Problem.NO_NAME);
    }

    @Test
    void savingNeedsAtLeastOneItem() {
        assertThat(new Macro("tools", List.of()).problem()).contains(Macro.Problem.NO_ITEMS);
    }

    @Test
    void aNamedMacroWithItemsCanBeSaved() {
        assertThat(new Macro("tools", List.of(batch("stick", 2))).problem()).isEmpty();
    }

    @Test
    void spacesAroundTheNameDoNotCount() {
        assertThat(Macro.EMPTY.named("  tools ").name()).isEqualTo("tools");
    }
}
