# Routed Pipes

Routed item transport, requesting and autocrafting for Minecraft 1.21.1 on NeoForge and Fabric.

Pipes that know where things are. Put a provider pipe on a chest and a request pipe anywhere on the same network,
and you can order what is in the chest. Crafting pipes make what is not in stock, suppliers keep machines filled,
chassis pipes take modules for sorting and extracting, and fluid pipes do the same for tanks.

Routed Pipes is a rewrite from scratch, based on the design of
[Logistics Pipes](https://github.com/RS485/LogisticsPipes) by Krapht and the RS485 team. It uses the original
textures under the MMPL. It is not affiliated with or endorsed by the original authors.

## What is in it

- Basic, provider, request, supplier, crafting, satellite and firewall pipes, chassis pipes with modules and upgrades
- A crafting planner with priorities, partial runs, a preview of what an order takes, and cancelling of open orders
- Fluid pipes, high speed tubes, a request table, a security station and a statistics table
- Inventory System Connectors and Teleport Logistics Pipes to join distant networks, and a Void Logistics Pipe for overflow
- HUD glasses, a guide book, pipe signs, item tracing, a settings copier and a remote orderer
- Works with JEI, Jade and CC: Tweaked when they are installed, and is tested against Mekanism, Create, AE2,
  Sophisticated Storage, Storage Drawers, Immersive Engineering, Ender Storage, ProjectE and Iron Chests
- On Fabric it needs Fabric API, energy goes through Team Reborn Energy, which is bundled. The JEI, Jade and
  CC: Tweaked support and the mod compatibility tests are NeoForge only for now

Worlds saved while the mod still had the id `logisticspipes` keep loading.

## Building

Java 21 is needed.

```
./gradlew build
```

The NeoForge jar ends up in `build/libs`, the Fabric jar in `fabric/build/libs`. `./gradlew check` runs the unit tests,
the architecture tests and the in-game tests on both loaders.
`./gradlew runGameTestServerCompat` runs the in-game tests with a set of other mods installed.
`./gradlew :fabric:runFabricClient` starts a Fabric client.

## Layout

The code is split into layers and tests keep them apart: `domain` is plain Java, `application` holds the network
and its ports, `adapter/minecraft` uses vanilla Minecraft only, and `adapter/neoforge` and `adapter/fabric` (in
`fabric/`) are the only places that know a mod loader. The `vanilla-check` project compiles `adapter/minecraft`
against plain Minecraft, so nothing of either loader slips in there.

`pack/` and the pack scripts describe a mod pack around the mod, see the [pack repository](https://github.com/routed-pipes/pack).

## Licence

[Minecraft Mod Public License 1.0.1](LICENSE.md), the licence of the original.
