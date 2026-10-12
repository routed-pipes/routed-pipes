#!/usr/bin/env python3
"""Drives a plain NeoForge server through its console: builds small pipe scenes with commands and checks the outcome.

Run by scripts/production-check.sh from inside the server directory. Prints one line per check and fails if any is missing.
"""
import subprocess, os, time, threading
env = dict(os.environ)
p = subprocess.Popen(["sh", "run.sh", "--nogui"], stdin=subprocess.PIPE, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True, env=env)
lines = []; ready = threading.Event()
def read():
    for line in p.stdout:
        lines.append(line)
        if "Done (" in line:
            ready.set()
threading.Thread(target=read, daemon=True).start()
def cmd(c):
    p.stdin.write(c + "\n"); p.stdin.flush()
if not ready.wait(300):
    p.kill(); print("server never finished starting"); raise SystemExit(1)
setup = [
    "forceload add 0 0 31 31",
    # sorting: a hopper feeds a basic pipe, the diamonds have to reach the chest at the basic pipe set as default route
    "setblock 8 -50 8 logisticspipes:basic_logistics_pipe",
    "setblock 8 -50 7 logisticspipes:power_junction{Energy:1000}",
    "setblock 9 -50 8 logisticspipes:transport_pipe",
    "setblock 10 -50 8 logisticspipes:basic_logistics_pipe{DefaultRoute:1b}",
    "setblock 10 -49 8 chest",
    'setblock 8 -49 8 hopper{Items:[{Slot:0b,id:"minecraft:diamond",count:7}]}',
    # extracting with power: chassis with an extractor module and a charged junction, cobblestone moves to the next chest
    'setblock 8 -50 12 logisticspipes:chassis_pipe_mk1{Modules:{Items:[{Slot:0b,id:"logisticspipes:module_extractor",count:1}]}}',
    'setblock 8 -49 12 chest{Items:[{Slot:0b,id:"minecraft:cobblestone",count:32}]}',
    "setblock 8 -50 13 logisticspipes:power_junction{Energy:100000}",
    "setblock 9 -50 12 logisticspipes:basic_logistics_pipe{DefaultRoute:1b}",
    "setblock 9 -49 12 chest",
    # the same without power must not move anything
    'setblock 8 -50 16 logisticspipes:chassis_pipe_mk1{Modules:{Items:[{Slot:0b,id:"logisticspipes:module_extractor",count:1}]}}',
    'setblock 8 -49 16 chest{Items:[{Slot:0b,id:"minecraft:cobblestone",count:32}]}',
    "setblock 9 -50 16 logisticspipes:basic_logistics_pipe{DefaultRoute:1b}",
    "setblock 9 -49 16 chest",
    # a crafting table of the mod keeps its recipe grid
    'setblock 8 -50 20 logisticspipes:logistics_crafting_table{Pattern:{Items:[{Slot:0b,id:"minecraft:oak_log",count:1}]},Storage:{Items:[{Slot:0b,id:"minecraft:oak_log",count:4}]}}',
]
for c in setup:
    cmd(c)
time.sleep(25)
checks = [
    "execute if items block 10 -49 8 container.* minecraft:diamond[count=7] run say LPCHECK sorting ok",
    "execute if items block 9 -49 12 container.* minecraft:cobblestone[count=32] run say LPCHECK powered extraction ok",
    "execute if items block 8 -49 16 container.* minecraft:cobblestone[count=32] run say LPCHECK unpowered extraction stays ok",
    "execute unless items block 9 -49 16 container.* minecraft:cobblestone run say LPCHECK unpowered target empty ok",
    # two rounds of 16 items at 10 FE each, plus 2 FE for each sorted stack
    "execute if data block 8 -50 13 {Energy:99676} run say LPCHECK power used ok",
    # the hopper hands over the seven diamonds one at a time, each is a stack of its own at 2 FE
    "execute if data block 8 -50 7 {Energy:986} run say LPCHECK sorting paid ok",
    "execute if data block 8 -50 20 Pattern.Items[{id:\"minecraft:oak_log\"}] run say LPCHECK crafting table data ok",
    "data get block 8 -50 13 Energy",
]
for c in checks:
    cmd(c)
time.sleep(3)
cmd("stop")
p.wait(timeout=120)
open("server-run.log", "w").writelines(lines)
expected = ["sorting ok", "powered extraction ok", "unpowered extraction stays ok", "unpowered target empty ok",
            "power used ok", "sorting paid ok", "crafting table data ok"]
passed = [name for name in expected if any("LPCHECK " + name in line for line in lines)]
errors = [line.strip()[:200] for line in lines if ("ERROR" in line or "Exception" in line) and "logisticspipes" in line.lower()]
for name in expected:
    print("ok  " if name in passed else "FAIL", name)
for error in errors:
    print("FAIL log:", error)
raise SystemExit(0 if len(passed) == len(expected) and not errors and p.returncode == 0 else 1)
