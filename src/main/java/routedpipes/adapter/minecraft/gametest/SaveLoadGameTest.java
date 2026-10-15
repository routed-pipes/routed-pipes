package routedpipes.adapter.minecraft.gametest;

import routedpipes.adapter.minecraft.content.Content;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import routedpipes.adapter.minecraft.CraftingTableBlockEntity;
import routedpipes.adapter.minecraft.McFluidType;
import routedpipes.adapter.minecraft.McItemType;
import routedpipes.adapter.minecraft.PipeBlockEntity;
import routedpipes.adapter.minecraft.PowerJunctionBlockEntity;
import routedpipes.adapter.minecraft.PowerProviderBlockEntity;
import routedpipes.adapter.minecraft.ProgramCompilerBlockEntity;
import routedpipes.adapter.minecraft.SecurityStationBlockEntity;
import routedpipes.adapter.minecraft.StatisticsTableBlockEntity;
import routedpipes.adapter.minecraft.TubeBlock;
import routedpipes.adapter.minecraft.TubeBlockEntity;
import routedpipes.domain.crafting.OutputOrder;
import routedpipes.domain.fluid.FluidBatch;
import routedpipes.domain.fluid.FluidSupply;
import routedpipes.domain.item.FuzzyFlags;
import routedpipes.domain.item.ItemBatch;
import routedpipes.domain.logistics.ModuleKind;
import routedpipes.domain.logistics.PipeKind;
import routedpipes.domain.logistics.UpgradeKind;
import routedpipes.domain.program.ProgramCategory;
import routedpipes.domain.routing.RouterId;
import routedpipes.domain.routing.Side;
import routedpipes.domain.sign.SignKind;
import routedpipes.domain.statistics.PipeStatistics;
import routedpipes.domain.tube.TubeKind;
import routedpipes.domain.tube.TubeShape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluids;

// every block entity of the mod with every piece of state set, saved, loaded the way a chunk is loaded and saved again
// both saves have to be the same
public class SaveLoadGameTest {

    private static final String LONG = "empty_long";

    private static int next;

    // a spot that touches no other spot, so nothing connects to anything
    private static BlockPos spot() {
        int index = next++;
        return new BlockPos(index % 8 * 2, 1, index / 8 * 2);
    }

    private static ItemStack upgrade(UpgradeKind kind, int count) {
        return new ItemStack(Content.UPGRADES.get(kind).get(), count);
    }

    // everything a routed pipe of any kind keeps
    private static PipeBlockEntity fullPipe(GameTestHelper helper, Block block) {
        BlockPos pos = spot();
        helper.setBlock(pos, block);
        PipeBlockEntity pipe = helper.getBlockEntity(pos);
        PipeKind kind = pipe.kind();
        if (kind == null) {
            pipe.accept(new ItemStack(Items.APPLE, 3), Direction.NORTH);
            return pipe;
        }
        if (kind == PipeKind.CHASSIS) {
            // a chassis points at an inventory
            helper.setBlock(pos.above(), Blocks.CHEST);
            pipe.modules().setItem(0, new ItemStack(Content.MODULES.get(ModuleKind.ITEM_SINK).get()));
            pipe.moduleUpgrades().setItem(1, upgrade(UpgradeKind.SPEED, 2));
            pipe.pointAtNext();
        }
        // the disconnection upgrade is not in the first slot
        pipe.upgrades().setItem(0, upgrade(UpgradeKind.SPEED, 3));
        pipe.upgrades().setItem(1, upgrade(UpgradeKind.DISCONNECTION, 1));
        pipe.toggleDisconnected(ItemStack.EMPTY, Direction.WEST);
        pipe.sneakyUpgrades().setItem(2, upgrade(UpgradeKind.SNEAKY, 1));
        pipe.config().setItem(0, new ItemStack(Items.DIAMOND, 5));
        pipe.config().setItem(PipeBlockEntity.OUTPUT_SLOT, new ItemStack(Items.STICK, 4));
        pipe.setDefaultRoute(true);
        pipe.setMode(2);
        pipe.setPriority(7);
        pipe.setExclude(kind != PipeKind.CRAFTING);
        pipe.setSatelliteName("north");
        pipe.setSlotSatellite(2, "south");
        pipe.slotSettings().setPatternSlot(1, 5);
        pipe.slotSettings().setLimited(true);
        pipe.slotSettings().setFuzzyFlags(0, FuzzyFlags.of(3));
        pipe.outputOrders().add(new OutputOrder(new RouterId(UUID.randomUUID()), new ItemBatch(McItemType.batchOf(new ItemStack(Items.STICK)).type(), 6), 40));
        pipe.powerPipe().receive(500);
        pipe.statistics().count(PipeStatistics.Kind.SENT, 9);
        pipe.security().card().setItem(0, Content.SECURITY_CARD.get().cardFor(UUID.randomUUID()));
        if (SignKind.ITEM_AMOUNT.allowedOn(kind)) {
            pipe.signs().place(Direction.UP, SignKind.ITEM_AMOUNT);
        }
        if (pipe.firewallSettings() != null) {
            pipe.firewallSettings().list().setItem(0, new ItemStack(Items.COBBLESTONE));
            for (int index = 0; index < pipe.firewallSettings().data().getCount(); index++) {
                pipe.firewallSettings().data().set(index, 1 - pipe.firewallSettings().data().get(index));
            }
        }
        if (pipe.requestTable() != null) {
            pipe.requestTable().grid().setItem(0, new ItemStack(Items.OAK_PLANKS));
            pipe.requestTable().buffer().setItem(3, new ItemStack(Items.OAK_PLANKS, 12));
            pipe.requestTable().sort().setItem(0, new ItemStack(Items.FLINT, 2));
        }
        if (pipe.diskDrive() != null) {
            pipe.diskDrive().slot().setItem(0, new ItemStack(Content.DISK.get()));
        }
        if (pipe.connector() != null) {
            pipe.connector().setChannel(UUID.randomUUID());
            pipe.connector().setResistance(3);
        }
        if (pipe.frequencySlot() != null) {
            pipe.frequencySlot().card().setItem(0, new ItemStack(Content.FREQUENCY_CARD.get()));
        }
        if (pipe.fluidPipe() != null) {
            pipe.fluidPipe().fill(Direction.NORTH, new FluidBatch(new McFluidType(Fluids.LAVA), 300), false);
            if (kind == PipeKind.FLUID_SUPPLIER) {
                pipe.fluidPipe().stock(new McFluidType(Fluids.WATER), 4000, true, FluidSupply.Minimum.ONE_BUCKET);
            }
        }
        pipe.accept(new ItemStack(Items.APPLE, 3), Direction.NORTH);
        return pipe;
    }

    private static List<BlockEntity> everything(GameTestHelper helper) {
        List<BlockEntity> all = new ArrayList<>();
        all.add(fullPipe(helper, Content.TRANSPORT_PIPE.get()));
        for (PipeKind kind : PipeKind.values()) {
            all.add(fullPipe(helper, kind == PipeKind.CHASSIS ? Content.CHASSIS_PIPES.get(PipeBlockEntity.MODULE_SLOTS).get()
                    : Content.pipe(kind)));
        }

        BlockPos pos = spot();
        helper.setBlock(pos, Content.CRAFTING_TABLE.get());
        CraftingTableBlockEntity table = helper.getBlockEntity(pos);
        table.pattern().setItem(4, new ItemStack(Items.OAK_LOG));
        table.storage().setItem(2, new ItemStack(Items.OAK_LOG, 20));
        table.surplus().setItem(0, new ItemStack(Items.OAK_PLANKS, 3));
        all.add(table);

        pos = spot();
        helper.setBlock(pos, Content.POWER_JUNCTION.get());
        PowerJunctionBlockEntity junction = helper.getBlockEntity(pos);
        junction.receive(1234, false);
        all.add(junction);

        pos = spot();
        helper.setBlock(pos, Content.POWER_PROVIDER_RF.get());
        PowerProviderBlockEntity provider = helper.getBlockEntity(pos);
        CompoundTag energy = new CompoundTag();
        energy.putInt("Energy", 777);
        provider.loadWithComponents(energy, helper.getLevel().registryAccess());
        provider.receive(500, false);
        all.add(provider);

        pos = spot();
        helper.setBlock(pos, Content.PROGRAM_COMPILER.get());
        ProgramCompilerBlockEntity compiler = helper.getBlockEntity(pos);
        compiler.slots().setItem(ProgramCompilerBlockEntity.DISK_SLOT, new ItemStack(Content.DISK.get()));
        compiler.slots().setItem(ProgramCompilerBlockEntity.PROGRAMMER_SLOT, new ItemStack(Content.LOGISTICS_PROGRAMMER.get()));
        CompoundTag task = compiler.saveWithoutMetadata(helper.getLevel().registryAccess());
        task.putString("Task", "UNLOCK");
        task.putString("Target", ProgramCategory.values()[1].name());
        task.putInt("Work", 5);
        compiler.loadWithComponents(task, helper.getLevel().registryAccess());
        all.add(compiler);

        pos = spot();
        helper.setBlock(pos, Content.SECURITY_STATION.get());
        SecurityStationBlockEntity station = helper.getBlockEntity(pos);
        station.cards().setItem(0, Content.SECURITY_CARD.get().cardFor(station.id()).copyWithCount(4));
        all.add(station);

        pos = spot();
        helper.setBlock(pos, Content.STATISTICS_TABLE.get());
        StatisticsTableBlockEntity statistics = helper.getBlockEntity(pos);
        CompoundTag tracked = new CompoundTag();
        ListTag list = new ListTag();
        CompoundTag entry = new CompoundTag();
        entry.put("Stack", new ItemStack(Items.IRON_INGOT).save(helper.getLevel().registryAccess()));
        entry.putLongArray("Samples", new long[] {3, 5, 8});
        list.add(entry);
        tracked.put("Tracked", list);
        statistics.loadWithComponents(tracked, helper.getLevel().registryAccess());
        all.add(statistics);
        return all;
    }

    // paths of the keys that differ, like Upgrades.Items or Disconnected
    private static void differences(String path, Tag before, Tag after, Set<String> found) {
        if (before instanceof CompoundTag first && after instanceof CompoundTag second) {
            Set<String> keys = new TreeSet<>(first.getAllKeys());
            keys.addAll(second.getAllKeys());
            for (String key : keys) {
                differences(path.isEmpty() ? key : path + "." + key, first.get(key), second.get(key), found);
            }
        } else if (before == null ? after != null : !before.equals(after)) {
            found.add(path + " (" + before + " -> " + after + ")");
        }
    }

    private static Set<String> roundTrip(GameTestHelper helper, BlockEntity original) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        CompoundTag first = original.saveWithoutMetadata(registries);
        // how a chunk loads it: a new block entity, no level yet
        BlockEntity loaded = BlockEntity.loadStatic(original.getBlockPos(), original.getBlockState(),
                original.saveWithFullMetadata(registries), registries);
        Set<String> found = new TreeSet<>();
        if (loaded == null) {
            found.add("did not load at all");
            return found;
        }
        differences("", first, loaded.saveWithoutMetadata(registries), found);
        return found;
    }

    private static String name(BlockEntity entity) {
        return entity instanceof PipeBlockEntity pipe ? String.valueOf(pipe.kind())
                : entity.getClass().getSimpleName();
    }

    // FINDING: only Disconnected is lost, see the test below, everything else comes back the same
    @GameTest(template = LONG, timeoutTicks = 200)
    public static void everyBlockEntityKeepsAllItsStateThroughSaveAndLoad(GameTestHelper helper) {
        next = 0;
        // a tube needs an item in it, which takes a few ticks
        BlockPos start = new BlockPos(0, 3, 1);
        helper.setBlock(start, Content.TRANSPORT_PIPE.get());
        TubeBlock tube = Content.TUBES.get(TubeKind.SPEEDUP).get();
        helper.assertTrue(tube.build(helper.getLevel(), helper.absolutePos(start.east()), new TubeShape(TubeKind.SPEEDUP, Side.EAST),
                pos -> true), "the tube fits");
        helper.runAfterDelay(2, () -> ((PipeBlockEntity) helper.getBlockEntity(start)).accept(new ItemStack(Items.DIAMOND, 3), Direction.WEST));
        List<String> problems = new ArrayList<>();
        boolean[] checked = {false};
        helper.succeedWhen(() -> {
            if (!checked[0]) {
                TubeBlockEntity inTube = helper.getBlockEntity(start.east());
                helper.assertTrue(!inTube.travellingItems().isEmpty(), "the item is in the tube");
                checked[0] = true;
                List<BlockEntity> all = new ArrayList<>(everything(helper));
                all.add(inTube);
                all.add(helper.getBlockEntity(start.east().east()));
                for (BlockEntity entity : all) {
                    Set<String> found = roundTrip(helper, entity);
                    if (!found.isEmpty()) {
                        problems.add(name(entity) + ": " + String.join(", ", found));
                    }
                }
            }
            helper.assertTrue(problems.isEmpty(), "state lost on save and load: " + String.join(" | ", problems));
        });
    }

    // FINDING: a side cut off with a disconnection upgrade connects again after a reload unless the upgrade sits in the first slot
    @GameTest(template = LONG)
    public static void aSideCutOffWithTheUpgradeInTheSecondSlotStaysCutOffAfterLoading(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, Content.pipe(PipeKind.BASIC));
        PipeBlockEntity pipe = helper.getBlockEntity(pos);
        pipe.installUpgrade(upgrade(UpgradeKind.SPEED, 1));
        // the first click puts the upgrade in, the next free slot is the second one
        pipe.toggleDisconnected(upgrade(UpgradeKind.DISCONNECTION, 1), Direction.WEST);
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        helper.assertTrue(pipe.saveWithoutMetadata(registries).getInt("Disconnected") != 0, "the west side is cut off");

        BlockEntity loaded = BlockEntity.loadStatic(pipe.getBlockPos(), pipe.getBlockState(), pipe.saveWithFullMetadata(registries), registries);
        helper.assertTrue(loaded.saveWithoutMetadata(registries).getInt("Disconnected") != 0,
                "the west side is still cut off after loading, with the upgrade still there: "
                        + loaded.saveWithoutMetadata(registries).getCompound("Upgrades"));
        helper.succeed();
    }

    // FINDING: loading a saved pipe into one that is already there, like /data merge does, owes every crafted item twice
    @GameTest(template = LONG)
    public static void loadingIntoAPipeThatIsThereDoesNotDoubleWhatItOwes(GameTestHelper helper) {
        BlockPos pos = new BlockPos(1, 1, 1);
        helper.setBlock(pos, Content.pipe(PipeKind.CRAFTING));
        PipeBlockEntity pipe = helper.getBlockEntity(pos);
        pipe.outputOrders().add(new OutputOrder(new RouterId(UUID.randomUUID()),
                new ItemBatch(McItemType.batchOf(new ItemStack(Items.STICK)).type(), 6), 40));
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        // what /data merge block does: the saved tag with the change merged in, loaded into the same block entity
        CompoundTag merged = pipe.saveWithoutMetadata(registries);
        merged.putInt("Priority", 3);
        pipe.loadWithComponents(merged, registries);
        helper.assertTrue(pipe.outputOrders().owedInTotal() == 6, "still owes 6 sticks, owes " + pipe.outputOrders().owedInTotal());
        helper.succeed();
    }
}
