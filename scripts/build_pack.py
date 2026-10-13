#!/usr/bin/env python3
"""Writes the Modrinth pack file from pack/mods.lock.json and the built jar. Run by scripts/build-pack.sh."""
import json, re, zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
properties = dict(re.findall(r"^(\w+)=(.*)$", (ROOT / "gradle.properties").read_text(), flags=re.M))
version, neoforge = properties["mod_version"], properties["neo_version"]
jar = ROOT / f"build/libs/routedpipes-{version}.jar"
lock = json.loads((ROOT / "pack/mods.lock.json").read_text())
mods = lock["modrinth"]
# a pack file may only download from Modrinth, so what comes from CurseForge is put inside it
bundled = [ROOT / f"run-pack-check/mods/{mod['slug']}-{mod['project']}-{mod['file']}.jar" for mod in lock["curseforge"]]
index = {
    "formatVersion": 1,
    "game": "minecraft",
    "versionId": version,
    "name": "Routed Pipes Pack",
    "summary": "A Tekkit of today around Routed Pipes: Mekanism, Project Red, ComputerCraft, Railcraft, AE2, Ender Storage and friends.",
    "files": [{"path": "mods/" + mod["file"], "hashes": {"sha1": mod["sha1"], "sha512": mod["sha512"]},
               "env": {"client": "required", "server": "required"}, "downloads": [mod["url"]], "fileSize": mod["size"]}
              for mod in mods],
    "dependencies": {"minecraft": "1.21.1", "neoforge": neoforge},
}
out = ROOT / f"build/pack/routed-pipes-pack-{version}.mrpack"
out.parent.mkdir(parents=True, exist_ok=True)
with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as pack:
    pack.writestr("modrinth.index.json", json.dumps(index, indent=2))
    for file in [jar] + bundled:
        pack.write(file, "overrides/mods/" + file.name)
print(out.relative_to(ROOT), f"{len(mods)} mods to download, inside: " + ", ".join(file.name for file in [jar] + bundled))
