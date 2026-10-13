#!/usr/bin/env python3
"""Pins the mods of the play pack. Reads pack/mods.txt, asks Modrinth for the newest NeoForge 1.21.1 build of each,
adds the libraries they require, and writes pack/mods.lock.json and pack/mods.gradle.

Run it again to update the pack. Releases are preferred over betas, betas over alphas.
"""
import json, sys, urllib.parse, urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
QUERY = urllib.parse.urlencode({"loaders": '["neoforge"]', "game_versions": '["1.21.1"]'})
RANK = {"release": 0, "beta": 1, "alpha": 2}

def get(path):
    request = urllib.request.Request("https://api.modrinth.com/v2/" + path, headers={"User-Agent": "routed-pipes-pack"})
    return json.load(urllib.request.urlopen(request, timeout=30))

def newest(project):
    versions = get(f"project/{project}/version?{QUERY}")
    if not versions:
        return None
    # the list comes newest first, the sort keeps that order within a kind
    return sorted(versions, key=lambda version: RANK[version["version_type"]])[0]

entries = [line.strip() for line in (ROOT / "pack/mods.txt").read_text().splitlines() if line.strip() and not line.startswith("#")]
wanted = [entry for entry in entries if not entry.startswith("curseforge ")]
# these come through cursemaven.com and are not looked up, the file id in the list is the pin
curseforge = [dict(zip(("slug", "project", "file"), entry.split()[1:])) for entry in entries if entry.startswith("curseforge ")]
locked, missing, queue = {}, [], [(slug, None) for slug in wanted]
while queue:
    project, needed_by = queue.pop(0)
    info = get(f"project/{project}")
    if info["id"] in locked:
        continue
    version = newest(info["id"])
    if version is None:
        missing.append(f"{info['slug']}" + (f", needed by {needed_by}" if needed_by else ""))
        continue
    file = next((f for f in version["files"] if f["primary"]), version["files"][0])
    locked[info["id"]] = {"slug": info["slug"], "project": info["id"], "name": info["title"], "version": version["version_number"],
                          "kind": version["version_type"], "id": version["id"], "file": file["filename"], "url": file["url"],
                          "sha1": file["hashes"]["sha1"], "sha512": file["hashes"]["sha512"], "size": file["size"],
                          "needed_by": needed_by, "client_only": info["server_side"] == "unsupported"}
    for dependency in version["dependencies"]:
        if dependency["dependency_type"] == "required" and dependency["project_id"]:
            queue.append((dependency["project_id"], info["slug"]))

if missing:
    print("no NeoForge 1.21.1 build of: " + "; ".join(missing))
    sys.exit(1)
mods = sorted(locked.values(), key=lambda mod: mod["slug"])
(ROOT / "pack/mods.lock.json").write_text(json.dumps({"modrinth": mods, "curseforge": curseforge}, indent=2) + "\n")
lines = ["// written by scripts/resolve_pack.py from pack/mods.txt, do not edit", "dependencies {"]
# a mod that says it does not run on a server is kept out of the server's folder, some of them crash it
lines += [f"    {'packClientMods' if mod['client_only'] else 'packMods'} 'maven.modrinth:{mod['slug']}:{mod['id']}'" for mod in mods]
lines += [f"    packMods 'curse.maven:{mod['slug']}-{mod['project']}:{mod['file']}'" for mod in curseforge]
(ROOT / "pack/mods.gradle").write_text("\n".join(lines + ["}"]) + "\n")
for mod in mods:
    print(f"{mod['slug']:32s} {mod['version'][:30]:30s} {mod['kind']:8s}" + (" client only" if mod["client_only"] else "")
          + (f" for {mod['needed_by']}" if mod["needed_by"] else ""))
for mod in curseforge:
    print(f"{mod['slug']:32s} file {mod['file']:25s} curseforge")
print(len(mods) + len(curseforge), "mods")
