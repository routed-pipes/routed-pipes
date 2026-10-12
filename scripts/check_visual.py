#!/usr/bin/env python3
"""Checks the screenshots of the visual test for things that can be judged without looking at them."""
import sys
from pathlib import Path

from PIL import Image, ImageChops

SHOTS = Path(sys.argv[1] if len(sys.argv) > 1 else "build/visual-test") / "screenshots"
LOG = Path(sys.argv[2]) if len(sys.argv) > 2 else None
EXPECTED = ["pipes_overview", "pipes_close", "pipes_junction", "pipes_vertical", "tubes_above", "tubes_side", "crafting_table", "chassis",
            "gui_request", "gui_request_search", "gui_supplier", "gui_basic", "gui_provider", "gui_request_table", "gui_firewall", "pipes_on_the_ground", "sign_crafting", "hud_panels", "placement_ghost", "guide_book_contents", "gui_security_station", "gui_statistics_table", "gui_program_compiler", "gui_fluid_request_pipe", "gui_fluid_basic_pipe", "gui_fluid_supplier_pipe", "gui_request_pipe_mk2", "gui_inventory_system_connector_pipe", "gui_system_entrance_pipe", "gui_controller", "gui_crafting", "gui_satellite", "gui_chassis",
            "gui_module_item_sink", "gui_module_names", "gui_crafting_table", "gui_crafting_imported"]
FLICKER_FRAMES = 12
# the grey of the window background drawn by Panel
PANEL = (198, 198, 198)


def load(name):
    return Image.open(SHOTS / f"{name}.png").convert("RGB")


def changed_box(base, frame):
    difference = ImageChops.difference(base, frame).convert("L").point(lambda value: 255 if value > 30 else 0)
    return difference.getbbox()


def main():
    problems = []
    for name in EXPECTED + ["flicker_base"] + [f"flicker_{frame:02d}" for frame in range(FLICKER_FRAMES)]:
        if not (SHOTS / f"{name}.png").exists():
            problems.append(f"missing screenshot {name}")
    if problems:
        return problems

    # the item must be visible in every frame and never jump backwards
    base = load("flicker_base")
    last = None
    for frame in range(FLICKER_FRAMES):
        box = changed_box(base, load(f"flicker_{frame:02d}"))
        if box is None:
            problems.append(f"flicker_{frame:02d}: the travelling item is not visible")
            continue
        centre = (box[0] + box[2]) / 2
        # the first frames show it leaving the chest downwards, after that it moves sideways
        if last is not None and frame > 2 and centre < last - 2:
            problems.append(f"flicker_{frame:02d}: the item jumped back from x={last:.0f} to x={centre:.0f}")
        last = centre

    # a screen that failed to open would leave no window background in the picture
    for name in (shot for shot in EXPECTED if shot.startswith("gui_")):
        colours = dict((colour, count) for count, colour in load(name).getcolors(maxcolors=1 << 24))
        if colours.get(PANEL, 0) < 5000:
            problems.append(f"{name}: no window on the screen")
    problems += log_problems()
    return problems


def log_problems():
    """Errors of this mod in the client log, and with JEI installed the plugin has to have been picked up."""
    if LOG is None or not LOG.exists():
        return []
    problems = []
    text = LOG.read_text(errors="replace")
    for line in text.splitlines():
        if ("/ERROR]" in line or "Caught an error" in line) and "logisticspipes" in line.lower():
            problems.append("log: " + line.strip()[:200])
    if "mezz.jei" in text:
        if "logisticspipes:jei" not in text:
            problems.append("log: JEI is installed but never mentions the logisticspipes plugin")
        if "recipe viewer check passed" not in text:
            failed = [line.strip()[-220:] for line in text.splitlines() if "recipe viewer check failed" in line]
            problems.append("log: " + (failed[0] if failed else "the recipe viewer check never ran"))
    return problems


if __name__ == "__main__":
    found = main()
    for problem in found:
        print("FAIL", problem)
    if not found:
        print("visual checks passed")
    sys.exit(1 if found else 0)
