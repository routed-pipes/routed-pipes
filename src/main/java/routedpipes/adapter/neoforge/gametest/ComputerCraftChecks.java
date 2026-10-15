package routedpipes.adapter.neoforge.gametest;

import routedpipes.adapter.minecraft.gametest.GameTestPower;
import routedpipes.adapter.minecraft.gametest.TestPlayer;
import routedpipes.adapter.minecraft.gametest.PowerSupplierGameTest;
import routedpipes.adapter.minecraft.gametest.ComputerGameTest;
import routedpipes.adapter.minecraft.content.Content;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import dan200.computercraft.api.lua.LuaException;
import dan200.computercraft.api.peripheral.IComputerAccess;
import dan200.computercraft.api.peripheral.IPeripheral;
import dan200.computercraft.api.peripheral.PeripheralCapability;
import routedpipes.adapter.computercraft.PipePeripheral;
import routedpipes.adapter.computercraft.RemotePipe;
import routedpipes.adapter.minecraft.PipeBlockEntity;
import routedpipes.adapter.minecraft.SecurityStationBlockEntity;
import routedpipes.domain.logistics.PipeKind;
import routedpipes.domain.logistics.UpgradeKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.LevelResource;

// run by ComputerCraftGameTest, needs CC: Tweaked
final class ComputerCraftChecks {

    private static final int SETTLE = 5;
    // far from the ids the computers of a test world get by themselves
    private static int nextComputer = 7300;

    private ComputerCraftChecks() {
    }

    private static PipeBlockEntity pipe(GameTestHelper helper, PipeKind kind, int x) {
        return ComputerGameTest.pipe(helper, kind, x);
    }

    // the pipe at x as the computer west of it finds it
    private static IPeripheral peripheral(GameTestHelper helper, int x) {
        return helper.getLevel().getCapability(PeripheralCapability.get(), helper.absolutePos(new BlockPos(x, 1, 1)), Direction.WEST);
    }

    private static void assertEquals(GameTestHelper helper, Object expected, Object actual) {
        helper.assertTrue(expected.equals(actual), "expected " + expected + " but got " + actual);
    }

    private static Object[] call(GameTestHelper helper, PipePeripheral peripheral, String name, Object... arguments) {
        try {
            return peripheral.call(name, 7, arguments);
        } catch (LuaException e) {
            throw new AssertionError(name + " failed: " + e.getMessage(), e);
        }
    }

    static void routedPipesArePeripherals(GameTestHelper helper) {
        PipeBlockEntity basic = pipe(helper, PipeKind.BASIC, 0);
        pipe(helper, PipeKind.REQUEST, 2);
        helper.setBlock(new BlockPos(4, 1, 1), Content.TRANSPORT_PIPE.get());

        helper.assertTrue(peripheral(helper, 4) == null, "a plain pipe is a peripheral");
        IPeripheral found = peripheral(helper, 0);
        helper.assertTrue(found instanceof PipePeripheral, "a routed pipe is no peripheral");
        PipePeripheral peripheral = (PipePeripheral) found;
        assertEquals(helper, "RoutedPipes:Normal", peripheral.getType());
        assertEquals(helper, "RoutedPipes:Request", peripheral(helper, 2).getType());
        helper.assertTrue(Arrays.asList(peripheral.getMethodNames()).containsAll(List.of("getRouterUUID", "sendBroadcast", "help")),
                "methods are missing: " + Arrays.toString(peripheral.getMethodNames()));
        assertEquals(helper, basic.routerId().value().toString(), call(helper, peripheral, "getRouterUUID")[0]);
        // a computer keeps the peripheral it has as long as it is the same pipe
        helper.assertTrue(peripheral.equals(peripheral(helper, 0)), "the same pipe is another peripheral");
        helper.assertFalse(peripheral.equals(peripheral(helper, 2)), "another pipe is the same peripheral");
        helper.succeed();
    }

    // a computer that only has an id and remembers its events
    private static IComputerAccess computer(int id, List<String> heard) {
        return (IComputerAccess) Proxy.newProxyInstance(IComputerAccess.class.getClassLoader(), new Class<?>[] {IComputerAccess.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getID" -> id;
                    case "queueEvent" -> {
                        heard.add(id + " " + arguments[0] + " " + Arrays.asList((Object[]) arguments[1]));
                        yield null;
                    }
                    case "hashCode" -> id;
                    case "equals" -> proxy == arguments[0];
                    default -> throw new UnsupportedOperationException(method.getName());
                });
    }

    static void messagesReachAttachedComputers(GameTestHelper helper) {
        pipe(helper, PipeKind.BASIC, 0);
        pipe(helper, PipeKind.PROVIDER, 1);
        PipePeripheral here = (PipePeripheral) peripheral(helper, 0);
        PipePeripheral there = (PipePeripheral) peripheral(helper, 1);
        List<String> heard = new ArrayList<>();
        IComputerAccess own = computer(7, heard);
        IComputerAccess other = computer(8, heard);
        IComputerAccess gone = computer(9, heard);
        here.attach(own);
        there.attach(other);
        there.attach(gone);
        there.detach(gone);

        helper.runAfterDelay(SETTLE, () -> {
            call(helper, here, "sendBroadcast", "to all");
            call(helper, here, "sendMessage", 8.0, "to one");
            here.detach(own);
            there.detach(other);
            assertEquals(helper, List.of("7 LP_BROADCAST [7, to all]", "8 LP_BROADCAST [7, to all]", "8 LP_MESSAGE [7, to one]"), heard);
            helper.succeed();
        });
    }

    // puts a computer with the given startup program at the place, it runs the program as soon as it ticks
    private static BlockEntity computer(GameTestHelper helper, BlockPos pos, String program) {
        int id = nextComputer++;
        Path startup = helper.getLevel().getServer().getWorldPath(LevelResource.ROOT)
                .resolve("computercraft/computer/" + id + "/startup.lua");
        try {
            Files.createDirectories(startup.getParent());
            Files.writeString(startup, program);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        helper.setBlock(pos, BuiltInRegistries.BLOCK.get(ResourceLocation.parse("computercraft:computer_advanced")));
        BlockEntity computer = helper.getBlockEntity(pos);
        CompoundTag tag = new CompoundTag();
        tag.putInt("ComputerId", id);
        tag.putBoolean("On", true);
        computer.loadWithComponents(tag, helper.getLevel().registryAccess());
        return computer;
    }

    // scripts report by naming their computer, the block saves the label, which holds 32 characters
    private static String label(GameTestHelper helper, BlockEntity computer) {
        return computer.saveWithoutMetadata(helper.getLevel().registryAccess()).getString("Label");
    }

    // the test server ticks as fast as it can, a computer needs real time to start up and run
    private static void assertLabel(GameTestHelper helper, BlockEntity computer, String expected) {
        if (!expected.equals(label(helper, computer))) {
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        assertEquals(helper, expected, label(helper, computer));
    }

    // a real computer next to the pipe, the whole way through CC: Tweaked and its Lua machine
    static void aScriptTalksToThePipe(GameTestHelper helper) {
        PipeBlockEntity basic = pipe(helper, PipeKind.BASIC, 1);
        BlockEntity computer = computer(helper, new BlockPos(0, 1, 1), """
                local pipe = peripheral.find("RoutedPipes:Normal")
                local module = pipe.getLogisticsModule()
                os.setComputerLabel(pipe.getRouterUUID():sub(1, 8) .. " " .. module.type .. " " .. #module.filter)
                """);

        helper.succeedWhen(() -> assertLabel(helper, computer,
                basic.routerId().value().toString().substring(0, 8) + " ItemSink Module 0"));
    }

    // a script orders at a request pipe and gets the answer as plain tables
    static void aScriptRequestsItems(GameTestHelper helper) {
        GameTestPower.supply(helper);
        pipe(helper, PipeKind.REQUEST, 0);
        BlockPos target = new BlockPos(0, 2, 1);
        helper.setBlock(target, Blocks.CHEST);
        pipe(helper, PipeKind.PROVIDER, 1);
        helper.setBlock(new BlockPos(1, 2, 1), Blocks.CHEST);
        Container source = helper.getBlockEntity(new BlockPos(1, 2, 1));
        source.setItem(0, new ItemStack(Items.COBBLESTONE, 64));

        helper.runAfterDelay(SETTLE, () -> {
            BlockEntity computer = computer(helper, new BlockPos(0, 1, 0), """
                    local pipe = peripheral.find("RoutedPipes:Request")
                    local listed = #pipe.getAvailableItems()
                    local status, items = pipe.makeRequest("minecraft:cobblestone", 3)
                    os.setComputerLabel(status .. " " .. items[1].name .. " " .. items[1].count .. " " .. listed)
                    """);
            helper.succeedWhen(() -> {
                Container container = helper.getBlockEntity(target);
                helper.assertTrue(container.countItem(Items.COBBLESTONE) == 3, "the cobblestone has not arrived");
                assertLabel(helper, computer, "DONE minecraft:cobblestone 3 1");
            });
        });
    }

    // the id the station is asked about is the one of the computer that calls
    static void aLockedPipeKeepsComputersOut(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity basic = pipe(helper, PipeKind.BASIC, 0);
        BlockPos pos = new BlockPos(3, 1, 3);
        helper.setBlock(pos, Content.SECURITY_STATION.get());
        SecurityStationBlockEntity station = helper.getBlockEntity(pos);
        station.station().settings().addComputerException(7);
        basic.security().card().setItem(0, Content.SECURITY_CARD.get().cardFor(station.id()));
        PipePeripheral peripheral = (PipePeripheral) peripheral(helper, 0);

        helper.runAfterDelay(SETTLE, () -> {
            try {
                peripheral.call("getRouterUUID", 8);
                helper.fail("a computer that is not listed gets in");
            } catch (LuaException e) {
                assertEquals(helper, "Permission denied", e.getMessage());
            }
            assertEquals(helper, basic.routerId().value().toString(), call(helper, peripheral, "getRouterUUID")[0]);
            helper.succeed();
        });
    }

    static void theRemoteControlUpgradeHandsOutPipeObjects(GameTestHelper helper) {
        PipeBlockEntity basic = pipe(helper, PipeKind.BASIC, 0);
        PipeBlockEntity request = pipe(helper, PipeKind.REQUEST, 1);
        basic.installUpgrade(new ItemStack(Content.UPGRADES.get(UpgradeKind.CC_REMOTE_CONTROL).get()));
        PipePeripheral peripheral = (PipePeripheral) peripheral(helper, 0);

        helper.runAfterDelay(SETTLE, () -> {
            Object found = call(helper, peripheral, "getPipeForUUID", request.routerId().value().toString())[0];
            helper.assertTrue(found instanceof RemotePipe, "no pipe object but " + found);
            RemotePipe remote = (RemotePipe) found;
            List<String> names = Arrays.asList(remote.getMethodNames());
            helper.assertTrue(names.containsAll(List.of("makeRequest", "getRouterUUID")) && !names.contains("getPipeForUUID")
                    && !names.contains("sendMessage"), "the wrong methods from afar: " + names);
            try {
                assertEquals(helper, request.routerId().value().toString(), remote.call("getRouterUUID")[0]);
            } catch (LuaException e) {
                throw new AssertionError(e);
            }
            helper.succeed();
        });
    }

    // the pipe object has to make it into the script and work there
    static void aScriptUsesAPipeFromAfar(GameTestHelper helper) {
        PipeBlockEntity basic = pipe(helper, PipeKind.BASIC, 1);
        PipeBlockEntity request = pipe(helper, PipeKind.REQUEST, 2);
        basic.installUpgrade(new ItemStack(Content.UPGRADES.get(UpgradeKind.CC_REMOTE_CONTROL).get()));

        helper.runAfterDelay(SETTLE, () -> {
            BlockEntity computer = computer(helper, new BlockPos(0, 1, 1), """
                    local pipe = peripheral.find("RoutedPipes:Normal")
                    local other = pipe.getPipeForUUID("%s")
                    os.setComputerLabel(other.getType() .. " " .. tostring(other.sendMessage) .. " " .. tostring(pipe.canAccess()))
                    """.formatted(request.routerId().value()));
            helper.succeedWhen(() -> assertLabel(helper, computer, "RoutedPipes:Request nil true"));
        });
    }
}
