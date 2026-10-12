#!/usr/bin/env python3
"""Writes blockstates, models and loot tables for all blocks. Run from the project root after adding one.

Pipes are cut out of the original mod's pipe model in scripts/source and loaded with the NeoForge obj loader.
"""
import json
import os

PIPES = ["transport_pipe", "basic_logistics_pipe", "provider_pipe", "request_pipe", "supplier_pipe",
         "crafting_pipe", "satellite_pipe", "firewall_pipe"] + [f"chassis_pipe_mk{tier}" for tier in range(1, 6)]
MODULES = ["module_item_sink", "module_extractor", "module_provider", "module_terminus", "module_passive_supplier",
           "module_polymorphic_item_sink", "module_quick_sort", "module_advanced_extractor", "module_mod_item_sink",
           "module_tag_item_sink", "module_creative_tab_item_sink", "module_enchantment_sink", "module_enchantment_sink_mk2",
           "module_crafter", "module_active_supplier"]
ASSETS = "src/main/resources/assets/logisticspipes/"
DATA = "src/main/resources/data/logisticspipes/"

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


def write(path, content):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as file:
        file.write(json.dumps(content, indent=2) + "\n")


def read_model():
    """Returns vertices, texture coordinates and the faces of every named mesh."""
    vertices, coords, meshes, current = [], [], {}, None
    with open(MODEL) as file:
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


def write_part(path, vertices, coords, mesh_lists, material, icon=False):
    """One obj file with the given meshes. The model brings its own back faces where they can be seen."""
    lines = ["mtllib pipe.mtl", "o part", f"usemtl {material}"]
    vertex_index, coord_index, faces_out = {}, {}, []
    for faces in mesh_lists:
        for face in faces:
            corners = []
            for vertex, coord in face:
                u, v = coords[coord - 1]
                if icon:
                    # the symbol sits in the upper left 12 of 16 pixels of its texture
                    u, v = u * 0.75, 1 - (1 - v) * 0.75
                if vertex not in vertex_index:
                    vertex_index[vertex] = len(vertex_index) + 1
                key = (round(u, 6), round(v, 6))
                if key not in coord_index:
                    coord_index[key] = len(coord_index) + 1
                corners.append((vertex_index[vertex], coord_index[key]))
            # the loader takes triangles and quads only
            for start in range(1, len(corners) - 1, 2):
                polygon = [corners[0]] + corners[start:start + 3]
                faces_out.append(polygon)
    for vertex in vertex_index:
        x, y, z = vertices[vertex - 1]
        if icon:
            # a hair outside the frame like in the original, a support runs through the same plane
            x, y, z = ((value - 0.5) * 1.001 + 0.5 for value in (x, y, z))
        lines.append(f"v {x:.6f} {y:.6f} {z:.6f}")
    for u, v in coord_index:
        lines.append(f"vt {u:.6f} {v:.6f}")
    for polygon in faces_out:
        lines.append("f " + " ".join(f"{vertex}/{coord}" for vertex, coord in polygon))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as file:
        file.write("\n".join(lines) + "\n")


def part_model(name, textures):
    return {"loader": "neoforge:obj", "model": f"logisticspipes:models/block/pipe/{name}.obj", "flip_v": True,
            "automatic_culling": False, "render_type": "minecraft:cutout", "textures": textures}


def write_parts(vertices, coords, meshes):
    """Writes every part once and returns the blockstate entries that are the same for all pipes."""
    folder = ASSETS + "models/block/pipe/"
    with open(folder + "pipe.mtl", "w") as file:
        file.write("newmtl frame\nmap_Kd #frame\nnewmtl icon\nmap_Kd #icon\n")
    frame = {"frame": "logisticspipes:block/pipe_frame", "particle": "logisticspipes:block/pipe_frame"}
    shared, item_frame = [], []

    def frame_part(name, mesh_lists, when):
        write_part(folder + name + ".obj", vertices, coords, mesh_lists, "frame")
        write(folder + name + ".json", part_model(name, frame))
        shared.append({"when": when, "apply": {"model": f"logisticspipes:block/pipe/{name}"}})

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
        frame_part(f"corner_{tag}", named(meshes, "Corner_M_" + letters), {side: "false" for side in corner})
        item_frame += named(meshes, "Corner_M_" + letters)
        # all three neighbours
        frame_part(f"corner_all_{tag}", named(meshes, "Corner_I3_" + letters), {side: "true" for side in corner})
        for axis, side in enumerate(corner):
            others = [other for other in corner if other != side]
            # one neighbour: a spacer along its side
            number = SPACERS[corner][{0: 2, 1: 0, 2: 1}[axis]]
            frame_part(f"spacer_{tag}_{side}", named(meshes, f"Spacer{number}")[:1],
                       {side: "true", others[0]: "false", others[1]: "false"})
            # two neighbours: the inner corner that points at the side without one
            frame_part(f"corner_inner_{tag}_{side}", corner_i(meshes, corner, axis),
                       {side: "false", others[0]: "true", others[1]: "true"})
    for (first, second), names in SUPPORTS.items():
        when = {side: ("true" if side in (first, second) else "false") for side in SIDES}
        for support in names:
            frame_part(f"support_{first}_{support.lower()}", named(meshes, "Support_" + support)[:1], when)
    return shared, item_frame


def blockstate(name, shared):
    parts = list(shared)
    for side in SIDES:
        parts.append({"when": {side: "true"}, "apply": {"model": f"logisticspipes:block/{name}_side_{side}"}})
        parts.append({"when": {side: "false"}, "apply": {"model": f"logisticspipes:block/{name}_plate_{side}"}})
    return {"multipart": parts}


def main():
    vertices, coords, meshes = read_model()
    folder = ASSETS + "models/block/pipe/"
    os.makedirs(folder, exist_ok=True)
    shared, item_frame = write_parts(vertices, coords, meshes)
    for side in SIDES:
        write_part(folder + f"side_{side}.obj", vertices, coords, named(meshes, "Side_" + LETTER[side]), "frame")
        plates = [faces for name, faces in meshes.items() if " Texture_Plate_" + LETTER[side] in name]
        write_part(folder + f"plate_{side}.obj", vertices, coords, plates, "icon", icon=True)
    # what a pipe looks like in the hand: the frame of an unconnected pipe with its symbol on every side
    write_part(folder + "item_frame.obj", vertices, coords, item_frame, "frame")
    for name in PIPES:
        # the arms of routed pipes carry the green routing lights
        frame = "logisticspipes:block/pipe_frame" if name == "transport_pipe" else "logisticspipes:block/pipe_frame_routed"
        icon = f"logisticspipes:block/{name}_icon"
        for side in SIDES:
            write(ASSETS + f"models/block/{name}_side_{side}.json", part_model(f"side_{side}", {"frame": frame, "particle": frame}))
            write(ASSETS + f"models/block/{name}_plate_{side}.json", part_model(f"plate_{side}", {"icon": icon, "particle": icon}))
        write(ASSETS + f"models/item/{name}.json", {"parent": "minecraft:block/block", "loader": "neoforge:composite", "children": {
            "frame": part_model("item_frame", {"frame": "logisticspipes:block/pipe_frame"}),
            **{f"plate_{side}": part_model(f"plate_{side}", {"icon": icon}) for side in SIDES}},
            "textures": {"particle": "logisticspipes:block/pipe_frame"}})
        write(ASSETS + f"blockstates/{name}.json", blockstate(name, shared))
    upgrades = ["sneaky", "speed", "module_upgrade", "action_speed", "item_extraction", "item_stack_extraction",
                "crafting_byproduct", "crafting_cleanup", "disconnection"]
    for module in MODULES + ["remote_orderer", "pipe_controller", "settings_copier"] + ["upgrade_" + upgrade for upgrade in upgrades]:
        write(ASSETS + f"models/item/{module}.json",
              {"parent": "minecraft:item/generated", "textures": {"layer0": f"logisticspipes:item/{module}"}})
    cubes = ["logistics_crafting_table", "power_junction"]
    for cube in cubes:
        write(ASSETS + f"models/block/{cube}.json", {"parent": "minecraft:block/cube_bottom_top", "textures": {
            "top": f"logisticspipes:block/{cube}_top", "side": f"logisticspipes:block/{cube}_side",
            "bottom": f"logisticspipes:block/{cube}_bottom"}})
        write(ASSETS + f"models/item/{cube}.json", {"parent": f"logisticspipes:block/{cube}"})
        write(ASSETS + f"blockstates/{cube}.json", {"variants": {"": {"model": f"logisticspipes:block/{cube}"}}})
    # the request table is a pipe underneath, so its state has the connection properties and one model for all of them
    table = "request_table"
    write(ASSETS + f"models/block/{table}.json", {"parent": "minecraft:block/cube_bottom_top", "textures": {
        "top": f"logisticspipes:block/{table}_top", "side": f"logisticspipes:block/{table}_side",
        "bottom": f"logisticspipes:block/{table}_bottom"}})
    write(ASSETS + f"models/item/{table}.json", {"parent": f"logisticspipes:block/{table}"})
    write(ASSETS + f"blockstates/{table}.json", {"multipart": [{"apply": {"model": f"logisticspipes:block/{table}"}}]})
    cubes.append(table)
    for name in PIPES + cubes:
        write(DATA + f"loot_table/blocks/{name}.json", {"type": "minecraft:block", "pools": [{
            "rolls": 1, "entries": [{"type": "minecraft:item", "name": f"logisticspipes:{name}"}],
            "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    write("src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json",
          {"values": [f"logisticspipes:{name}" for name in PIPES + cubes]})


if __name__ == "__main__":
    main()
