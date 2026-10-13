#!/usr/bin/env python3
"""The mounts that hold a pipe to solid blocks, the way the original mod picks them, written down a second time
apart from the game code in domain/look/Mounts.java.

Run from the project root. Checks that the model of every mount sits where its name says, and that the table
src/test/resources/mounts.txt, which the unit tests hold the game code against, is the one these rules give.
With --write the table is written anew."""
import itertools
import os
import sys

MODELS = "src/main/resources/assets/routedpipes/models/block/pipe/"
TABLE = "src/test/resources/mounts.txt"
# the order the original goes through directions and through mounts
SIDES = ["down", "up", "north", "south", "west", "east"]
OPPOSITE = {"down": "up", "up": "down", "north": "south", "south": "north", "west": "east", "east": "west"}
AXIS = {"west": (0, -1), "east": (0, 1), "down": (1, -1), "up": (1, 1), "north": (2, -1), "south": (2, 1)}
CLOCKWISE = {"north": "east", "east": "south", "south": "west", "west": "north"}
MOUNTS = [(towards, flank) for towards in ("up", "down", "north", "south", "east", "west")
          for flank in ("up", "down", "north", "south", "east", "west") if flank not in (towards, OPPOSITE[towards])]


def remove_from_side(mounts, towards):
    mounts[:] = [mount for mount in mounts if mount[0] != towards]


def find_opponent_on_same_side(mounts, towards):
    mounts[:] = [mount for mount in mounts if mount[0] == towards]
    if len(mounts) <= 2:
        return
    flanks = {mount[1] for mount in mounts}
    keep = ()
    for pair in (("north", "south"), ("east", "west"), ("up", "down")):
        if flanks >= set(pair):
            keep = pair
            break
    mounts[:] = [mount for mount in mounts if mount[1] in keep]


def reduce_to_one_per_side(mounts, towards, preferred=None):
    if preferred and (towards, preferred) in mounts:
        first = (towards, preferred)
    else:
        first = next(mount for mount in mounts if mount[0] == towards)
    mounts[:] = [mount for mount in mounts if mount[0] != towards or mount == first]


def remove_if_has_opponent_side(mounts):
    present = {mount[0] for mount in mounts}
    for wall, across, others in (("north", "south", ("east", "west")), ("east", "west", ("north", "south"))):
        if wall in present and across in present:
            for other in others:
                remove_from_side(mounts, other)
            reduce_to_one_per_side(mounts, wall)
            reduce_to_one_per_side(mounts, across)
            return


def remove_if_has_connected_side(mounts):
    present = {mount[0] for mount in mounts}
    for wall in SIDES[2:]:
        turned = CLOCKWISE[wall]
        if wall in present and turned in present:
            reduce_to_one_per_side(mounts, wall, OPPOSITE[turned])
            reduce_to_one_per_side(mounts, turned, CLOCKWISE[turned])


def choose(connected, solid):
    """Connected: the sides with an arm. Solid: the sides whose block has a full face towards the pipe."""
    mounts = list(MOUNTS)
    for side in SIDES:
        if side in connected:
            mounts = [mount for mount in mounts if side not in mount]
    holding = set()
    for side in SIDES:
        if side not in solid or side in connected:
            remove_from_side(mounts, side)
        else:
            holding.add(side)
    if not mounts:
        return mounts
    if "down" in holding:
        find_opponent_on_same_side(mounts, "down")
    elif "up" in holding:
        find_opponent_on_same_side(mounts, "up")
    else:
        remove_from_side(mounts, "down")
        remove_from_side(mounts, "up")
        if len(mounts) > 2:
            remove_if_has_opponent_side(mounts)
        if len(mounts) > 2:
            remove_if_has_connected_side(mounts)
        if len(mounts) > 2:
            find_opponent_on_same_side(mounts, mounts[0][0])
    return mounts


def subsets(sides):
    return [set(chosen) for count in range(len(sides) + 1) for chosen in itertools.combinations(sides, count)]


def table():
    """One line for every way a pipe can be connected and surrounded."""
    lines = []
    for connected in subsets(SIDES):
        for solid in subsets(SIDES):
            mounts = " ".join(f"{towards}_{flank}" for towards, flank in choose(connected, solid))
            lines.append(f"{','.join(side for side in SIDES if side in connected)};"
                         f"{','.join(side for side in SIDES if side in solid)};{mounts}")
    return "\n".join(lines) + "\n"


def vertices(towards, flank):
    path = MODELS + f"mount_{towards}_{flank}.obj"
    return [tuple(float(value) for value in line.split()[1:4]) for line in open(path) if line.startswith("v ")]


def misplaced(towards, flank):
    """A mount reaches the border of the block on the side of the block it holds on to, and stays on its flank of the pipe."""
    points = vertices(towards, flank)
    axis, sign = AXIS[towards]
    reach = max(point[axis] * sign for point in points)
    # the generator makes mounts a hair larger
    if abs(reach - (1 if sign > 0 else 0)) > 0.003:
        return f"ends at {reach * sign:.3f} and not at the block to the {towards}"
    axis, sign = AXIS[flank]
    if any((point[axis] - 0.5) * sign < 0.1 for point in points):
        return f"is not on the {flank} flank"
    for other in range(3):
        if other not in (AXIS[towards][0], axis) and any(abs(point[other] - 0.5) > 0.25 for point in points):
            return "is not in the middle of its flank"
    return None


def main():
    if "--write" in sys.argv:
        os.makedirs(os.path.dirname(TABLE), exist_ok=True)
        with open(TABLE, "w") as file:
            file.write(table())
    problems = []
    for towards, flank in MOUNTS:
        problem = misplaced(towards, flank)
        if problem:
            problems.append(f"mount_{towards}_{flank} {problem}")
    if open(TABLE).read() != table():
        problems.append(f"{TABLE} is not what the rules give, run with --write")
    sizes = [len(choose(connected, solid)) for connected in subsets(SIDES) for solid in subsets(SIDES)]
    if max(sizes) > 2:
        problems.append("more than two mounts")
    print(f"{len(MOUNTS)} mounts, {len(sizes)} surroundings, {len(problems)} problems")
    for problem in problems:
        print(" ", problem)
    sys.exit(1 if problems else 0)


if __name__ == "__main__":
    main()
