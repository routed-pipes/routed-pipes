package routedpipes.adapter.minecraft.gametest;

import routedpipes.adapter.minecraft.content.Content;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import routedpipes.adapter.minecraft.CompilerDiskContents;
import routedpipes.adapter.minecraft.PowerJunctionBlockEntity;
import routedpipes.adapter.minecraft.ProgramCompilerBlockEntity;
import routedpipes.adapter.minecraft.ProgrammerItem;
import routedpipes.adapter.minecraft.menu.ProgramCompilerMenu;
import routedpipes.domain.logistics.PipeKind;
import routedpipes.domain.program.CompilerKnowledge;
import routedpipes.domain.program.CompilerTask;
import routedpipes.domain.program.Program;
import routedpipes.domain.program.ProgramCatalog;
import routedpipes.domain.program.ProgramCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public class ProgramCompilerGameTest {

    private static final String EMPTY = "empty";
    // long enough for a pipe to find its power junction
    private static final int SETTLE = 20;
    private static final BlockPos COMPILER = new BlockPos(0, 1, 1);
    private static final BlockPos PIPE = new BlockPos(1, 1, 1);
    private static final Program REQUEST = ProgramCatalog.byId("request_pipe").orElseThrow();
    private static final Program CRAFTER = ProgramCatalog.byId("module_crafter").orElseThrow();
    private static final CompilerKnowledge BASIC = CompilerKnowledge.EMPTY.with(ProgramCategory.BASIC);

    private static ProgramCompilerBlockEntity compiler(GameTestHelper helper, CompilerKnowledge disk, boolean programmer) {
        return compiler(helper, COMPILER, disk, programmer);
    }

    private static ProgramCompilerBlockEntity compiler(GameTestHelper helper, BlockPos pos, CompilerKnowledge disk,
            boolean programmer) {
        helper.setBlock(pos, Content.PROGRAM_COMPILER.get());
        ProgramCompilerBlockEntity compiler = helper.getBlockEntity(pos);
        if (disk != null) {
            compiler.slots().setItem(ProgramCompilerBlockEntity.DISK_SLOT, disk(disk));
        }
        if (programmer) {
            compiler.slots().setItem(ProgramCompilerBlockEntity.PROGRAMMER_SLOT, new ItemStack(Content.LOGISTICS_PROGRAMMER.get()));
        }
        return compiler;
    }

    private static ItemStack disk(CompilerKnowledge knowledge) {
        ItemStack disk = new ItemStack(Content.DISK.get());
        CompilerDiskContents.write(disk, knowledge);
        return disk;
    }

    // a pipe with a junction on top that holds that much
    private static PowerJunctionBlockEntity poweredPipe(GameTestHelper helper, PipeKind kind, BlockPos pos, int energy) {
        helper.setBlock(pos, Content.pipe(kind));
        return GameTestPower.junction(helper, pos.above(), energy);
    }

    private static CompilerKnowledge knowledge(ProgramCompilerBlockEntity compiler) {
        return CompilerDiskContents.read(compiler.slots().getItem(ProgramCompilerBlockEntity.DISK_SLOT)).orElseThrow();
    }

    private static ProgramCompilerMenu open(GameTestHelper helper, Player player) {
        helper.getBlockState(COMPILER).useWithoutItem(helper.getLevel(), player,
                new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(COMPILER)), Direction.UP, helper.absolutePos(COMPILER), false));
        return (ProgramCompilerMenu) player.containerMenu;
    }

    // five seconds and 1000 energy with one pipe, then the programmer carries the program
    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void flashingTakesFiveSecondsAndAThousandEnergy(GameTestHelper helper) {
        ProgramCompilerBlockEntity compiler = compiler(helper, BASIC.with(REQUEST), true);
        PowerJunctionBlockEntity junction = poweredPipe(helper, PipeKind.BASIC, PIPE, 1000);
        ProgramCompilerMenu menu = open(helper, TestPlayer.in(helper));

        helper.runAfterDelay(SETTLE, () -> {
            helper.assertTrue(menu.clickMenuButton(null, ProgramCompilerMenu.flashButton(REQUEST)), "flashing should start");
            helper.assertTrue(menu.task().equals(compiler.compiler().task()) && menu.task().isPresent(), "the menu shows the task");
        });
        helper.runAfterDelay(SETTLE + 90, () -> {
            ItemStack programmer = compiler.slots().getItem(ProgramCompilerBlockEntity.PROGRAMMER_SLOT);
            helper.assertTrue(ProgrammerItem.programOf(programmer).isEmpty(), "not flashed yet");
            helper.assertTrue(menu.powered() && menu.progress() > 0.8f && menu.progress() < 1, "nearly done, at " + menu.progress());
        });
        helper.runAfterDelay(SETTLE + 105, () -> {
            ItemStack programmer = compiler.slots().getItem(ProgramCompilerBlockEntity.PROGRAMMER_SLOT);
            helper.assertTrue(ProgrammerItem.programOf(programmer).equals(Optional.of(REQUEST)), "flashed for the request pipe");
            helper.assertTrue(ProgrammerItem.flashedFor(programmer, Content.pipe(PipeKind.REQUEST).asItem()),
                    "good for crafting a request pipe");
            helper.assertTrue(!ProgrammerItem.flashedFor(programmer, Content.pipe(PipeKind.PROVIDER).asItem()),
                    "and for nothing else");
            helper.assertTrue(junction.stored() == 0, "1000 energy used, left " + junction.stored());
            helper.assertTrue(menu.task().isEmpty(), "the task is over");
            helper.succeed();
        });
    }

    // two pipes halve the twenty seconds, each pays half of the 4000 energy
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void compilingWritesTheProgramOnTheDisk(GameTestHelper helper) {
        ProgramCompilerBlockEntity compiler = compiler(helper, BASIC, false);
        PowerJunctionBlockEntity first = poweredPipe(helper, PipeKind.BASIC, PIPE, 2000);
        PowerJunctionBlockEntity second = poweredPipe(helper, PipeKind.BASIC, COMPILER.north(), 2000);
        ProgramCompilerMenu menu = open(helper, TestPlayer.in(helper));

        helper.runAfterDelay(SETTLE, () ->
                helper.assertTrue(menu.clickMenuButton(null, ProgramCompilerMenu.compileButton(REQUEST)), "compiling should start"));
        helper.runAfterDelay(SETTLE + 190, () ->
                helper.assertTrue(!knowledge(compiler).compiled(REQUEST), "not compiled yet"));
        helper.runAfterDelay(SETTLE + 205, () -> {
            helper.assertTrue(knowledge(compiler).compiled(REQUEST), "the disk has the program");
            helper.assertTrue(menu.disk().orElseThrow().compiled(REQUEST), "and the menu sees it");
            helper.assertTrue(first.stored() == 0 && second.stored() == 0, "4000 energy used");
            helper.succeed();
        });
    }

    // the last tick of unlocking, the two thousand before it are checked without a game
    @GameTest(template = EMPTY)
    public static void unlockingWritesTheCategoryOnTheDiskAndATaskSurvivesSaving(GameTestHelper helper) {
        ProgramCompilerBlockEntity compiler = compiler(helper, CompilerKnowledge.EMPTY, true);
        poweredPipe(helper, PipeKind.BASIC, PIPE, 1000);
        helper.assertTrue(compiler.start(CompilerTask.unlock(ProgramCategory.FLUID)), "unlocking should start");
        CompoundTag saved = compiler.saveWithoutMetadata(helper.getLevel().registryAccess());
        saved.putInt("Work", CompilerTask.Type.UNLOCK.work() - 1);
        compiler.slots().clearContent();
        helper.setBlock(COMPILER, Blocks.AIR);
        ProgramCompilerBlockEntity loaded = compiler(helper, PIPE.east(), null, false);
        loaded.loadWithComponents(saved, helper.getLevel().registryAccess());

        helper.assertTrue(loaded.compiler().task().equals(Optional.of(CompilerTask.unlock(ProgramCategory.FLUID))),
                "the task is still running after loading");
        helper.assertTrue(loaded.slots().getItem(ProgramCompilerBlockEntity.PROGRAMMER_SLOT).is(Content.LOGISTICS_PROGRAMMER.get()),
                "the programmer is still in its slot");
        helper.runAfterDelay(SETTLE, () -> {
            helper.assertTrue(knowledge(loaded).unlocked(ProgramCategory.FLUID), "the category is unlocked");
            helper.assertTrue(!knowledge(loaded).unlocked(ProgramCategory.BASIC), "and no other");
            helper.assertTrue(loaded.compiler().task().isEmpty(), "the task is over");
            helper.succeed();
        });
    }

    // the original took the word of the client for all of this
    @GameTest(template = EMPTY)
    public static void theServerChecksWhatAClientAsksFor(GameTestHelper helper) {
        ProgramCompilerBlockEntity compiler = compiler(helper, BASIC, false);
        ProgramCompilerMenu menu = open(helper, TestPlayer.in(helper));

        helper.assertTrue(!menu.clickMenuButton(null, ProgramCompilerMenu.compileButton(CRAFTER)), "its category is locked");
        helper.assertTrue(!menu.clickMenuButton(null, ProgramCompilerMenu.flashButton(REQUEST)), "not compiled");
        helper.assertTrue(!menu.clickMenuButton(null, ProgramCompilerMenu.unlockButton(ProgramCategory.BASIC)), "unlocked already");
        helper.assertTrue(!menu.clickMenuButton(null, -1) && !menu.clickMenuButton(null, 99) && !menu.clickMenuButton(null, 5000),
                "no such button");
        compiler.slots().setItem(ProgramCompilerBlockEntity.DISK_SLOT, disk(BASIC.with(REQUEST)));
        helper.assertTrue(!menu.clickMenuButton(null, ProgramCompilerMenu.flashButton(REQUEST)), "no programmer");
        compiler.slots().setItem(ProgramCompilerBlockEntity.DISK_SLOT, ItemStack.EMPTY);
        helper.assertTrue(!menu.clickMenuButton(null, ProgramCompilerMenu.unlockButton(ProgramCategory.FLUID)), "no disk");
        helper.assertTrue(compiler.compiler().task().isEmpty(), "nothing started");

        compiler.slots().setItem(ProgramCompilerBlockEntity.DISK_SLOT, disk(BASIC));
        helper.assertTrue(menu.clickMenuButton(null, ProgramCompilerMenu.unlockButton(ProgramCategory.FLUID)), "this one is fine");
        helper.assertTrue(!menu.clickMenuButton(null, ProgramCompilerMenu.compileButton(REQUEST)), "one task at a time");
        helper.succeed();
    }

    // only basic logistics pipes pay, and not the one above
    @GameTest(template = EMPTY)
    public static void withoutABasicPipeBesideItNothingMoves(GameTestHelper helper) {
        ProgramCompilerBlockEntity compiler = compiler(helper, BASIC, false);
        PowerJunctionBlockEntity beside = poweredPipe(helper, PipeKind.PROVIDER, PIPE, 1000);
        helper.setBlock(COMPILER.above(), Content.pipe(PipeKind.BASIC));
        PowerJunctionBlockEntity above = GameTestPower.junction(helper, COMPILER.above().north(), 1000);
        ProgramCompilerMenu menu = open(helper, TestPlayer.in(helper));

        helper.runAfterDelay(SETTLE, () -> helper.assertTrue(compiler.start(CompilerTask.compile(REQUEST)), "compiling should start"));
        helper.runAfterDelay(SETTLE + 20, () -> {
            helper.assertTrue(compiler.compiler().work() == 0, "nothing should be done");
            helper.assertTrue(menu.task().isPresent() && !menu.powered(), "the menu shows that power is missing");
            helper.assertTrue(beside.stored() == 1000 && above.stored() == 1000, "no energy is taken");
            helper.succeed();
        });
    }

    // a task is not finished on another disk than the one it was started with
    @GameTest(template = EMPTY)
    public static void takingTheDiskOutDropsTheTask(GameTestHelper helper) {
        ProgramCompilerBlockEntity compiler = compiler(helper, BASIC, false);
        PowerJunctionBlockEntity junction = poweredPipe(helper, PipeKind.BASIC, PIPE, 1000);

        helper.runAfterDelay(SETTLE, () -> helper.assertTrue(compiler.start(CompilerTask.compile(REQUEST)), "compiling should start"));
        helper.runAfterDelay(SETTLE + 10, () -> {
            helper.assertTrue(compiler.compiler().work() > 0, "work should be under way");
            compiler.slots().setItem(ProgramCompilerBlockEntity.DISK_SLOT, disk(CompilerKnowledge.EMPTY));
        });
        helper.runAfterDelay(SETTLE + 15, () -> {
            helper.assertTrue(compiler.compiler().task().isEmpty(), "the task should be dropped");
            int left = junction.stored();
            helper.assertTrue(left < 1000, "the work done was paid for");
            helper.runAfterDelay(5, () -> {
                helper.assertTrue(junction.stored() == left, "nothing more is taken");
                helper.succeed();
            });
        });
    }

    @GameTest(template = EMPTY)
    public static void slotsTakeOnlyADiskAndAProgrammer(GameTestHelper helper) {
        compiler(helper, null, false);
        Player player = TestPlayer.in(helper);
        ProgramCompilerMenu menu = open(helper, player);
        ItemStack disk = new ItemStack(Content.DISK.get());
        ItemStack programmer = new ItemStack(Content.LOGISTICS_PROGRAMMER.get());

        helper.assertTrue(menu.getSlot(ProgramCompilerMenu.DISK_SLOT).mayPlace(disk)
                && !menu.getSlot(ProgramCompilerMenu.DISK_SLOT).mayPlace(programmer), "the left slot is for a disk");
        helper.assertTrue(menu.getSlot(ProgramCompilerMenu.PROGRAMMER_SLOT).mayPlace(programmer)
                && !menu.getSlot(ProgramCompilerMenu.PROGRAMMER_SLOT).mayPlace(disk), "the right slot is for a programmer");
        player.getInventory().setItem(9, disk);
        player.getInventory().setItem(10, programmer);
        player.getInventory().setItem(11, new ItemStack(Items.STICK));
        for (int slot = 2; slot <= 4; slot++) {
            menu.quickMoveStack(player, slot);
        }
        helper.assertTrue(menu.getSlot(ProgramCompilerMenu.DISK_SLOT).getItem().is(Content.DISK.get())
                && menu.holdsProgrammer(), "shift clicking fills both slots");
        helper.assertTrue(player.getInventory().getItem(11).is(Items.STICK), "a stick stays where it is");
        menu.quickMoveStack(player, ProgramCompilerMenu.DISK_SLOT);
        helper.assertTrue(menu.disk().isEmpty() && player.getInventory().countItem(Content.DISK.get()) == 1,
                "and the disk comes back out");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void breakingTheCompilerDropsWhatIsInIt(GameTestHelper helper) {
        compiler(helper, BASIC, true);

        helper.setBlock(COMPILER, Blocks.AIR);

        helper.assertItemEntityPresent(Content.DISK.get(), COMPILER, 2);
        helper.assertItemEntityPresent(Content.LOGISTICS_PROGRAMMER.get(), COMPILER, 2);
        helper.killAllEntities();
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void theProgrammerSaysWhatItIsFlashedFor(GameTestHelper helper) {
        ItemStack programmer = new ItemStack(Content.LOGISTICS_PROGRAMMER.get());
        helper.assertTrue(tooltip(programmer).size() == 3, "three lines of warning on a blank programmer");
        ProgrammerItem.flash(programmer, CRAFTER);
        List<Component> lines = tooltip(programmer);
        helper.assertTrue(lines.size() == 2 && lines.get(0).equals(Component.translatable(
                "item.routedpipes.logistics_programmer.loaded.module").withStyle(ChatFormatting.GRAY)), "the kind, got " + lines);
        helper.assertTrue(lines.get(1).getString().equals(new ItemStack(ProgrammerItem.itemOf(CRAFTER)).getHoverName().getString()),
                "the name, got " + lines);
        helper.succeed();
    }

    private static List<Component> tooltip(ItemStack stack) {
        List<Component> lines = new ArrayList<>();
        stack.getItem().appendHoverText(stack, Item.TooltipContext.EMPTY, lines, TooltipFlag.NORMAL);
        return lines;
    }

    // a program without an item could never be flashed for anything
    @GameTest(template = EMPTY)
    public static void everyProgramIsForAnItemThatExists(GameTestHelper helper) {
        List<String> missing = ProgramCatalog.all().stream().filter(program -> ProgrammerItem.itemOf(program) == Items.AIR)
                .map(Program::id).toList();
        helper.assertTrue(missing.isEmpty(), "no item for " + missing);
        helper.succeed();
    }
}
