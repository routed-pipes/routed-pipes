package routedpipes.adapter.neoforge.gametest;

import routedpipes.adapter.minecraft.content.Content;
import routedpipes.adapter.minecraft.CraftingTableBlockEntity;
import routedpipes.adapter.minecraft.McItemType;
import routedpipes.adapter.minecraft.PipeBlockEntity;
import routedpipes.adapter.minecraft.menu.RequestMenu;
import routedpipes.domain.item.ItemType;
import routedpipes.domain.logistics.PipeKind;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// the request screen gives up on what is still being crafted for its pipe
@GameTestHolder(Content.MODID)
@PrefixGameTestTemplate(false)
public class CancelOrderGameTest {

    private static final String EMPTY = "empty";
    private static final int SETTLE = 15;

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

    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void theCancelButtonGivesUpOnWhatIsBeingCrafted(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity request = pipe(helper, PipeKind.REQUEST, 0);
        Container target = chest(helper, 0);
        pipe(helper, PipeKind.PROVIDER, 1);
        Container source = chest(helper, 1, new ItemStack(Items.OAK_LOG, 16));
        PipeBlockEntity crafting = pipe(helper, PipeKind.CRAFTING, 2);
        BlockPos tablePos = new BlockPos(2, 2, 1);
        helper.setBlock(tablePos, Content.CRAFTING_TABLE.get());
        CraftingTableBlockEntity table = helper.getBlockEntity(tablePos);
        table.pattern().setItem(0, new ItemStack(Items.OAK_LOG));
        crafting.importRecipe();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        ItemType planks = McItemType.batchOf(new ItemStack(Items.OAK_PLANKS)).type();

        helper.runAfterDelay(SETTLE, () -> {
            RequestMenu menu = new RequestMenu(Content.REQUEST_MENU.get(), 1, request, user -> true);
            helper.assertTrue(menu.openOrders() == 0, "nothing is being crafted yet");
            helper.assertTrue(request.request(McItemType.batchOf(new ItemStack(Items.OAK_PLANKS, 8))).started(), "planks can be crafted");
            menu = new RequestMenu(Content.REQUEST_MENU.get(), 1, request, user -> true);
            helper.assertTrue(menu.openOrders() == 8, "the screen knows that 8 planks are being crafted, says " + menu.openOrders());

            helper.assertTrue(menu.clickMenuButton(player, RequestMenu.BUTTON_CANCEL), "the button is taken");
            helper.assertTrue(menu.openOrders() == 0 && crafting.outputOrders().isEmpty(), "the order is gone");
        });

        helper.runAfterDelay(150, () -> {
            helper.assertTrue(target.countItem(Items.OAK_PLANKS) == 0, "no planks arrive, found " + target.countItem(Items.OAK_PLANKS));
            helper.assertTrue(source.countItem(Items.OAK_LOG) == 14 && table.storage().countItem(Items.OAK_LOG) == 2,
                    "the two logs that were on their way end up in the table: " + table.storage().countItem(Items.OAK_LOG));
            Integer offered = request.available().get(planks);
            helper.assertTrue(offered != null && offered == 8, "what they make can be had by the next request, offered " + offered);
            helper.succeed();
        });
    }
}
