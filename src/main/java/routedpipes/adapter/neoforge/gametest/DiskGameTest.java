package routedpipes.adapter.neoforge.gametest;

import routedpipes.adapter.minecraft.content.Content;
import java.util.ArrayList;
import java.util.List;

import routedpipes.adapter.minecraft.DiskDrive;
import routedpipes.adapter.minecraft.DiskItem;
import routedpipes.adapter.minecraft.McItemType;
import routedpipes.adapter.minecraft.PipeBlockEntity;
import routedpipes.adapter.minecraft.menu.DiskMenu;
import routedpipes.adapter.minecraft.menu.MacroEditMenu;
import routedpipes.adapter.minecraft.menu.MenuRequests;
import routedpipes.adapter.minecraft.menu.RequestMenu;
import routedpipes.adapter.minecraft.menu.RequestTableMenu;
import routedpipes.domain.item.ItemBatch;
import routedpipes.domain.logistics.PipeKind;
import routedpipes.domain.macro.Macro;
import routedpipes.domain.macro.MacroDisk;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(Content.MODID)
@PrefixGameTestTemplate(false)
public class DiskGameTest {

    private static final String EMPTY = "empty";
    // long enough for every pipe to find its neighbours and for the provider to know its chest
    private static final int SETTLE = 20;
    private static final BlockPos PIPE = new BlockPos(0, 1, 1);

    private static ItemBatch batch(Item item, int count) {
        return new ItemBatch(McItemType.batchOf(new ItemStack(item)).type(), count);
    }

    private static ItemStack disk(MacroDisk contents) {
        ItemStack disk = new ItemStack(Content.DISK.get());
        DiskItem.write(disk, contents);
        return disk;
    }

    private static PipeBlockEntity pipe(GameTestHelper helper, PipeKind kind, BlockPos pos) {
        helper.setBlock(pos, Content.pipe(kind));
        return helper.getBlockEntity(pos);
    }

    private static void chest(GameTestHelper helper, BlockPos pos, ItemStack... contents) {
        helper.setBlock(pos, Blocks.CHEST);
        Container container = helper.getBlockEntity(pos);
        for (int slot = 0; slot < contents.length; slot++) {
            container.setItem(slot, contents[slot]);
        }
    }

    private static int count(GameTestHelper helper, BlockPos pos, Item item) {
        Container container = helper.getBlockEntity(pos);
        return container.countItem(item);
    }

    // a right click on the block the way the game does it
    private static void use(GameTestHelper helper, Player player, BlockPos relative) {
        BlockPos pos = helper.absolutePos(relative);
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
        ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (held.isEmpty() || helper.getBlockState(relative).useItemOn(held, helper.getLevel(), player, InteractionHand.MAIN_HAND,
                hit) == ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION) {
            helper.getBlockState(relative).useWithoutItem(helper.getLevel(), player, hit);
        }
    }

    private static void text(Player player, int field, String text) {
        MenuRequests.applyText(player, player.containerMenu.containerId, field, text);
    }

    // a pipe of that kind with an empty disk in it and a chest on top, and a provider with cobblestone and three diamonds
    private static PipeBlockEntity macroScene(GameTestHelper helper, PipeKind kind) {
        GameTestPower.supply(helper);
        PipeBlockEntity request = pipe(helper, kind, PIPE);
        chest(helper, PIPE.above());
        pipe(helper, PipeKind.PROVIDER, PIPE.east());
        chest(helper, PIPE.east().above(), new ItemStack(Items.COBBLESTONE, 64), new ItemStack(Items.DIAMOND, 3));
        request.diskDrive().slot().setItem(0, new ItemStack(Content.DISK.get()));
        return request;
    }

    @GameTest(template = EMPTY)
    public static void diskKeepsItsNameAndMacrosThroughSaving(GameTestHelper helper) {
        ItemStack named = new ItemStack(Items.DIAMOND_SWORD);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Sting"));
        MacroDisk contents = new MacroDisk("spares", List.of(
                new Macro("tools", List.of(batch(Items.STICK, 200), McItemType.batchOf(named))),
                new Macro("food", List.of(batch(Items.BREAD, 8)))));
        ItemStack disk = disk(contents);

        ItemStack loaded = ItemStack.parse(helper.getLevel().registryAccess(), disk.save(helper.getLevel().registryAccess()))
                .orElseThrow();
        helper.assertTrue(DiskItem.read(loaded).equals(contents), "the disk should come back as it was saved");
        helper.assertTrue(ItemStack.isSameItemSameComponents(disk, loaded), "and be the same stack");
        helper.assertTrue(disk.getMaxStackSize() == 1, "disks do not stack");
        helper.assertTrue(DiskItem.read(new ItemStack(Content.DISK.get())).equals(MacroDisk.EMPTY), "a new disk is empty");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void diskShowsItsNameInTheTooltip(GameTestHelper helper) {
        List<Component> blank = new ArrayList<>();
        new ItemStack(Content.DISK.get()).getItem().appendHoverText(new ItemStack(Content.DISK.get()),
                Item.TooltipContext.EMPTY, blank, TooltipFlag.NORMAL);
        List<Component> named = new ArrayList<>();
        ItemStack disk = disk(MacroDisk.EMPTY.named("spares"));
        disk.getItem().appendHoverText(disk, Item.TooltipContext.EMPTY, named, TooltipFlag.NORMAL);

        helper.assertTrue(blank.size() == 1, "a disk without a name only has its description");
        helper.assertTrue(named.size() == 2 && named.get(1).getString().equals("spares"), "the name is on the tooltip");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void requestPipeMk2TakesAHeldDiskAndThrowsItOutOnAClick(GameTestHelper helper) {
        PipeBlockEntity request = pipe(helper, PipeKind.REQUEST_MK2, PIPE);
        DiskDrive drive = request.diskDrive();
        TestPlayer player = TestPlayer.in(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, disk(MacroDisk.EMPTY.named("first")));

        use(helper, player, PIPE);
        helper.assertTrue(drive.disk().name().equals("first") && player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty(),
                "the disk goes from the hand into the pipe");
        helper.assertTrue(!(player.containerMenu instanceof RequestMenu), "and no screen opens");

        ItemStack second = disk(MacroDisk.EMPTY.named("second"));
        player.setItemInHand(InteractionHand.MAIN_HAND, second);
        use(helper, player, PIPE);
        helper.assertTrue(drive.disk().name().equals("first") && player.getItemInHand(InteractionHand.MAIN_HAND) == second,
                "a second disk stays in the hand");
        helper.assertTrue(player.containerMenu instanceof RequestMenu menu && menu.takesDisk() && menu.holdsDisk(),
                "and the pipe opens with the disk shown");

        player.containerMenu.clicked(RequestMenu.DISK_SLOT, 0, ClickType.PICKUP, player);
        helper.assertTrue(!drive.holdsDisk() && player.containerMenu.getCarried().isEmpty(), "a click throws the disk out");
        helper.assertItemEntityPresent(Content.DISK.get(), PIPE, 2);
        helper.assertTrue(!player.containerMenu.clickMenuButton(player, RequestMenu.BUTTON_DISK)
                && player.containerMenu instanceof RequestMenu, "without a disk there are no macros to look at");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void plainRequestPipeTakesNoDisk(GameTestHelper helper) {
        PipeBlockEntity request = pipe(helper, PipeKind.REQUEST, PIPE);
        TestPlayer player = TestPlayer.in(helper);
        ItemStack held = disk(MacroDisk.EMPTY);
        player.setItemInHand(InteractionHand.MAIN_HAND, held);

        use(helper, player, PIPE);
        helper.assertTrue(request.diskDrive() == null && player.getItemInHand(InteractionHand.MAIN_HAND) == held,
                "the disk stays in the hand");
        helper.assertTrue(player.containerMenu instanceof RequestMenu menu && !menu.takesDisk(), "the pipe just opens");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void requestPipeMk2KeepsItsDiskThroughSavingAndDropsItWhenBroken(GameTestHelper helper) {
        PipeBlockEntity request = pipe(helper, PipeKind.REQUEST_MK2, PIPE);
        MacroDisk contents = MacroDisk.EMPTY.named("spares").saved(new Macro("food", List.of(batch(Items.BREAD, 8))));
        request.diskDrive().slot().setItem(0, disk(contents));

        PipeBlockEntity loaded = pipe(helper, PipeKind.REQUEST_MK2, PIPE.east(3));
        loaded.loadWithComponents(request.saveWithoutMetadata(helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        helper.assertTrue(loaded.diskDrive().disk().equals(contents), "the disk is saved with the pipe");
        loaded.diskDrive().slot().clearContent();

        helper.setBlock(PIPE, Blocks.AIR);
        helper.assertItemEntityPresent(Content.DISK.get(), PIPE, 2);
        helper.succeed();
    }

    // the whole way through the screens: name the disk, put a macro together, save it and order it
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void macroIsPutTogetherSavedAndOrdered(GameTestHelper helper) {
        PipeBlockEntity request = macroScene(helper, PipeKind.REQUEST_MK2);
        DiskDrive drive = request.diskDrive();
        TestPlayer player = TestPlayer.in(helper);

        helper.runAfterDelay(SETTLE, () -> {
            use(helper, player, PIPE);
            player.containerMenu.clickMenuButton(player, RequestMenu.BUTTON_DISK);
            helper.assertTrue(player.containerMenu instanceof DiskMenu, "the disk button shows the macros");
            text(player, DiskMenu.TEXT_DISK_NAME, " spares ");
            helper.assertTrue(drive.disk().name().equals("spares"), "the disk takes its name");

            text(player, DiskMenu.TEXT_EDIT, "");
            helper.assertTrue(player.containerMenu instanceof MacroEditMenu, "with no macro picked a new one is started");
            MacroEditMenu editor = (MacroEditMenu) player.containerMenu;
            helper.assertTrue(editor.getSlot(0).getItem().is(Items.COBBLESTONE) && editor.getSlot(1).getItem().is(Items.DIAMOND),
                    "the editor lists what the network has");
            editor.clicked(0, 0, ClickType.PICKUP, player);
            editor.clickMenuButton(player, 4);
            editor.clickMenuButton(player, RequestMenu.BUTTON_REQUEST);
            helper.assertTrue(editor.macro().entries().equals(List.of(batch(Items.COBBLESTONE, 11))),
                    "the picked item and amount go into the macro");
            editor.clickMenuButton(player, MacroEditMenu.BUTTON_SAVE);
            helper.assertTrue(player.containerMenu == editor && drive.disk().macros().isEmpty(), "no saving without a name");

            text(player, MacroEditMenu.TEXT_NAME, "build");
            editor.clicked(1, 0, ClickType.PICKUP, player);
            editor.clickMenuButton(player, 1);
            editor.clickMenuButton(player, 3);
            editor.clickMenuButton(player, RequestMenu.BUTTON_REQUEST);
            editor.clicked(0, 0, ClickType.PICKUP, player);
            editor.clickMenuButton(player, RequestMenu.BUTTON_REQUEST);
            helper.assertTrue(editor.macro().entries().equals(List.of(batch(Items.COBBLESTONE, 13), batch(Items.DIAMOND, 2))),
                    "adding an item again raises its amount");
            editor.clickMenuButton(player, MacroEditMenu.BUTTON_REMOVE);
            helper.assertTrue(editor.macro().entries().equals(List.of(batch(Items.DIAMOND, 2))), "an entry can be taken out");
            editor.clickMenuButton(player, RequestMenu.BUTTON_REQUEST);
            helper.assertTrue(request.travellingItems().isEmpty() && count(helper, PIPE.east().above(), Items.COBBLESTONE) == 64,
                    "nothing is ordered while editing");

            editor.clickMenuButton(player, MacroEditMenu.BUTTON_SAVE);
            Macro saved = new Macro("build", List.of(batch(Items.DIAMOND, 2), batch(Items.COBBLESTONE, 2)));
            helper.assertTrue(player.containerMenu instanceof DiskMenu menu && menu.disk().macros().equals(List.of(saved)),
                    "saving writes the macro to the disk and goes back to the list");

            text(player, DiskMenu.TEXT_EDIT, "build");
            helper.assertTrue(player.containerMenu instanceof MacroEditMenu opened && opened.macro().equals(saved),
                    "a saved macro opens in the editor as it is");
            player.containerMenu.clickMenuButton(player, MacroEditMenu.BUTTON_BACK);
            helper.assertTrue(player.containerMenu instanceof DiskMenu && drive.disk().macros().equals(List.of(saved)),
                    "going back changes nothing");

            text(player, DiskMenu.TEXT_REQUEST, "build");
            player.containerMenu.clickMenuButton(player, DiskMenu.BUTTON_BACK);
            helper.assertTrue(player.containerMenu instanceof RequestMenu, "back leads to the request screen");
        });

        helper.succeedWhen(() -> helper.assertTrue(count(helper, PIPE.above(), Items.DIAMOND) == 2
                && count(helper, PIPE.above(), Items.COBBLESTONE) == 2, "the macro should have been delivered"));
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void macroIsOrderedCompletelyOrNotAtAll(GameTestHelper helper) {
        PipeBlockEntity request = macroScene(helper, PipeKind.REQUEST_MK2);
        DiskDrive drive = request.diskDrive();
        Macro tooMuch = new Macro("too much", List.of(batch(Items.COBBLESTONE, 10), batch(Items.DIAMOND, 5)));
        Macro fine = new Macro("fine", List.of(batch(Items.COBBLESTONE, 10)));
        drive.write(MacroDisk.EMPTY.saved(tooMuch).saved(fine));
        TestPlayer player = TestPlayer.in(helper);

        helper.runAfterDelay(SETTLE, () -> {
            drive.open(player);
            text(player, DiskMenu.TEXT_REQUEST, "too much");
            text(player, DiskMenu.TEXT_REQUEST, "no such macro");
            text(player, DiskMenu.TEXT_DELETE, "fine");
            helper.assertTrue(drive.disk().macros().equals(List.of(tooMuch)), "the deleted macro is gone");
            text(player, DiskMenu.TEXT_REQUEST, "fine");
        });
        helper.runAfterDelay(100, () -> {
            helper.assertContainerEmpty(PIPE.above());
            helper.assertTrue(count(helper, PIPE.east().above(), Items.COBBLESTONE) == 64, "nothing left the provider");

            drive.eject();
            helper.assertTrue(!player.containerMenu.stillValid(player), "the list closes when the disk is gone");
            helper.succeed();
        });
    }

    // the original only took a held disk when one was in the slot already
    @GameTest(template = EMPTY)
    public static void requestTableTakesAHeldDiskWhenItsSlotIsEmpty(GameTestHelper helper) {
        PipeBlockEntity table = pipe(helper, PipeKind.REQUEST_TABLE, PIPE);
        DiskDrive drive = table.diskDrive();
        TestPlayer player = TestPlayer.in(helper);
        player.setItemInHand(InteractionHand.MAIN_HAND, disk(MacroDisk.EMPTY.named("first")));

        use(helper, player, PIPE);
        helper.assertTrue(drive.disk().name().equals("first") && player.getItemInHand(InteractionHand.MAIN_HAND).isEmpty(),
                "the disk goes from the hand into the empty slot");
        helper.assertTrue(!(player.containerMenu instanceof RequestTableMenu), "and no screen opens");

        ItemStack second = disk(MacroDisk.EMPTY.named("second"));
        player.setItemInHand(InteractionHand.MAIN_HAND, second);
        use(helper, player, PIPE);
        helper.assertTrue(drive.disk().name().equals("first") && player.getItemInHand(InteractionHand.MAIN_HAND) == second,
                "the disk in the slot is not replaced");
        helper.assertTrue(player.containerMenu instanceof RequestTableMenu menu && menu.holdsDisk(), "the table opens instead");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void requestTableDiskSlotHoldsOneDiskAndIsSavedAndDropped(GameTestHelper helper) {
        PipeBlockEntity table = pipe(helper, PipeKind.REQUEST_TABLE, PIPE);
        TestPlayer player = TestPlayer.in(helper);
        use(helper, player, PIPE);
        RequestTableMenu menu = (RequestTableMenu) player.containerMenu;
        menu.clickMenuButton(player, RequestTableMenu.BUTTON_DISK);
        helper.assertTrue(!menu.holdsDisk() && player.containerMenu == menu, "without a disk the disk button opens nothing");

        menu.setCarried(new ItemStack(Items.COBBLESTONE, 3));
        menu.clicked(RequestTableMenu.DISK_SLOT, 0, ClickType.PICKUP, player);
        helper.assertTrue(!menu.holdsDisk() && menu.getCarried().getCount() == 3, "only disks go into the disk slot");
        MacroDisk contents = MacroDisk.EMPTY.named("spares").saved(new Macro("food", List.of(batch(Items.BREAD, 8))));
        menu.setCarried(disk(contents));
        menu.clicked(RequestTableMenu.DISK_SLOT, 0, ClickType.PICKUP, player);
        helper.assertTrue(menu.getCarried().isEmpty() && table.diskDrive().disk().equals(contents), "a disk does");

        menu.quickMoveStack(player, RequestTableMenu.DISK_SLOT);
        helper.assertTrue(!menu.holdsDisk() && player.getInventory().countItem(Content.DISK.get()) == 1,
                "a shift click takes the disk back out");
        table.diskDrive().slot().setItem(0, disk(contents));

        PipeBlockEntity loaded = pipe(helper, PipeKind.REQUEST_TABLE, PIPE.east(3));
        loaded.loadWithComponents(table.saveWithoutMetadata(helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        helper.assertTrue(loaded.diskDrive().disk().equals(contents), "the disk is saved with the table");
        loaded.diskDrive().slot().clearContent();

        helper.setBlock(PIPE, Blocks.AIR);
        helper.assertItemEntityPresent(Content.DISK.get(), PIPE, 2);
        helper.succeed();
    }

    // the table has the same macro screens as the pipe, what a macro orders lands in its buffer
    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void requestTableOrdersAMacroIntoItsBuffer(GameTestHelper helper) {
        PipeBlockEntity table = macroScene(helper, PipeKind.REQUEST_TABLE);
        TestPlayer player = TestPlayer.in(helper);

        helper.runAfterDelay(SETTLE, () -> {
            use(helper, player, PIPE);
            player.containerMenu.clickMenuButton(player, RequestTableMenu.BUTTON_DISK);
            helper.assertTrue(player.containerMenu instanceof DiskMenu, "the disk button shows the macros");
            text(player, DiskMenu.TEXT_EDIT, "");
            MacroEditMenu editor = (MacroEditMenu) player.containerMenu;
            text(player, MacroEditMenu.TEXT_NAME, "gems");
            editor.clicked(1, 0, ClickType.PICKUP, player);
            editor.clickMenuButton(player, 3);
            editor.clickMenuButton(player, RequestMenu.BUTTON_REQUEST);
            editor.clickMenuButton(player, MacroEditMenu.BUTTON_SAVE);
            helper.assertTrue(table.diskDrive().disk().macro("gems").isPresent(), "the macro is on the disk of the table");

            text(player, DiskMenu.TEXT_REQUEST, "gems");
            player.containerMenu.clickMenuButton(player, DiskMenu.BUTTON_BACK);
            helper.assertTrue(player.containerMenu instanceof RequestTableMenu, "back leads to the table");
        });

        helper.succeedWhen(() -> {
            helper.assertTrue(table.requestTable().buffer().countItem(Items.DIAMOND) == 2, "the diamonds should be in the buffer");
            helper.assertContainerEmpty(PIPE.above());
        });
    }
}
