#!/usr/bin/env python3
"""Checks that every block state of a pipe draws what the rules of the original mod call for: the pieces of the frame,
lit or dark, an arm with the right lights on every connected side and a symbol on every other one.
The rules are written down here a second time on purpose, apart from the ones in generate_pipe_assets.py.
Usage: check_pipe_states.py [blockstate name ...], all pipes without names. Run from the project root."""
import itertools
import json
import os
import sys

from PIL import Image

ASSETS = "src/main/resources/assets/routedpipes/"
SIDES = ["down", "up", "north", "south", "west", "east"]
OPPOSITE = {"down": "up", "up": "down", "north": "south", "south": "north", "west": "east", "east": "west"}
ARMS = ["none", "unrouted", "routed"]
PLAIN, DARK, STATUS = "pipe_frame", "pipe_frame_inactive", "pipe_frame_routed"
# four supports for a straight run, named by the original per axis
SUPPORTS = {"down": ["e_s", "w_s", "n_s", "s_s"], "north": ["e_u", "w_u", "u_s", "d_s"], "west": ["u_u", "d_u", "n_u", "s_u"]}
COLOURS = {"green": lambda r, g, b: g > 200 and r < 160 and b < 80, "red": lambda r, g, b: r > 150 and g < 60 and b < 60,
           "orange": lambda r, g, b: r > 190 and 90 < g < 160 and b < 80, "blue": lambda r, g, b: b > 170 and r < 80 and g < 80}


def terms(when):
    return [term for inner in when["OR"] for term in terms(inner)] if "OR" in when else [when]


def holds(when, state):
    # a property the check does not know only swaps a texture, its first value stands for all
    return any(all(state.setdefault(key, value.split("|")[0]) in value.split("|") for key, value in term.items())
               for term in terms(when))


def lights(obj, texture):
    """The colours of the lights on the part of the texture the faces of a model are cut from."""
    coords, pixels = [], set()
    image = Image.open(ASSETS + f"textures/block/{texture}.png").convert("RGBA")
    width, height = image.size
    for line in open(obj):
        parts = line.split()
        if parts and parts[0] == "vt":
            # flip_v: the file counts v from the bottom
            coords.append((float(parts[1]) * width, (1 - float(parts[2])) * height))
        elif parts and parts[0] == "f":
            polygon = [coords[int(corner.split("/")[1]) - 1] for corner in parts[1:]]
            xs, ys = [x for x, _ in polygon], [y for _, y in polygon]
            for x in range(int(min(xs)), int(max(xs)) + 1):
                for y in range(int(min(ys)), int(max(ys)) + 1):
                    if inside(polygon, x + 0.5, y + 0.5):
                        pixels.add((x % width, y % height))
    found = set()
    for pixel in pixels:
        r, g, b, a = image.getpixel(pixel)
        found.update(name for name, test in COLOURS.items() if a and test(r, g, b))
    return "+".join(sorted(found)) or "none"


def inside(polygon, x, y):
    hit = False
    for index, (x1, y1) in enumerate(polygon):
        x2, y2 = polygon[index - 1]
        if (y1 > y) != (y2 > y) and x < x1 + (y - y1) * (x2 - x1) / (y2 - y1):
            hit = not hit
    return hit


LIGHTS = {}


def drawn(model):
    """What a model of the blockstate is: its geometry by file name, its texture, and for arms the lights on it."""
    data = json.load(open(ASSETS + "models/" + model.split(":")[1] + ".json"))
    obj = ASSETS + data["model"].split(":")[1]
    part = os.path.basename(obj)[:-len(".obj")]
    texture = (data["textures"].get("frame") or data["textures"]["icon"]).split("/")[-1]
    if not part.startswith("side_"):
        return part, texture
    if (obj, texture) not in LIGHTS:
        LIGHTS[obj, texture] = lights(obj, texture)
    # the name of an arm says nothing, its place and its lights do
    return "side_" + part.split("_")[1], texture, LIGHTS[obj, texture]


def expected(name, state):
    """The rules of the original renderer, section 14 of the spec."""
    routed = name != "transport_pipe"
    connected = {side: state[side] != "none" for side in SIDES}
    count = sum(connected.values())
    parts = set()
    for first, second in itertools.combinations(SIDES, 2):
        # an edge runs between two sides and goes when either of them has an arm
        if second != OPPOSITE[first] and not connected[first] and not connected[second]:
            parts.add((edge(first, second), PLAIN))
    # the frame is dark on a routed pipe without power and on a plain pipe used as a junction
    dark = state["powered"] == "false" if routed else count > 2
    for corner in itertools.product(("up", "down"), ("north", "south"), ("west", "east")):
        tag = "_".join(corner)
        arms = [side for side in corner if connected[side]]
        if len(arms) == 0:
            piece = f"corner_{tag}"
        elif len(arms) == 1:
            piece = f"spacer_{tag}_{arms[0]}"
        elif len(arms) == 2:
            piece = f"corner_inner_{tag}_" + next(side for side in corner if not connected[side])
        else:
            piece = f"corner_all_{tag}"
        parts.add((piece, DARK if dark else PLAIN))
    for first, names in SUPPORTS.items():
        if count == 2 and connected[first] and connected[OPPOSITE[first]]:
            parts.update((f"support_{first}_{support}", PLAIN) for support in names)
    for side in SIDES:
        if not connected[side]:
            # the system connector shows its link state on the plate
            parts.add((f"plate_{side}", f"{name}_{state['link']}_icon" if "link" in state else f"{name}_icon"))
        elif not routed:
            parts.add((f"side_{side}", PLAIN, "none"))
        else:
            parts.add((f"side_{side}", STATUS, "green" if state[side] == "routed" else "red"))
    return parts


def edge(first, second):
    """Edges are named top or bottom first, the upright ones north or south first."""
    return f"edge_{first}_{second}" if first in ("up", "down") or first in ("north", "south") else f"edge_{second}_{first}"


def check(name):
    entries = [(entry.get("when", {}), drawn(entry["apply"]["model"]))
               for entry in json.load(open(ASSETS + f"blockstates/{name}.json"))["multipart"]]
    properties = {**{side: ARMS for side in SIDES}, "powered": ["true", "false"]}
    wrong = 0
    for values in itertools.product(*properties.values()):
        state = dict(zip(properties, values))
        found = [part for when, part in entries if holds(when, state)]
        want = expected(name, state)
        if set(found) != want or len(found) != len(want):
            wrong += 1
            if wrong <= 3:
                print(f"  {state}\n    missing {sorted(want - set(found))}\n    extra {sorted(set(found) - want)}"
                      f"\n    twice {sorted(part for part in set(found) if found.count(part) > 1)}")
    states = 1
    for values in properties.values():
        states *= len(values)
    print(f"{name}: {states} states, {wrong} wrong")
    return wrong


def main():
    names = sys.argv[1:] or sorted(file[:-len(".json")] for file in os.listdir(ASSETS + "blockstates") if "_pipe" in file)
    sys.exit(1 if sum(check(name) for name in names) else 0)


if __name__ == "__main__":
    main()
