package logisticspipes.adapter.neoforge.gametest;

import logisticspipes.adapter.minecraft.PipeBlockEntity;
import logisticspipes.adapter.minecraft.RemoteOrdererItem;
import logisticspipes.adapter.minecraft.menu.RequestMenu;
import logisticspipes.adapter.neoforge.LogisticsPipes;
import logisticspipes.domain.logistics.PipeKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// what the server does when a client says the key for the remote orderer was pressed
@GameTestHolder(LogisticsPipes.MODID)
@PrefixGameTestTemplate(false)
public class RemoteOrdererKeyGameTest {

    private static final String EMPTY = "empty";
    private static final int SETTLE = 5;

    // an orderer linked to the pipe at that place the way a player links it
    private static ItemStack linkedOrderer(GameTestHelper helper, TestPlayer player, BlockPos pipe) {
        ItemStack orderer = new ItemStack(LogisticsPipes.REMOTE_ORDERER.get());
        BlockPos pos = helper.absolutePos(pipe);
        orderer.useOn(new UseOnContext(helper.getLevel(), player, InteractionHand.MAIN_HAND, orderer,
                new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false)));
        helper.assertTrue(RemoteOrdererItem.isLinked(orderer), "the orderer should be linked");
        return orderer;
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void keyOpensTheOrdererFromAnywhereInTheInventory(GameTestHelper helper) {
        GameTestPower.supply(helper);
        BlockPos pipePos = new BlockPos(0, 1, 1);
        helper.setBlock(pipePos, LogisticsPipes.pipe(PipeKind.REQUEST));
        BlockPos target = new BlockPos(0, 2, 1);
        helper.setBlock(target, Blocks.CHEST);
        helper.setBlock(new BlockPos(1, 1, 1), LogisticsPipes.pipe(PipeKind.PROVIDER));
        helper.setBlock(new BlockPos(1, 2, 1), Blocks.CHEST);
        Container source = helper.getBlockEntity(new BlockPos(1, 2, 1));
        source.setItem(0, new ItemStack(Items.DIAMOND, 9));
        TestPlayer player = TestPlayer.in(helper);
        player.setPos(player.getX() + 500, player.getY(), player.getZ());
        // an unlinked one comes first, the linked one sits deep in the backpack rows
        player.getInventory().setItem(3, new ItemStack(LogisticsPipes.REMOTE_ORDERER.get()));
        player.getInventory().setItem(30, linkedOrderer(helper, player, pipePos));

        helper.runAfterDelay(SETTLE, () -> {
            RemoteOrdererItem.openFromInventory(player);
            helper.assertTrue(player.containerMenu instanceof RequestMenu, "the key opens the request screen");
            helper.assertTrue(player.getMainHandItem().isEmpty(), "the orderer does not have to be in the hand");
            RequestMenu menu = (RequestMenu) player.containerMenu;
            helper.assertTrue(menu.stillValid(player), "distance does not matter");
            menu.clicked(0, 0, ClickType.PICKUP, player);
            menu.clickMenuButton(player, RequestMenu.BUTTON_REQUEST);
        });

        helper.succeedWhen(() -> {
            Container chest = helper.getBlockEntity(target);
            helper.assertTrue(chest.countItem(Items.DIAMOND) == 1, "the order made through the key arrives");
        });
    }

    @GameTest(template = EMPTY)
    public static void keyDoesNothingWithoutALinkedOrdererOrItsPipe(GameTestHelper helper) {
        GameTestPower.supply(helper);
        BlockPos pipePos = new BlockPos(0, 1, 1);
        helper.setBlock(pipePos, LogisticsPipes.pipe(PipeKind.REQUEST));
        PipeBlockEntity request = helper.getBlockEntity(pipePos);
        TestPlayer player = TestPlayer.in(helper);

        RemoteOrdererItem.openFromInventory(player);
        helper.assertTrue(player.containerMenu == player.inventoryMenu, "a player without an orderer opens nothing");

        player.getInventory().setItem(5, new ItemStack(LogisticsPipes.REMOTE_ORDERER.get()));
        RemoteOrdererItem.openFromInventory(player);
        helper.assertTrue(player.containerMenu == player.inventoryMenu, "an unlinked orderer opens nothing");

        player.getInventory().setItem(5, linkedOrderer(helper, player, pipePos));
        RemoteOrdererItem.openFromInventory(player);
        helper.assertTrue(player.containerMenu instanceof RequestMenu, "the linked orderer opens the pipe");

        player.containerMenu = player.inventoryMenu;
        helper.setBlock(pipePos, LogisticsPipes.pipe(PipeKind.PROVIDER));
        RemoteOrdererItem.openFromInventory(player);
        helper.assertTrue(player.containerMenu == player.inventoryMenu, "a pipe of another kind in its place is not opened");

        helper.setBlock(pipePos, Blocks.AIR);
        RemoteOrdererItem.openFromInventory(player);
        helper.assertTrue(player.containerMenu == player.inventoryMenu, "a removed pipe is not opened");
        helper.assertTrue(request.isRemoved(), "the pipe is gone");
        helper.succeed();
    }
}
