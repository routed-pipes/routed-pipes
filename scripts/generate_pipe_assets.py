#!/usr/bin/env python3
"""Writes blockstates, models and loot tables for all blocks. Run from the project root after adding one.

Pipes are cut out of the original mod's pipe model in scripts/source and loaded with the NeoForge obj loader.
"""
import itertools
import json
import os

PIPES = ["transport_pipe", "basic_logistics_pipe", "provider_pipe", "request_pipe", "request_pipe_mk2", "supplier_pipe",
         "crafting_pipe", "satellite_pipe", "firewall_pipe"] + [f"chassis_pipe_mk{tier}" for tier in range(1, 6)] + [
             "fluid_basic_pipe", "fluid_insertion_pipe", "fluid_extractor_pipe", "fluid_provider_pipe", "fluid_terminus_pipe", "fluid_request_pipe", "fluid_satellite_pipe", "fluid_supplier_pipe", "fluid_container_supplier_pipe",
             "system_entrance_pipe", "system_destination_pipe", "void_pipe"]
# these show on their sides whether they have found the others of their channel, one symbol per state of the block
CONNECTORS = ["inventory_system_connector_pipe"]
LINKS = ["disconnected", "mismatched", "connected"]
MODULES = ["module_item_sink", "module_extractor", "module_provider", "module_terminus", "module_passive_supplier",
           "module_polymorphic_item_sink", "module_quick_sort", "module_advanced_extractor", "module_mod_item_sink",
           "module_tag_item_sink", "module_creative_tab_item_sink", "module_enchantment_sink", "module_enchantment_sink_mk2",
           "module_crafter", "module_active_supplier", "module_bulk_storage_sink"]
# high speed tubes: the model of the original, the texture it is drawn with
# and the quarter turns that make the model face north the way the game counts the blocks of the tube
TUBES = {"line_tube": ("tube_line", "line_tube", 0), "speedup_tube": ("tube_speedup", "speedup_tube", 0),
         "curve_tube": ("tube_turn", "tube", 2), "s_curve_tube": ("tube_gain", "tube", 0), "gain_tube": ("tube_gain", "tube", 0)}
FACINGS = ["north", "east", "south", "west"]
OPPOSITE = {"north": "south", "south": "north", "east": "west", "west": "east"}
ASSETS = "src/main/resources/assets/routedpipes/"
DATA = "src/main/resources/data/routedpipes/"

# The pipe model of the original mod, about 500 named meshes. Which of them are drawn depends on the sides
# a pipe is connected on, the rules below are the ones of the original renderer.
MODEL = "scripts/source/pipe_model.obj"
LETTER = {"north": "N", "south": "S", "east": "E", "west": "W", "up": "U", "down": "D"}
SIDES = list(LETTER)
EDGES = [("up", "north"), ("up", "south"), ("up", "east"), ("up", "west"), ("down", "north"), ("down", "south"),
         ("down", "east"), ("down", "west"), ("north", "west"), ("north", "east"), ("south", "east"), ("south", "west")]
CORNERS = [(ud, ns, ew) for ud in ("up", "down") for ns in ("north", "south") for ew in ("west", "east")]
# the original numbers its spacers per corner and axis
SPACERS = {("up", "north", "west"): (1, 14, 23), ("up", "north", "east"): (2, 9, 22), ("up", "south", "west"): (6, 13, 24),
           ("up", "south", "east"): (5, 10, 21), ("down", "north", "west"): (4, 15, 20), ("down", "north", "east"): (3, 12, 17),
           ("down", "south", "west"): (7, 16, 19), ("down", "south", "east"): (8, 11, 18)}
# a straight run gets four supports, named by side and orientation
SUPPORTS = {("down", "up"): ["E_S", "W_S", "N_S", "S_S"], ("north", "south"): ["E_U", "W_U", "U_S", "D_S"],
            ("west", "east"): ["U_U", "D_U", "N_U", "S_U"]}
# where the arm of a routed pipe finds its red lights on the texture, as u and v
RED_LIGHTS = (0, -0.37)


def write(path, content):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as file:
        file.write(json.dumps(content, indent=2) + "\n")


def read_model(path=MODEL):
    """Returns vertices, texture coordinates and the faces of every named mesh."""
    vertices, coords, meshes, current = [], [], {}, None
    with open(path) as file:
        for line in file:
            parts = line.split()
            if not parts:
                continue
            if parts[0] == "v":
                # the model is 100 units wide and sits on the wrong side of the z axis
                vertices.append((float(parts[1]) / 100, float(parts[2]) / 100, float(parts[3]) / 100 + 1))
            elif parts[0] == "vt":
                coords.append((float(parts[1]), float(parts[2])))
            elif parts[0] in ("g", "o"):
                current = meshes.setdefault(" ".join(parts[1:]), [])
            elif parts[0] == "f":
                current.append([tuple(int(index) for index in corner.split("/")[:2]) for corner in parts[1:]])
    return vertices, coords, meshes


def named(meshes, group):
    """Meshes that carry the group name as a whole word, the way the original looks them up."""
    return [faces for name, faces in meshes.items() if f" {group} " in name + " "]


def corner_i(meshes, corner, axis):
    """The inner corner pieces share a name and differ by a digit: none for up/down, 1 for east/west, 2 for north/south."""
    group = " Corner_I_" + LETTER[corner[0]] + "_" + LETTER[corner[1]] + LETTER[corner[2]]
    wanted = {0: "", 1: "2", 2: "1"}[axis]
    for name, faces in meshes.items():
        if group in name:
            rest = name[name.index(group) + len(group):]
            suffix = rest[:1] if rest[:1].isdigit() else ""
            if suffix == wanted:
                return [faces]
    raise ValueError(f"no inner corner for {corner} axis {axis}")


def write_part(path, vertices, coords, mesh_lists, material, icon=False, flip=False, shift=(0, 0), swell=1):
    """One obj file with the given meshes. The model brings its own back faces where they can be seen.

    Flip is for vertices that were mirrored, their faces would look the wrong way otherwise.
    Shift moves the part to another place on its texture. Swell makes it larger around the middle of the block."""
    lines = ["mtllib pipe.mtl", "o part", f"usemtl {material}"]
    vertex_index, coord_index, faces_out = {}, {}, []
    for faces in mesh_lists:
        for face in faces:
            corners = []
            for vertex, coord in face:
                u, v = coords[coord - 1]
                if shift != (0, 0):
                    u, v = u + shift[0], v + shift[1]
                if icon:
                    # the symbol sits in the upper left 12 of 16 pixels of its texture
                    u, v = u * 0.75, 1 - (1 - v) * 0.75
                if vertex not in vertex_index:
                    vertex_index[vertex] = len(vertex_index) + 1
                key = (round(u, 6), round(v, 6))
                if key not in coord_index:
                    coord_index[key] = len(coord_index) + 1
                corners.append((vertex_index[vertex], coord_index[key]))
            if flip:
                corners.reverse()
            # the loader takes triangles and quads only
            for start in range(1, len(corners) - 1, 2):
                polygon = [corners[0]] + corners[start:start + 3]
                faces_out.append(polygon)
    for vertex in vertex_index:
        x, y, z = vertices[vertex - 1]
        if icon:
            # a hair outside the frame like in the original, a support runs through the same plane
            x, y, z = ((value - 0.5) * 1.001 + 0.5 for value in (x, y, z))
        if swell != 1:
            x, y, z = ((value - 0.5) * swell + 0.5 for value in (x, y, z))
        lines.append(f"v {x:.6f} {y:.6f} {z:.6f}")
    for u, v in coord_index:
        lines.append(f"vt {u:.6f} {v:.6f}")
    for polygon in faces_out:
        lines.append("f " + " ".join(f"{vertex}/{coord}" for vertex, coord in polygon))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as file:
        file.write("\n".join(lines) + "\n")


def part_model(name, textures):
    return {"loader": "neoforge:obj", "model": f"routedpipes:models/block/pipe/{name}.obj", "flip_v": True,
            "automatic_culling": False, "render_type": "minecraft:cutout", "textures": textures}


def write_parts(vertices, coords, meshes):
    """Writes every part once and returns the blockstate entries that are the same for all pipes."""
    folder = ASSETS + "models/block/pipe/"
    with open(folder + "pipe.mtl", "w") as file:
        file.write("newmtl frame\nmap_Kd #frame\nnewmtl icon\nmap_Kd #icon\n")
    frame = {"frame": "routedpipes:block/pipe_frame", "particle": "routedpipes:block/pipe_frame"}
    dark = {"frame": "routedpipes:block/pipe_frame_inactive", "particle": "routedpipes:block/pipe_frame_inactive"}
    shared, item_frame = [], []

    def frame_part(name, mesh_lists, when, corner=False):
        write_part(folder + name + ".obj", vertices, coords, mesh_lists, "frame")
        write(folder + name + ".json", part_model(name, frame))
        if corner:
            # the pieces around the corners go dark with the pipe
            write(folder + name + "_dark.json", part_model(name, dark))
        shared.append({"when": when, "apply": {"model": f"routedpipes:block/pipe/{name}"}, "corner": corner})

    for first, second in EDGES:
        if first in ("up", "down"):
            group = f"Edge_M_{LETTER[first]}_{LETTER[second]}"
        else:
            group = f"Edge_M_S_{LETTER[first]}{LETTER[second]}"
        frame_part(f"edge_{first}_{second}", named(meshes, group)[:1], {first: "false", second: "false"})
        item_frame += named(meshes, group)[:1]
    for corner in CORNERS:
        letters = LETTER[corner[0]] + "_" + LETTER[corner[1]] + LETTER[corner[2]]
        tag = "_".join(corner)
        # no neighbour at this corner, the plain corner piece
        frame_part(f"corner_{tag}", named(meshes, "Corner_M_" + letters), {side: "false" for side in corner}, corner=True)
        item_frame += named(meshes, "Corner_M_" + letters)
        # all three neighbours
        frame_part(f"corner_all_{tag}", named(meshes, "Corner_I3_" + letters), {side: "true" for side in corner}, corner=True)
        for axis, side in enumerate(corner):
            others = [other for other in corner if other != side]
            # one neighbour: a spacer along its side
            number = SPACERS[corner][{0: 2, 1: 0, 2: 1}[axis]]
            frame_part(f"spacer_{tag}_{side}", named(meshes, f"Spacer{number}")[:1],
                       {side: "true", others[0]: "false", others[1]: "false"}, corner=True)
            # two neighbours: the inner corner that points at the side without one
            frame_part(f"corner_inner_{tag}_{side}", corner_i(meshes, corner, axis),
                       {side: "false", others[0]: "true", others[1]: "true"}, corner=True)
    for (first, second), names in SUPPORTS.items():
        when = {side: ("true" if side in (first, second) else "false") for side in SIDES}
        for support in names:
            frame_part(f"support_{first}_{support.lower()}", named(meshes, "Support_" + support)[:1], when)
    # the mounts are no part of any blockstate, the game adds them by the blocks around a pipe, see PipeMounts
    for towards, flank in itertools.permutations(SIDES, 2):
        mount = named(meshes, f"Mount_{LETTER[towards]}_{LETTER[flank]}")[:1]
        if mount:
            name = f"mount_{towards}_{flank}"
            # a mount lies flat on the edge it holds, a hair larger it does not flicker with it
            # and the two in the corner of two walls lie in each other, so they are not the same hair larger
            write_part(folder + name + ".obj", vertices, coords, mount, "frame",
                       swell=1.0045 if towards in ("east", "west") else 1.0005)
            write(folder + name + ".json", part_model(name, frame))
    return shared, item_frame


def arms(when):
    """A side of a pipe block is none, unrouted or routed, the frame only cares whether there is an arm."""
    return {side: "unrouted|routed" if value == "true" else "none" for side, value in when.items()}


def junction(when):
    """Splits the states a piece of a plain pipe is drawn in: with up to two arms on the pipe, and with more."""
    others = [side for side in SIDES if side not in when]
    # this many of the other sides need an arm to make it more than two
    needed = 3 - list(when.values()).count("true")
    if needed <= 0:
        return [], [arms(when)]
    few = [arms({**when, **{side: "false" for side in free}}) for free in itertools.combinations(others, len(others) - needed + 1)]
    many = [arms({**when, **{side: "true" for side in taken}}) for taken in itertools.combinations(others, needed)]
    return few, many


def blockstate(name, shared):
    parts = []
    for entry in shared:
        model = entry["apply"]["model"]
        if not entry["corner"]:
            parts.append({"when": arms(entry["when"]), "apply": {"model": model}})
        elif name == "transport_pipe":
            # a plain pipe that joins more than two pipes has the dark frame
            for states, apply in zip(junction(entry["when"]), (model, model + "_dark")):
                if states:
                    parts.append({"when": states[0] if len(states) == 1 else {"OR": states}, "apply": {"model": apply}})
        else:
            # a routed pipe has it while its network is without power
            parts.append({"when": {**arms(entry["when"]), "powered": "true"}, "apply": {"model": model}})
            parts.append({"when": {**arms(entry["when"]), "powered": "false"}, "apply": {"model": model + "_dark"}})
    for side in SIDES:
        arm = {"model": f"routedpipes:block/{name}_side_{side}"}
        if name == "transport_pipe":
            parts.append({"when": arms({side: "true"}), "apply": arm})
        else:
            # green where the arm leads to another routed pipe, red where it does not
            parts.append({"when": {side: "routed"}, "apply": arm})
            parts.append({"when": {side: "unrouted"}, "apply": {"model": f"routedpipes:block/pipe/side_{side}_unrouted"}})
        parts.append({"when": arms({side: "false"}), "apply": {"model": f"routedpipes:block/{name}_plate_{side}"}})
    return {"multipart": parts}


def connector_blockstate(name, shared):
    """Like any routed pipe, but the plate on an open side depends on the link state."""
    parts = []
    for part in blockstate(name, shared)["multipart"]:
        model = part["apply"]["model"]
        side = model.rsplit("_", 1)[-1]
        if model != f"routedpipes:block/{name}_plate_{side}":
            parts.append(part)
            continue
        for link in LINKS:
            parts.append({"when": {**part["when"], "link": link},
                          "apply": {"model": f"routedpipes:block/{name}_plate_{link}_{side}"}})
    return {"multipart": parts}


def turned(vertices, turns):
    """Quarter turns around the middle of the main block, seen from above north goes to east."""
    for _ in range(turns):
        vertices = [(1 - z, y, x) for x, y, z in vertices]
    return vertices


def fitted(vertices):
    """Shrunk into one block, a whole tube is too long for a hand."""
    low = [min(vertex[axis] for vertex in vertices) for axis in range(3)]
    high = [max(vertex[axis] for vertex in vertices) for axis in range(3)]
    scale = 1 / max(high[axis] - low[axis] for axis in range(3))
    return [tuple(0.5 + (vertex[axis] - (low[axis] + high[axis]) / 2) * scale for axis in range(3)) for vertex in vertices]


def pipe_end(shared, facing):
    """The main block of a speedup tube is a piece of pipe: straight through, with an arm for the pipe behind it."""
    back = OPPOSITE[facing]
    state = {side: "true" if side in (facing, back) else "false" for side in SIDES}
    models = [entry["apply"] for entry in shared if all(state[side] == value for side, value in entry["when"].items())]
    models.append({"model": f"routedpipes:block/transport_pipe_side_{back}"})
    return [{"when": {"facing": facing}, "apply": model} for model in models]


def write_tubes(shared):
    """A tube is drawn as a whole by its main block, the models of the original face north."""
    folder = ASSETS + "models/block/pipe/"
    for name, (model, texture, base) in TUBES.items():
        vertices, coords, meshes = read_model(f"scripts/source/{model}.obj")
        vertices = turned(vertices, base)
        if name == "s_curve_tube":
            # the gain tube laid on its side, what went up now goes east
            vertices = [(y, 1 - x, z) for x, y, z in vertices]
        textures = {"frame": f"routedpipes:block/{texture}", "particle": f"routedpipes:block/{texture}"}
        parts = []
        for turns, facing in enumerate(FACINGS):
            write_part(folder + f"{name}_{facing}.obj", turned(vertices, turns), coords, meshes.values(), "frame")
            write(ASSETS + f"models/block/{name}_{facing}.json", part_model(f"{name}_{facing}", textures))
            when = {"facing": facing}
            if name == "s_curve_tube":
                # and the one that goes west instead
                mirrored = [(1 - x, y, z) for x, y, z in vertices]
                write_part(folder + f"{name}_{facing}_mirrored.obj", turned(mirrored, turns), coords, meshes.values(), "frame",
                           flip=True)
                write(ASSETS + f"models/block/{name}_{facing}_mirrored.json", part_model(f"{name}_{facing}_mirrored", textures))
                parts.append({"when": {"facing": facing, "mirrored": "true"},
                              "apply": {"model": f"routedpipes:block/{name}_{facing}_mirrored"}})
                when = {"facing": facing, "mirrored": "false"}
            parts.append({"when": when, "apply": {"model": f"routedpipes:block/{name}_{facing}"}})
            if name == "speedup_tube":
                parts += pipe_end(shared, facing)
        write(ASSETS + f"blockstates/{name}.json", {"multipart": parts})
        write_part(folder + f"{name}_item.obj", fitted(vertices), coords, meshes.values(), "frame")
        write(ASSETS + f"models/item/{name}.json", {"parent": "minecraft:block/block", **part_model(f"{name}_item", textures)})
    # the other blocks of a tube are not drawn, this is only for the bits flying off when one breaks
    write(ASSETS + "models/block/tube_part.json", {"textures": {"particle": "routedpipes:block/line_tube"}})
    write(ASSETS + "blockstates/tube_part.json", {"variants": {"": {"model": "routedpipes:block/tube_part"}}})


def main():
    vertices, coords, meshes = read_model()
    folder = ASSETS + "models/block/pipe/"
    os.makedirs(folder, exist_ok=True)
    shared, item_frame = write_parts(vertices, coords, meshes)
    for side in SIDES:
        write_part(folder + f"side_{side}.obj", vertices, coords, named(meshes, "Side_" + LETTER[side]), "frame")
        # the red lights are the same piece of the texture 37 of 100 pixels further down
        write_part(folder + f"side_{side}_unrouted.obj", vertices, coords, named(meshes, "Side_" + LETTER[side]), "frame",
                   shift=RED_LIGHTS)
        routed = {"frame": "routedpipes:block/pipe_frame_routed", "particle": "routedpipes:block/pipe_frame_routed"}
        write(folder + f"side_{side}_unrouted.json", part_model(f"side_{side}_unrouted", routed))
        plates = [faces for name, faces in meshes.items() if " Texture_Plate_" + LETTER[side] in name]
        write_part(folder + f"plate_{side}.obj", vertices, coords, plates, "icon", icon=True)
    # what a pipe looks like in the hand: the frame of an unconnected pipe with its symbol on every side
    write_part(folder + "item_frame.obj", vertices, coords, item_frame, "frame")
    for name in PIPES:
        # the arms of routed pipes carry the green routing lights
        frame = "routedpipes:block/pipe_frame" if name == "transport_pipe" else "routedpipes:block/pipe_frame_routed"
        icon = f"routedpipes:block/{name}_icon"
        for side in SIDES:
            write(ASSETS + f"models/block/{name}_side_{side}.json", part_model(f"side_{side}", {"frame": frame, "particle": frame}))
            write(ASSETS + f"models/block/{name}_plate_{side}.json", part_model(f"plate_{side}", {"icon": icon, "particle": icon}))
        write(ASSETS + f"models/item/{name}.json", {"parent": "minecraft:block/block", "loader": "neoforge:composite", "children": {
            "frame": part_model("item_frame", {"frame": "routedpipes:block/pipe_frame"}),
            **{f"plate_{side}": part_model(f"plate_{side}", {"icon": icon}) for side in SIDES}},
            "textures": {"particle": "routedpipes:block/pipe_frame"}})
        write(ASSETS + f"blockstates/{name}.json", blockstate(name, shared))
    routed = "routedpipes:block/pipe_frame_routed"
    for name in CONNECTORS:
        for side in SIDES:
            write(ASSETS + f"models/block/{name}_side_{side}.json", part_model(f"side_{side}", {"frame": routed, "particle": routed}))
            for link in LINKS:
                icon = f"routedpipes:block/{name}_{link}_icon"
                write(ASSETS + f"models/block/{name}_plate_{link}_{side}.json",
                      part_model(f"plate_{side}", {"icon": icon, "particle": icon}))
        # in the hand it looks connected
        icon = f"routedpipes:block/{name}_connected_icon"
        write(ASSETS + f"models/item/{name}.json", {"parent": "minecraft:block/block", "loader": "neoforge:composite", "children": {
            "frame": part_model("item_frame", {"frame": "routedpipes:block/pipe_frame"}),
            **{f"plate_{side}": part_model(f"plate_{side}", {"icon": icon}) for side in SIDES}},
            "textures": {"particle": "routedpipes:block/pipe_frame"}})
        write(ASSETS + f"blockstates/{name}.json", connector_blockstate(name, shared))
    write_tubes(shared)
    upgrades = ["sneaky", "speed", "module_upgrade", "action_speed", "item_extraction", "item_stack_extraction",
                "crafting_byproduct", "crafting_cleanup", "disconnection", "advanced_satellite", "power_supplier_rf",
                "power_transportation", "opaque", "cc_remote_control", "sneaky_combination", "pattern", "fuzzy",
                "fluid_crafting"]
    parts = ["chip_basic_raw", "chip_basic", "chip_advanced_raw", "chip_advanced", "chip_fpga_raw", "chip_fpga", "module_blank",
             "logistics_programmer"]
    for module in MODULES + ["remote_orderer", "pipe_controller", "settings_copier", "security_card", "disk", "frequency_card", "guide_book", "hud_glasses"] + ["upgrade_" + upgrade for upgrade in upgrades] + parts:
        write(ASSETS + f"models/item/{module}.json",
              {"parent": "minecraft:item/generated", "textures": {"layer0": f"routedpipes:item/{module}"}})
    # the sign creator looks like the kind of sign it makes
    write(ASSETS + "models/item/sign_creator.json", {"parent": "minecraft:item/generated",
          "textures": {"layer0": "routedpipes:item/sign_creator_crafting"},
          "overrides": [{"predicate": {"routedpipes:sign_mode": 1}, "model": "routedpipes:item/sign_creator_item_amount"}]})
    write(ASSETS + "models/item/sign_creator_item_amount.json", {"parent": "minecraft:item/generated",
          "textures": {"layer0": "routedpipes:item/sign_creator_item_amount"}})
    # the window of the container is tinted with the colour of the fluid in it
    write(ASSETS + "models/item/fluid_container.json", {"parent": "minecraft:item/generated", "textures": {
        "layer0": "routedpipes:item/fluid_container", "layer1": "routedpipes:item/fluid_container_window"}})
    cubes = ["logistics_crafting_table", "power_junction", "power_provider_rf", "security_station", "statistics_table",
             "frame", "program_compiler"]
    for cube in cubes:
        write(ASSETS + f"models/block/{cube}.json", {"parent": "minecraft:block/cube_bottom_top", "textures": {
            "top": f"routedpipes:block/{cube}_top", "side": f"routedpipes:block/{cube}_side",
            "bottom": f"routedpipes:block/{cube}_bottom"}})
        write(ASSETS + f"models/item/{cube}.json", {"parent": f"routedpipes:block/{cube}"})
        write(ASSETS + f"blockstates/{cube}.json", {"variants": {"": {"model": f"routedpipes:block/{cube}"}}})
    # the request table is a pipe underneath, so its state has the connection properties and one model for all of them
    table = "request_table"
    write(ASSETS + f"models/block/{table}.json", {"parent": "minecraft:block/cube_bottom_top", "textures": {
        "top": f"routedpipes:block/{table}_top", "side": f"routedpipes:block/{table}_side",
        "bottom": f"routedpipes:block/{table}_bottom"}})
    write(ASSETS + f"models/item/{table}.json", {"parent": f"routedpipes:block/{table}"})
    write(ASSETS + f"blockstates/{table}.json", {"multipart": [{"apply": {"model": f"routedpipes:block/{table}"}}]})
    cubes.append(table)
    for name in PIPES + CONNECTORS + cubes + list(TUBES):
        write(DATA + f"loot_table/blocks/{name}.json", {"type": "minecraft:block", "pools": [{
            "rolls": 1, "entries": [{"type": "minecraft:item", "name": f"routedpipes:{name}"}],
            "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    write("src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json",
          {"values": [f"routedpipes:{name}" for name in PIPES + CONNECTORS + cubes + list(TUBES) + ["tube_part"]]})


if __name__ == "__main__":
    main()
