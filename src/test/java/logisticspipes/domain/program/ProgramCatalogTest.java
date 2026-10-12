package logisticspipes.domain.program;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

class ProgramCatalogTest {

    @Test
    void everyProgramIsInOneCategoryOnly() {
        List<Program> listed = Stream.of(ProgramCategory.values()).flatMap(category -> category.programs().stream()).toList();

        assertThat(listed).doesNotHaveDuplicates();
        assertThat(ProgramCatalog.all()).containsExactlyInAnyOrderElementsOf(listed);
    }

    @Test
    void noCategoryIsEmpty() {
        assertThat(ProgramCategory.values()).allSatisfy(category -> assertThat(category.programs()).isNotEmpty());
    }

    @Test
    void programsAreListedPipesFirstThenModulesThenUpgradesByName() {
        List<Program> all = ProgramCatalog.all();

        assertThat(all).extracting(Program::kind).isSorted();
        assertThat(all.stream().filter(program -> program.kind() == Program.Kind.MODULE)).extracting(Program::id).isSorted();
        assertThat(all.get(0)).isEqualTo(new Program(Program.Kind.PIPE, "chassis_pipe_mk1"));
    }

    @Test
    void theOriginalCategoriesAreKept() {
        assertThat(category("request_pipe")).isEqualTo(ProgramCategory.BASIC);
        assertThat(category("upgrade_crafting_cleanup")).isEqualTo(ProgramCategory.BASIC);
        assertThat(category("request_pipe_mk2")).isEqualTo(ProgramCategory.TIER_2);
        assertThat(category("upgrade_advanced_satellite")).isEqualTo(ProgramCategory.TIER_2);
        assertThat(category("fluid_supplier_pipe")).isEqualTo(ProgramCategory.FLUID);
        assertThat(category("firewall_pipe")).isEqualTo(ProgramCategory.TIER_3);
        assertThat(category("upgrade_module_upgrade")).isEqualTo(ProgramCategory.TIER_3);
        assertThat(category("chassis_pipe_mk5")).isEqualTo(ProgramCategory.CHASSIS);
        assertThat(category("module_provider")).isEqualTo(ProgramCategory.CHASSIS);
        assertThat(category("module_crafter")).isEqualTo(ProgramCategory.CHASSIS_2);
    }

    @Test
    void whatNeedsNoProgramIsNotListed() {
        assertThat(ProgramCatalog.byId("basic_logistics_pipe")).isEmpty();
        assertThat(ProgramCatalog.byId("transport_pipe")).isEmpty();
        assertThat(ProgramCatalog.byId("request_table")).isEmpty();
        assertThat(ProgramCatalog.byId("upgrade_power_supplier_rf")).isEmpty();
        assertThatThrownBy(() -> ProgramCatalog.categoryOf(new Program(Program.Kind.PIPE, "transport_pipe")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static ProgramCategory category(String id) {
        return ProgramCatalog.categoryOf(ProgramCatalog.byId(id).orElseThrow());
    }
}
