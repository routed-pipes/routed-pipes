package routedpipes.adapter.minecraft.gametest;

import routedpipes.adapter.minecraft.content.Content;
import java.util.Optional;

import routedpipes.adapter.minecraft.PipeBlockEntity;
import routedpipes.adapter.minecraft.PowerJunctionBlockEntity;
import routedpipes.adapter.minecraft.menu.RequestMenu;
import routedpipes.adapter.minecraft.menu.RequestMenu.Source;
import routedpipes.application.LogisticsNetwork;
import routedpipes.domain.logistics.PipeKind;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

public class RequestPreviewGameTest {

    private static final String EMPTY = "empty";
    private static final int SETTLE = 5;
    private static final int PLUS_ONE = 3;
    private static final int PLUS_TEN = 4;
    private static final int PLUS_64 = 5;

    private static PipeBlockEntity pipe(GameTestHelper helper, PipeKind kind, int x) {
        BlockPos pos = new BlockPos(x, 1, 1);
        helper.setBlock(pos, Content.pipe(kind));
        return helper.getBlockEntity(pos);
    }

    private static Container chest(GameTestHelper helper, int x, ItemStack... contents) {
        BlockPos pos = new BlockPos(x, 2, 1);
        helper.setBlock(pos, Blocks.CHEST);
        Container container = helper.getBlockEntity(pos);
        for (int slot = 0; slot < contents.length; slot++) {
            container.setItem(slot, contents[slot]);
        }
        return container;
    }

    // a request pipe, a crafting pipe that saws a log into four planks and a provider with eight logs
    private static PipeBlockEntity sawmill(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity request = pipe(helper, PipeKind.REQUEST, 0);
        chest(helper, 0);
        PipeBlockEntity crafting = pipe(helper, PipeKind.CRAFTING, 1);
        chest(helper, 1);
        crafting.config().setItem(0, new ItemStack(Items.OAK_LOG));
        crafting.config().setItem(PipeBlockEntity.OUTPUT_SLOT, new ItemStack(Items.OAK_PLANKS, 4));
        pipe(helper, PipeKind.PROVIDER, 2);
        chest(helper, 2, new ItemStack(Items.OAK_LOG, 8));
        return request;
    }

    private static void select(GameTestHelper helper, RequestMenu menu, Player player, Item item) {
        for (int slot = 0; slot < RequestMenu.PAGE_SIZE; slot++) {
            if (menu.getSlot(slot).getItem().is(item)) {
                menu.clicked(slot, 0, ClickType.PICKUP, player);
                return;
            }
        }
        helper.fail(item + " should be listed");
    }

    // how many of the item the preview shows as coming from that source, empty if it does not show it
    private static Optional<Integer> previewed(RequestMenu menu, Source source, Item item) {
        for (int slot = RequestMenu.FIRST_PREVIEW_SLOT; slot < RequestMenu.FIRST_PREVIEW_SLOT + RequestMenu.PREVIEW_SIZE; slot++) {
            ItemStack stack = menu.getSlot(slot).getItem();
            if (stack.is(item) && RequestMenu.sourceOf(stack).filter(source::equals).isPresent()) {
                return Optional.of(RequestMenu.listedCount(stack));
            }
        }
        return Optional.empty();
    }

    private static int previewedEntries(RequestMenu menu) {
        int entries = 0;
        for (int slot = RequestMenu.FIRST_PREVIEW_SLOT; slot < RequestMenu.FIRST_PREVIEW_SLOT + RequestMenu.PREVIEW_SIZE; slot++) {
            entries += menu.getSlot(slot).hasItem() ? 1 : 0;
        }
        return entries;
    }

    private static int stored(GameTestHelper helper) {
        PowerJunctionBlockEntity first = helper.getBlockEntity(new BlockPos(0, 1, 2));
        PowerJunctionBlockEntity second = helper.getBlockEntity(new BlockPos(1, 1, 2));
        return first.stored() + second.stored();
    }

    @GameTest(template = EMPTY)
    public static void previewListsStockCraftsAndEnergyWithoutOrdering(GameTestHelper helper) {
        PipeBlockEntity request = sawmill(helper);
        Player player = TestPlayer.in(helper);

        helper.runAfterDelay(SETTLE, () -> {
            int energy = stored(helper);
            RequestMenu menu = new RequestMenu(Content.REQUEST_MENU.get(), 1, request, user -> true);
            helper.assertTrue(menu.previews() && !menu.previewing(), "the menu opens on the list");
            menu.clickMenuButton(player, RequestMenu.BUTTON_PREVIEW);
            helper.assertTrue(!menu.previewing(), "nothing to preview before an item is picked");

            select(helper, menu, player, Items.OAK_PLANKS);
            for (int press = 0; press < 7; press++) {
                menu.clickMenuButton(player, PLUS_ONE);
            }
            helper.assertTrue(menu.amount() == 8, "eight planks are picked, got " + menu.amount());
            menu.clickMenuButton(player, RequestMenu.BUTTON_PREVIEW);

            helper.assertTrue(menu.previewing(), "the preview is on show");
            helper.assertTrue(!menu.getSlot(0).isActive() && menu.getSlot(RequestMenu.FIRST_PREVIEW_SLOT).isActive(),
                    "the preview takes the place of the list");
            helper.assertTrue(previewed(menu, Source.STOCK, Items.OAK_LOG).equals(Optional.of(2)),
                    "two logs come from stock: " + previewed(menu, Source.STOCK, Items.OAK_LOG));
            helper.assertTrue(previewed(menu, Source.CRAFTED, Items.OAK_PLANKS).equals(Optional.of(8)),
                    "eight planks are crafted: " + previewed(menu, Source.CRAFTED, Items.OAK_PLANKS));
            helper.assertTrue(previewedEntries(menu) == 2, "nothing else is listed, got " + previewedEntries(menu));
            long cost = 2 * LogisticsNetwork.ENERGY_PER_PROVIDED_ITEM + 8 * LogisticsNetwork.ENERGY_PER_CRAFTED_ITEM;
            helper.assertTrue(menu.previewEnergy() == cost, "the energy should be " + cost + ", got " + menu.previewEnergy());
            helper.assertTrue(menu.page() == 0 && menu.pages() == 1, "the preview fits on one page");

            // nothing of this was ordered
            PipeBlockEntity crafting = helper.getBlockEntity(new BlockPos(1, 1, 1));
            Container logs = helper.getBlockEntity(new BlockPos(2, 2, 1));
            helper.assertTrue(crafting.outputOrders().isEmpty(), "a preview must not queue a craft");
            helper.assertTrue(logs.countItem(Items.OAK_LOG) == 8, "a preview must not take anything out of the stock");
            helper.assertTrue(stored(helper) == energy, "a preview must not use energy");

            menu.clickMenuButton(player, RequestMenu.BUTTON_PREVIEW);
            helper.assertTrue(!menu.previewing() && menu.getSlot(0).isActive(), "the same button leads back to the list");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void previewFollowsTheAmountAndTellsWhatIsMissing(GameTestHelper helper) {
        PipeBlockEntity request = sawmill(helper);
        Player player = TestPlayer.in(helper);

        helper.runAfterDelay(SETTLE, () -> {
            RequestMenu menu = new RequestMenu(Content.REQUEST_MENU.get(), 1, request, user -> true);
            select(helper, menu, player, Items.OAK_PLANKS);
            menu.clickMenuButton(player, RequestMenu.BUTTON_PREVIEW);
            helper.assertTrue(previewed(menu, Source.CRAFTED, Items.OAK_PLANKS).equals(Optional.of(1)), "one plank to start with");
            helper.assertTrue(previewed(menu, Source.MISSING, Items.OAK_LOG).isEmpty(), "one plank can be had");

            // 75 planks take 19 logs and there are eight
            menu.clickMenuButton(player, PLUS_64);
            menu.clickMenuButton(player, PLUS_TEN);
            helper.assertTrue(menu.previewing(), "changing the amount keeps the preview open");
            helper.assertTrue(previewed(menu, Source.STOCK, Items.OAK_LOG).equals(Optional.of(8)),
                    "all eight logs are used: " + previewed(menu, Source.STOCK, Items.OAK_LOG));
            helper.assertTrue(previewed(menu, Source.CRAFTED, Items.OAK_PLANKS).equals(Optional.of(32)),
                    "they make 32 planks: " + previewed(menu, Source.CRAFTED, Items.OAK_PLANKS));
            helper.assertTrue(previewed(menu, Source.MISSING, Items.OAK_LOG).equals(Optional.of(11)),
                    "eleven logs are missing: " + previewed(menu, Source.MISSING, Items.OAK_LOG));
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void requestingFromThePreviewOrdersAndGoesBackToTheList(GameTestHelper helper) {
        PipeBlockEntity request = sawmill(helper);
        Player player = TestPlayer.in(helper);

        helper.runAfterDelay(SETTLE, () -> {
            RequestMenu menu = new RequestMenu(Content.REQUEST_MENU.get(), 1, request, user -> true);
            select(helper, menu, player, Items.OAK_LOG);
            menu.clickMenuButton(player, PLUS_ONE);
            menu.clickMenuButton(player, RequestMenu.BUTTON_PREVIEW);
            helper.assertTrue(previewed(menu, Source.STOCK, Items.OAK_LOG).equals(Optional.of(2)), "two logs from stock");
            menu.clickMenuButton(player, RequestMenu.BUTTON_REQUEST);
            helper.assertTrue(!menu.previewing(), "the list is back after the request");
        });

        helper.succeedWhen(() -> {
            Container target = helper.getBlockEntity(new BlockPos(0, 2, 1));
            helper.assertTrue(target.countItem(Items.OAK_LOG) == 2, "the logs have not arrived yet");
        });
    }
}
