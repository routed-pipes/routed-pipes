#!/usr/bin/env python3
"""Writes the play pack in packwiz form into another directory, the repository launchers and the server sync from.

Usage: scripts/export_packwiz.py <directory>. Needs the built jar and the mods of run-pack-check, see scripts/export-packwiz.sh.
Mods from Modrinth become a small file that says where to download them. This mod's jar, the few mods that are only
on CurseForge and the settings in pack/overrides are copied in.
"""
import hashlib, json, re, shutil, sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
target = Path(sys.argv[1]).resolve()
properties = dict(re.findall(r"^(\w+)=(.*)$", (ROOT / "gradle.properties").read_text(), flags=re.M))
lock = json.loads((ROOT / "pack/mods.lock.json").read_text())

# a string the way toml wants it, json would turn an emoji in a mod name into escapes toml does not know
def text(value):
    return json.dumps(value, ensure_ascii=False)

def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

# everything in these belongs to the pack, the rest of the directory is left alone
for owned in ("mods", "config"):
    shutil.rmtree(target / owned, ignore_errors=True)
(target / "mods").mkdir(parents=True)
files = []
for mod in lock["modrinth"]:
    meta = target / "mods" / (mod["slug"] + ".pw.toml")
    meta.write_text(f'''name = {text(mod["name"])}
filename = {text(mod["file"])}
side = "{"client" if mod["client_only"] else "both"}"

[download]
url = {text(mod["url"])}
hash-format = "sha512"
hash = "{mod["sha512"]}"

[update]
[update.modrinth]
mod-id = "{mod["project"]}"
version = "{mod["id"]}"
''', encoding="utf-8")
    files.append((meta, True))
jars = [ROOT / f"build/libs/routedpipes-{properties['mod_version']}.jar"]
jars += [ROOT / f"run-pack-check/mods/{mod['slug']}-{mod['project']}-{mod['file']}.jar" for mod in lock["curseforge"]]
for jar in jars:
    shutil.copy(jar, target / "mods" / jar.name)
    files.append((target / "mods" / jar.name, False))
for file in sorted((ROOT / "pack/overrides").rglob("*")):
    if file.is_file():
        copy = target / file.relative_to(ROOT / "pack/overrides")
        copy.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy(file, copy)
        files.append((copy, False))

index = 'hash-format = "sha256"\n'
for file, meta in sorted(files, key=lambda entry: str(entry[0])):
    index += f'\n[[files]]\nfile = "{file.relative_to(target).as_posix()}"\nhash = "{sha256(file)}"\n' + ("metafile = true\n" if meta else "")
(target / "index.toml").write_text(index)
(target / "pack.toml").write_text(f'''name = "Routed Pipes Pack"
author = "routed-pipes"
version = "{properties["mod_version"]}"
pack-format = "packwiz:1.1.0"

[index]
file = "index.toml"
hash-format = "sha256"
hash = "{sha256(target / "index.toml")}"

[versions]
minecraft = "1.21.1"
neoforge = "{properties["neo_version"]}"
''')
print(f"{target}: {len(lock['modrinth'])} mods to download, {len(jars)} jars and {len(files) - len(lock['modrinth']) - len(jars)} settings files inside")
