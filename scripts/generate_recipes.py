#!/usr/bin/env python3
"""Writes the recipes of the original mod. Run from the project root after adding a line to RECIPES.

Files of recipes that are not in the table are left alone, so a recipe written by hand stays.
A grid is a list of rows, the things in a row are separated by spaces and a dot is an empty place.
"""
import json
import os

RECIPES_DIR = "src/main/resources/data/routedpipes/recipe/"

# short names for what is not from this mod, everything else is the id of one of our items
NAMES = {
    "iron": "#c:ingots/iron", "gold": "#c:ingots/gold", "gold_nugget": "#c:nuggets/gold", "copper": "#c:ingots/copper",
    "redstone": "#c:dusts/redstone", "glowstone": "#c:dusts/glowstone", "diamond": "#c:gems/diamond",
    "lapis": "#c:gems/lapis", "quartz": "#c:gems/quartz", "sand": "#c:sands", "glass": "#c:glass_blocks",
    "planks": "#minecraft:planks", "chest": "#c:chests/wooden", "black_dye": "#c:dyes/black",
    "purple_dye": "#c:dyes/purple", "paper": "minecraft:paper", "crafting_table": "minecraft:crafting_table",
    "piston": "minecraft:piston", "redstone_block": "#c:storage_blocks/redstone", "bucket": "minecraft:bucket",
    "ender_pearl": "#c:ender_pearls", "blaze_powder": "minecraft:blaze_powder", "sign": "#minecraft:signs", "book": "minecraft:book",
    "copper_block": "#c:storage_blocks/copper", "slime_ball": "#c:slime_balls", "ink_sac": "minecraft:ink_sac",
    "comparator": "minecraft:comparator", "wheat": "minecraft:wheat",
}
HAS_COPPER = {"type": "neoforge:not", "value": {"type": "neoforge:tag_empty", "tag": "c:ingots/copper"}}
NO_COPPER = {"type": "neoforge:tag_empty", "tag": "c:ingots/copper"}


def ingredient(name):
    target = NAMES.get(name, "routedpipes:" + name)
    return {"tag": target[1:]} if target.startswith("#") else {"item": target}


def result(name, count):
    return {"id": "routedpipes:" + name, "count": count}


def shaped(name, rows, count=1, file=None, when=None, kind="minecraft:crafting_shaped"):
    """Letters are handed out in the order the ingredients show up in the grid."""
    letters, pattern = {}, []
    for row in rows:
        line = ""
        for thing in row.split():
            if thing == ".":
                line += " "
            else:
                line += letters.setdefault(thing, chr(ord("A") + len(letters)))
        pattern.append(line)
    recipe = {"type": kind, "category": "misc", "pattern": pattern,
              "key": {letter: ingredient(thing) for thing, letter in letters.items()}, "result": result(name, count)}
    if when:
        recipe = {"neoforge:conditions": [when], **recipe}
    return file or name, recipe


def programmed(name, layout, base=None, count=1):
    """A shaped recipe that takes the programmer flashed for what it makes and leaves it in the grid.

    In a layout P is the programmer and BASE the pipe or module that is built on."""
    rows = [" ".join({"P": "logistics_programmer", "BASE": base}.get(thing, thing) for thing in row.split()) for row in layout]
    return shaped(name, rows, count, kind="routedpipes:programmed")


def shapeless(name, things, file=None, count=1):
    """Every entry of things is one ingredient, a list of names for an ingredient that takes any of them."""
    ingredients = [[ingredient(option) for option in thing] if isinstance(thing, list) else ingredient(thing) for thing in things]
    return file or name, {"type": "minecraft:crafting_shapeless", "category": "misc", "ingredients": ingredients,
                          "result": result(name, count)}


def smelted(name, raw):
    return name + "_from_smelting", {"type": "minecraft:smelting", "category": "misc", "ingredient": ingredient(raw),
                                     "result": result(name, 1), "experience": 0.0, "cookingtime": 200}


PIPE_LEVEL_1 = [". P .", "redstone chip_fpga redstone", ". BASE ."]
PIPE_LEVEL_2 = [". P .", "redstone chip_basic redstone", "iron BASE iron"]
PIPE_LEVEL_3 = [". P .", "redstone chip_advanced redstone", "gold BASE gold"]
PIPE_ENDER_1 = [". P .", "ender_pearl chip_basic redstone", "iron BASE iron"]
PIPE_ENDER_2 = [". P .", "ender_pearl chip_advanced redstone", "iron BASE iron"]

UPGRADE_LEVEL_1 = ["redstone P redstone", "iron chip_basic iron", "paper gold_nugget paper"]
UPGRADE_LEVEL_2 = ["redstone P redstone", "iron chip_advanced iron", "paper gold_nugget paper"]
UPGRADE_LEVEL_3 = ["redstone P redstone", "gold chip_basic gold", "paper gold_nugget paper"]
UPGRADE_LEVEL_4 = ["redstone P redstone", "gold chip_advanced gold", "paper gold_nugget paper"]

MODULE_LEVEL_1 = [". P .", "redstone chip_fpga redstone", "iron BASE iron"]
MODULE_LEVEL_2 = ["chip_fpga P chip_fpga", "redstone chip_basic redstone", "iron BASE iron"]
MODULE_LEVEL_3 = ["chip_fpga P chip_fpga", "redstone chip_advanced redstone", "gold BASE gold"]
MODULE_LEVEL_4 = ["chip_fpga P chip_fpga", "lapis chip_basic lapis", "iron BASE iron"]
MODULE_UPGRADE_1 = ["P", "chip_fpga", "BASE"]
MODULE_UPGRADE_2 = [". P .", "redstone chip_fpga redstone", "gold BASE gold"]
MODULE_UPGRADE_5 = [". P .", "redstone chip_basic redstone", "iron BASE iron"]
MODULE_UPGRADE_6 = [". P .", "redstone chip_basic redstone", "gold BASE gold"]
MODULE_ADVANCED_1 = ["chip_fpga P chip_fpga", "lapis chip_basic lapis", "gold BASE gold"]
MODULE_ADVANCED_4 = ["chip_basic P chip_basic", "blaze_powder chip_advanced blaze_powder", "gold BASE gold"]

# routed pipes are turned back into the basic pipe of their kind, a module on its own loses its settings
ITEM_PIPES = ["provider_pipe", "request_pipe", "request_pipe_mk2", "supplier_pipe", "crafting_pipe", "satellite_pipe",
              "firewall_pipe", "chassis_pipe_mk1", "chassis_pipe_mk2", "chassis_pipe_mk3", "chassis_pipe_mk4",
              "chassis_pipe_mk5", "inventory_system_connector_pipe", "system_entrance_pipe", "system_destination_pipe"]
FLUID_PIPES = ["fluid_insertion_pipe", "fluid_extractor_pipe", "fluid_provider_pipe", "fluid_terminus_pipe",
               "fluid_request_pipe", "fluid_satellite_pipe", "fluid_supplier_pipe", "fluid_container_supplier_pipe"]
MODULES_WITH_SETTINGS = ["item_sink", "provider", "terminus", "passive_supplier", "advanced_extractor", "mod_item_sink",
                         "tag_item_sink", "creative_tab_item_sink", "enchantment_sink_mk2", "crafter", "active_supplier"]

RECIPES = [
    # chips, with gold where no mod brings copper
    shaped("chip_basic_raw", ["sand copper", "copper sand"], count=2, when=HAS_COPPER),
    shaped("chip_basic_raw", ["sand gold", "gold sand"], count=2, file="chip_basic_raw_nocopper", when=NO_COPPER),
    shaped("chip_advanced_raw", ["chip_basic_raw diamond", "diamond chip_basic_raw"], count=2),
    shaped("chip_fpga_raw", ["sand diamond sand", "gold redstone gold", "sand lapis sand"], count=16),
    smelted("chip_basic", "chip_basic_raw"),
    smelted("chip_advanced", "chip_advanced_raw"),
    smelted("chip_fpga", "chip_fpga_raw"),
    shaped("module_blank", ["redstone", "paper", "gold_nugget"]),
    shaped("frame", ["iron redstone iron", ". . .", "planks . planks"]),
    shaped("program_compiler", [". chip_basic .", "redstone frame redstone", "iron lapis iron"]),
    shaped("logistics_programmer", [". chip_advanced .", "iron chip_fpga iron", "gold module_blank gold"]),
    shaped("disk", ["iron redstone iron", "redstone gold_nugget redstone", "iron redstone iron"]),

    # pipes and tubes that need no program
    shaped("transport_pipe", ["iron redstone iron", "glass . glass", "iron redstone iron"], count=8),
    shaped("basic_logistics_pipe", ["chip_fpga", "transport_pipe"]),
    shaped("line_tube", ["transport_pipe transport_pipe transport_pipe"], count=3),
    shaped("speedup_tube", ["line_tube line_tube line_tube"]),
    shaped("curve_tube", ["line_tube line_tube line_tube", "line_tube . .", "line_tube . ."]),
    shaped("s_curve_tube", ["line_tube line_tube .", ". line_tube line_tube"]),
    shaped("gain_tube", ["line_tube .", "line_tube line_tube", ". line_tube"]),

    # blocks
    shaped("power_junction", [". chip_basic .", "redstone frame redstone", "iron redstone_block iron"]),
    shaped("security_station", [". chip_advanced .", "redstone frame redstone", "iron redstone iron"]),
    shaped("statistics_table", [". chip_advanced .", "redstone frame redstone", ". iron ."]),
    shaped("logistics_crafting_table", [". crafting_table .", "planks frame planks", ". piston ."]),
    shaped("request_table", [". chip_advanced .", "request_pipe_mk2 frame crafting_pipe", ". chest ."]),

    # tools
    shaped("remote_orderer", ["chip_advanced gold chip_advanced", "redstone glass lapis", "redstone redstone lapis"]),
    shaped("pipe_controller", ["gold chip_basic gold", "redstone glass lapis", "redstone redstone lapis"]),
    shaped("guide_book", ["book transport_pipe"]),
    shaped("sign_creator", ["chip_basic . chip_basic", ". sign .", ". chip_advanced ."]),
    # no recipe in the original: iron bows, a glass in front of each eye, the chip that shows the panels on the nose
    shaped("hud_glasses", ["iron . iron", "glass chip_advanced glass"]),

    # pipes
    programmed("request_pipe", PIPE_LEVEL_2, "basic_logistics_pipe"),
    programmed("provider_pipe", PIPE_LEVEL_2, "basic_logistics_pipe"),
    programmed("crafting_pipe", PIPE_LEVEL_1, "basic_logistics_pipe"),
    programmed("satellite_pipe", PIPE_LEVEL_1, "basic_logistics_pipe"),
    programmed("supplier_pipe", PIPE_LEVEL_2, "basic_logistics_pipe"),
    programmed("request_pipe_mk2", PIPE_LEVEL_3, "request_pipe"),
    programmed("inventory_system_connector_pipe", PIPE_ENDER_1, "basic_logistics_pipe"),
    programmed("system_entrance_pipe", PIPE_LEVEL_1, "provider_pipe"),
    programmed("system_destination_pipe", PIPE_LEVEL_1, "provider_pipe"),
    programmed("firewall_pipe", PIPE_ENDER_2, "basic_logistics_pipe"),
    programmed("chassis_pipe_mk1", [". P .", ". chip_basic .", "chip_fpga basic_logistics_pipe chip_fpga"]),
    programmed("chassis_pipe_mk2", [". P .", "chip_basic chassis_pipe_mk1 chip_basic", "iron lapis iron"]),
    programmed("chassis_pipe_mk3", [". P .", "glowstone chassis_pipe_mk2 glowstone", "iron chip_advanced iron"]),
    programmed("chassis_pipe_mk4", [". P .", "chip_basic chassis_pipe_mk3 chip_basic", "gold chip_advanced gold"]),
    programmed("chassis_pipe_mk5", [". P .", "chip_advanced chassis_pipe_mk4 chip_advanced", "diamond quartz diamond"]),

    # fluid pipes, the container supplier is the fluid supplier of the original and the fluid supplier its mk2
    programmed("fluid_basic_pipe", [". P .", "chip_basic basic_logistics_pipe chip_basic", "gold bucket gold"]),
    programmed("fluid_container_supplier_pipe", [". P .", "chip_basic basic_logistics_pipe chip_basic", "iron bucket iron"]),
    programmed("fluid_terminus_pipe", [". P .", "black_dye fluid_basic_pipe purple_dye", "iron chip_basic iron"]),
    programmed("fluid_request_pipe", PIPE_LEVEL_2, "fluid_basic_pipe"),
    programmed("fluid_provider_pipe", PIPE_LEVEL_2, "fluid_basic_pipe"),
    programmed("fluid_supplier_pipe", PIPE_LEVEL_2, "fluid_container_supplier_pipe"),
    programmed("fluid_satellite_pipe", PIPE_LEVEL_1, "fluid_basic_pipe"),
    programmed("fluid_insertion_pipe", PIPE_LEVEL_1, "fluid_basic_pipe"),
    programmed("fluid_extractor_pipe", PIPE_LEVEL_1, "fluid_basic_pipe"),

    # upgrades
    programmed("upgrade_sneaky", UPGRADE_LEVEL_1),
    programmed("upgrade_speed", UPGRADE_LEVEL_1),
    programmed("upgrade_disconnection", UPGRADE_LEVEL_1, count=8),
    programmed("upgrade_advanced_satellite", UPGRADE_LEVEL_2),
    programmed("upgrade_crafting_byproduct", UPGRADE_LEVEL_1),
    programmed("upgrade_fluid_crafting", UPGRADE_LEVEL_1),
    programmed("upgrade_power_transportation", UPGRADE_LEVEL_1),
    programmed("upgrade_crafting_cleanup", UPGRADE_LEVEL_2),
    programmed("upgrade_module_upgrade", UPGRADE_LEVEL_4),
    programmed("upgrade_action_speed", UPGRADE_LEVEL_3),
    programmed("upgrade_item_extraction", UPGRADE_LEVEL_3),
    programmed("upgrade_item_stack_extraction", UPGRADE_LEVEL_4),

    # modules
    programmed("module_item_sink", MODULE_LEVEL_1, "module_blank"),
    programmed("module_passive_supplier", MODULE_LEVEL_1, "module_blank"),
    programmed("module_extractor", MODULE_LEVEL_2, "module_blank"),
    programmed("module_polymorphic_item_sink", MODULE_LEVEL_1, "module_blank"),
    programmed("module_quick_sort", MODULE_LEVEL_3, "module_blank"),
    programmed("module_terminus", MODULE_LEVEL_1, "module_blank"),
    programmed("module_advanced_extractor", MODULE_UPGRADE_1, "module_extractor"),
    programmed("module_provider", MODULE_LEVEL_4, "module_blank"),
    programmed("module_mod_item_sink", MODULE_UPGRADE_2, "module_blank"),
    programmed("module_tag_item_sink", MODULE_UPGRADE_2, "module_blank"),
    programmed("module_enchantment_sink", MODULE_UPGRADE_5, "module_item_sink"),
    programmed("module_enchantment_sink_mk2", MODULE_UPGRADE_6, "module_enchantment_sink"),
    programmed("module_creative_tab_item_sink", MODULE_UPGRADE_2, "module_blank"),
    programmed("module_crafter", MODULE_ADVANCED_1, "module_blank"),
    programmed("module_active_supplier", MODULE_ADVANCED_4, "module_blank"),
    programmed("module_bulk_storage_sink", MODULE_UPGRADE_2, "module_polymorphic_item_sink"),

    # no recipe in the original, or none that needs a program
    shapeless("frequency_card", ["paper", "redstone", "gold_nugget"], count=2),
    shapeless("settings_copier", ["paper", "redstone", "ink_sac"]),
    shapeless("power_provider_rf", ["redstone_block", "basic_logistics_pipe", "copper_block"]),
    shapeless("upgrade_cc_remote_control", ["paper", "redstone", "gold", "comparator"]),
    shapeless("upgrade_fuzzy", ["paper", "redstone", "iron", "wheat"]),
    shapeless("upgrade_opaque", ["paper", "redstone", "iron", "black_dye"]),
    shapeless("upgrade_pattern", ["paper", "redstone", "gold", "supplier_pipe"]),
    shapeless("upgrade_power_supplier_rf", ["paper", "redstone", "iron", "copper"]),
    shapeless("upgrade_sneaky_combination", ["upgrade_sneaky", "upgrade_sneaky", "gold", "slime_ball"]),
    shapeless("void_pipe", ["basic_logistics_pipe", "ink_sac", "redstone"]),

    shapeless("basic_logistics_pipe", [ITEM_PIPES], file="basic_logistics_pipe_from_pipes"),
    shapeless("fluid_basic_pipe", [FLUID_PIPES], file="fluid_basic_pipe_from_pipes"),
    *[shapeless("module_" + module, ["module_" + module], file=f"module_{module}_reset") for module in MODULES_WITH_SETTINGS],
]


def main():
    written = set()
    for file, recipe in RECIPES:
        if file in written:
            raise ValueError(f"two recipes want the file {file}")
        written.add(file)
        os.makedirs(RECIPES_DIR, exist_ok=True)
        with open(RECIPES_DIR + file + ".json", "w") as out:
            out.write(json.dumps(recipe, indent=2) + "\n")


if __name__ == "__main__":
    main()
