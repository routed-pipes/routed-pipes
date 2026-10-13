package routedpipes.domain.macro;

import static routedpipes.testing.Fixtures.batch;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

class MacroDiskTest {

    private static Macro macro(String name, String item, int count) {
        return new Macro(name, List.of(batch(item, count)));
    }

    @Test
    void savedMacrosAreKeptInOrder() {
        MacroDisk disk = MacroDisk.EMPTY.saved(macro("tools", "stick", 2)).saved(macro("food", "bread", 8));

        assertThat(disk.macros()).extracting(Macro::name).containsExactly("tools", "food");
    }

    @Test
    void savingUnderANameThatIsTakenOverwrites() {
        MacroDisk disk = MacroDisk.EMPTY.saved(macro("tools", "stick", 2)).saved(macro("food", "bread", 8))
                .saved(macro("tools", "iron", 3));

        assertThat(disk.macros()).extracting(Macro::name).containsExactly("tools", "food");
        assertThat(disk.macro("tools")).contains(macro("tools", "iron", 3));
    }

    @Test
    void aMacroWithoutNameOrItemsIsNotSaved() {
        assertThatThrownBy(() -> MacroDisk.EMPTY.saved(macro(" ", "stick", 2))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> MacroDisk.EMPTY.saved(new Macro("tools", List.of())))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void aDiskNeverHoldsTwoMacrosWithTheSameNameOrUnfinishedOnes() {
        MacroDisk disk = new MacroDisk("mine", List.of(macro("tools", "stick", 2), new Macro("empty", List.of()),
                macro("tools", "iron", 3)));

        assertThat(disk.macros()).containsExactly(macro("tools", "iron", 3));
    }

    @Test
    void aMacroCanBeDeleted() {
        MacroDisk disk = MacroDisk.EMPTY.saved(macro("tools", "stick", 2)).saved(macro("food", "bread", 8)).without("tools");

        assertThat(disk.macros()).extracting(Macro::name).containsExactly("food");
        assertThat(disk.macro("tools")).isEmpty();
    }

    @Test
    void renamingTheDiskKeepsItsMacros() {
        MacroDisk disk = MacroDisk.EMPTY.saved(macro("tools", "stick", 2)).named(" spares ");

        assertThat(disk.name()).isEqualTo("spares");
        assertThat(disk.macros()).hasSize(1);
    }

    @Test
    void aFullDiskTakesNoNewMacroButStillChangesItsOwn() {
        MacroDisk disk = MacroDisk.EMPTY;
        for (int index = 0; index < MacroDisk.MAX_MACROS; index++) {
            disk = disk.saved(macro("m" + index, "coal", 1));
        }

        assertThat(disk.saved(macro("one more", "coal", 1)).macros()).hasSize(MacroDisk.MAX_MACROS);
        assertThat(disk.saved(macro("m0", "iron", 5)).macro("m0").orElseThrow().entries())
                .extracting(entry -> entry.count()).containsExactly(5);
    }
}
