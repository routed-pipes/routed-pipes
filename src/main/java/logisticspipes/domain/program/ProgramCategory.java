package logisticspipes.domain.program;

import java.util.List;
import java.util.stream.Stream;

// programs are unlocked on a disk a category at a time
public enum ProgramCategory {
    BASIC(pipes("request_pipe", "provider_pipe", "crafting_pipe", "satellite_pipe", "supplier_pipe"),
            upgrades("sneaky", "speed", "disconnection", "crafting_byproduct", "crafting_cleanup")),
    TIER_2(pipes("request_pipe_mk2", "system_entrance_pipe", "system_destination_pipe"),
            upgrades("advanced_satellite", "power_transportation", "action_speed", "item_extraction",
                    "item_stack_extraction")),
    FLUID(pipes("fluid_container_supplier_pipe", "fluid_basic_pipe", "fluid_terminus_pipe", "fluid_request_pipe",
            "fluid_provider_pipe", "fluid_supplier_pipe", "fluid_satellite_pipe", "fluid_insertion_pipe",
            "fluid_extractor_pipe")),
    TIER_3(pipes("inventory_system_connector_pipe", "firewall_pipe"), upgrades("module_upgrade")),
    CHASSIS(pipes("chassis_pipe_mk1", "chassis_pipe_mk2", "chassis_pipe_mk3", "chassis_pipe_mk4", "chassis_pipe_mk5"),
            modules("item_sink", "passive_supplier", "extractor", "polymorphic_item_sink", "quick_sort", "terminus",
                    "advanced_extractor", "provider")),
    CHASSIS_2(modules("mod_item_sink", "tag_item_sink", "enchantment_sink", "enchantment_sink_mk2",
            "creative_tab_item_sink", "crafter", "active_supplier"));

    private final List<Program> programs;

    @SafeVarargs
    ProgramCategory(List<Program>... programs) {
        this.programs = Stream.of(programs).flatMap(List::stream).toList();
    }

    public List<Program> programs() {
        return programs;
    }

    private static List<Program> pipes(String... ids) {
        return Stream.of(ids).map(id -> new Program(Program.Kind.PIPE, id)).toList();
    }

    private static List<Program> modules(String... names) {
        return Stream.of(names).map(name -> new Program(Program.Kind.MODULE, "module_" + name)).toList();
    }

    private static List<Program> upgrades(String... names) {
        return Stream.of(names).map(name -> new Program(Program.Kind.UPGRADE, "upgrade_" + name)).toList();
    }
}
