#!/usr/bin/env python3
"""Writes blockstates, models and loot tables for all blocks. Run from the project root after adding one."""
import json
import os

PIPES = ["transport_pipe", "basic_logistics_pipe", "provider_pipe", "request_pipe", "supplier_pipe",
         "crafting_pipe", "satellite_pipe"]
ASSETS = "src/main/resources/assets/logisticspipes/"
DATA = "src/main/resources/data/logisticspipes/"

# all parts are modelled on the north side and turned to the other five by the blockstate
ROTATIONS = {"north": {}, "east": {"y": 90}, "south": {"y": 180}, "west": {"y": 270}, "up": {"x": 270}, "down": {"x": 90}}
VECTORS = {"north": (0, 0, -1), "south": (0, 0, 1), "west": (-1, 0, 0), "east": (1, 0, 0), "up": (0, 1, 0), "down": (0, -1, 0)}

# pieces of the closed end face: the symbol in the middle and one frame line per edge
END_PARTS = {
    "symbol": ([5, 5, 4], [11, 11, 4]),
    "edge_up": ([4, 11, 4], [12, 12, 4]),
    "edge_down": ([4, 4, 4], [12, 5, 4]),
    "edge_east": ([11, 4, 4], [12, 12, 4]),
    "edge_west": ([4, 4, 4], [5, 12, 4]),
}


def write(path, content):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as file:
        file.write(json.dumps(content, indent=2) + "\n")


def faces(*names):
    return {name: {"texture": "#pipe"} for name in names}


def turned(direction, rotation):
    """Where a direction of the north facing part ends up after the blockstate rotation, x first and then y."""
    x, y, z = VECTORS[direction]
    for _ in range(rotation.get("x", 0) // 90):
        y, z = z, -y
    for _ in range(rotation.get("y", 0) // 90):
        x, z = -z, x
    return next(name for name, vector in VECTORS.items() if vector == (x, y, z))


def blockstate(name):
    parts = []
    for side, rotation in ROTATIONS.items():
        parts.append({"when": {side: "true"}, "apply": {"model": f"logisticspipes:block/{name}_arm", **rotation}})
        parts.append({"when": {side: "false"}, "apply": {"model": f"logisticspipes:block/{name}_symbol", **rotation}})
        for edge in ("up", "down", "east", "west"):
            # a frame line only where two closed sides meet, so straight runs stay smooth
            parts.append({"when": {side: "false", turned(edge, rotation): "false"},
                          "apply": {"model": f"logisticspipes:block/{name}_edge_{edge}", **rotation}})
    return {"multipart": parts}


def main():
    base = {"textures": {"particle": "#pipe"}, "render_type": "minecraft:cutout"}
    for part, (start, end) in END_PARTS.items():
        write(ASSETS + f"models/block/pipe_{part}.json", {**base, "elements": [{"from": start, "to": end, "faces": faces("north")}]})
    # open towards the centre and towards the neighbour
    write(ASSETS + "models/block/pipe_arm.json",
          {**base, "elements": [{"from": [4, 4, 0], "to": [12, 12, 4], "faces": faces("up", "down", "west", "east")}]})
    write(ASSETS + "models/block/pipe_inventory.json", {"parent": "minecraft:block/block", **base, "elements": [
        {"from": [4, 4, 4], "to": [12, 12, 12], "faces": faces("up", "down", "west", "east")},
        {"from": [4, 4, 0], "to": [12, 12, 4], "faces": faces("up", "down", "west", "east", "north")},
        {"from": [4, 4, 12], "to": [12, 12, 16], "faces": faces("up", "down", "west", "east", "south")}]})
    for name in PIPES:
        texture = {"pipe": f"logisticspipes:block/{name}"}
        for part in list(END_PARTS) + ["arm"]:
            write(ASSETS + f"models/block/{name}_{part}.json", {"parent": f"logisticspipes:block/pipe_{part}", "textures": texture})
        write(ASSETS + f"models/item/{name}.json", {"parent": "logisticspipes:block/pipe_inventory", "textures": texture})
        write(ASSETS + f"blockstates/{name}.json", blockstate(name))
    table = "logistics_crafting_table"
    write(ASSETS + f"models/block/{table}.json", {"parent": "minecraft:block/cube_bottom_top", "textures": {
        "top": f"logisticspipes:block/{table}_top", "side": f"logisticspipes:block/{table}_side",
        "bottom": f"logisticspipes:block/{table}_bottom"}})
    write(ASSETS + f"models/item/{table}.json", {"parent": f"logisticspipes:block/{table}"})
    write(ASSETS + f"blockstates/{table}.json", {"variants": {"": {"model": f"logisticspipes:block/{table}"}}})
    for name in PIPES + [table]:
        write(DATA + f"loot_table/blocks/{name}.json", {"type": "minecraft:block", "pools": [{
            "rolls": 1, "entries": [{"type": "minecraft:item", "name": f"logisticspipes:{name}"}],
            "conditions": [{"condition": "minecraft:survives_explosion"}]}]})
    write("src/main/resources/data/minecraft/tags/block/mineable/pickaxe.json",
          {"values": [f"logisticspipes:{name}" for name in PIPES + [table]]})


if __name__ == "__main__":
    main()
