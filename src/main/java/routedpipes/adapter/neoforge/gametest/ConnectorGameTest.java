package routedpipes.adapter.neoforge.gametest;

import routedpipes.adapter.minecraft.content.Content;
import java.util.UUID;

import routedpipes.adapter.minecraft.ChannelRegistry;
import routedpipes.adapter.minecraft.ConnectorPipeBlock;
import routedpipes.adapter.minecraft.ConnectorPipeBlock.LinkState;
import routedpipes.adapter.minecraft.McItemType;
import routedpipes.adapter.minecraft.PipeBlockEntity;
import routedpipes.adapter.minecraft.PowerJunctionBlockEntity;
import routedpipes.adapter.minecraft.SecurityStationBlockEntity;
import routedpipes.adapter.minecraft.SystemConnector;
import routedpipes.adapter.minecraft.menu.ChannelMenu;
import routedpipes.adapter.minecraft.menu.SecurityStationMenu;
import routedpipes.application.RequestOutcome;
import routedpipes.domain.channel.Channel;
import routedpipes.domain.channel.ChannelAccess;
import routedpipes.domain.channel.Channels;
import routedpipes.domain.logistics.PipeKind;
import routedpipes.domain.security.Permission;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// two networks side by side: a provider and a connector, a chest both connectors touch, a connector and a request pipe
@GameTestHolder(Content.MODID)
@PrefixGameTestTemplate(false)
public class ConnectorGameTest {

    private static final String EMPTY = "empty";
    private static final int SETTLE = 5;
    private static final BlockPos SHARED = new BlockPos(2, 1, 1);

    private static PipeBlockEntity pipe(GameTestHelper helper, PipeKind kind, int x) {
        BlockPos pos = new BlockPos(x, 1, 1);
        helper.setBlock(pos, Content.pipe(kind));
        return helper.getBlockEntity(pos);
    }

    private static BlockPos chest(GameTestHelper helper, BlockPos pos, ItemStack... contents) {
        helper.setBlock(pos, Blocks.CHEST);
        Container container = helper.getBlockEntity(pos);
        for (int slot = 0; slot < contents.length; slot++) {
            container.setItem(slot, contents[slot]);
        }
        return pos;
    }

    private static int count(GameTestHelper helper, BlockPos pos, Item item) {
        Container container = helper.getBlockEntity(pos);
        return container.countItem(item);
    }

    private static Channels channels(GameTestHelper helper) {
        return ChannelRegistry.of(helper.getLevel()).channels();
    }

    // a channel nobody else sees, the tests of a run share one world
    private static UUID channel(GameTestHelper helper) {
        return channels(helper).create("test", UUID.randomUUID(), "Tester", ChannelAccess.PRIVATE, null).orElseThrow().id();
    }

    private static LinkState shown(GameTestHelper helper, int x) {
        return helper.getBlockState(new BlockPos(x, 1, 1)).getValue(ConnectorPipeBlock.LINK);
    }

    // the provider with its chest and the first connector, the junctions of GameTestPower are beside them
    private static PipeBlockEntity providerSide(GameTestHelper helper) {
        pipe(helper, PipeKind.PROVIDER, 0);
        chest(helper, new BlockPos(0, 2, 1), new ItemStack(Items.COBBLESTONE, 64));
        return pipe(helper, PipeKind.INVENTORY_SYSTEM_CONNECTOR, 1);
    }

    @GameTest(template = EMPTY, timeoutTicks = 300)
    public static void itemsCrossTheInventoryBetweenTwoConnectorsOnTheSameChannel(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity near = providerSide(helper);
        chest(helper, SHARED);
        PipeBlockEntity far = pipe(helper, PipeKind.INVENTORY_SYSTEM_CONNECTOR, 3);
        PipeBlockEntity request = pipe(helper, PipeKind.REQUEST, 4);
        BlockPos target = chest(helper, new BlockPos(4, 2, 1));
        // power does not cross, so the other side needs its own
        PowerJunctionBlockEntity junction = GameTestPower.junction(helper, new BlockPos(4, 1, 2), PowerJunctionBlockEntity.CAPACITY);
        UUID channel = channel(helper);
        near.connector().setChannel(channel);
        far.connector().setChannel(channel);

        helper.runAfterDelay(SETTLE, () -> {
            helper.assertTrue(shown(helper, 1) == LinkState.CONNECTED && shown(helper, 3) == LinkState.CONNECTED,
                    "both connectors show that they found each other");
            helper.assertTrue(request.available().getOrDefault(McItemType.batchOf(new ItemStack(Items.COBBLESTONE)).type(), 0) == 64,
                    "the request pipe sees what the provider of the other network has");
            helper.assertTrue(request.request(McItemType.batchOf(new ItemStack(Items.COBBLESTONE, 10))).started(),
                    "and can order it");
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(count(helper, target, Items.COBBLESTONE) == 10, "the order arrives on the other side");
            helper.assertTrue(count(helper, SHARED, Items.COBBLESTONE) == 0, "nothing stays in the shared inventory");
            helper.assertTrue(far.connector().expected().isEmpty(), "and the connector waits for nothing more");
            helper.assertTrue(junction.stored() == PowerJunctionBlockEntity.CAPACITY - 10 * 2 - SystemConnector.ENERGY_PER_STACK,
                    "taking the stack out costs the connector that does it");
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void powerDoesNotCrossTheInventory(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity near = providerSide(helper);
        chest(helper, SHARED);
        PipeBlockEntity far = pipe(helper, PipeKind.INVENTORY_SYSTEM_CONNECTOR, 3);
        PipeBlockEntity request = pipe(helper, PipeKind.REQUEST, 4);
        UUID channel = channel(helper);
        near.connector().setChannel(channel);
        far.connector().setChannel(channel);

        helper.runAfterDelay(SETTLE, () -> {
            RequestOutcome outcome = request.request(McItemType.batchOf(new ItemStack(Items.COBBLESTONE, 10)));
            helper.assertTrue(outcome.missing().isEmpty() && !outcome.powered(), "the cobblestone is there but the power to move it is not");
            helper.assertTrue(near.usePower(1) && !far.usePower(1), "only the side with the junctions has power");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 100)
    public static void aConnectorShowsWhetherItFoundTheOthersOnItsChannel(GameTestHelper helper) {
        PipeBlockEntity near = providerSide(helper);
        PipeBlockEntity far = pipe(helper, PipeKind.INVENTORY_SYSTEM_CONNECTOR, 3);
        UUID channel = channel(helper);
        near.connector().setChannel(channel);

        helper.runAfterDelay(SETTLE, () -> {
            helper.assertTrue(shown(helper, 1) == LinkState.DISCONNECTED, "alone on its channel");
            far.connector().setChannel(channel);
        });
        helper.runAfterDelay(2 * SETTLE, () -> {
            helper.assertTrue(shown(helper, 1) == LinkState.MISMATCHED && shown(helper, 3) == LinkState.MISMATCHED,
                    "found each other, but there is no inventory");
            chest(helper, SHARED);
        });
        helper.runAfterDelay(60, () -> {
            helper.assertTrue(shown(helper, 1) == LinkState.CONNECTED && shown(helper, 3) == LinkState.CONNECTED, "connected");
            helper.assertTrue(near.networkStatus().orElseThrow().routers() == 2, "the networks are joined: the provider and the other connector");
            channels(helper).delete(channel, channels(helper).find(channel).orElseThrow().owner());
        });
        helper.runAfterDelay(60 + SETTLE, () -> {
            helper.assertTrue(near.connector().channel().isEmpty() && shown(helper, 1) == LinkState.DISCONNECTED,
                    "a deleted channel connects nothing");
            helper.assertTrue(near.networkStatus().orElseThrow().routers() == 1, "the networks are apart again");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void channelAndResistanceSurviveSaveAndLoad(GameTestHelper helper) {
        PipeBlockEntity connector = pipe(helper, PipeKind.INVENTORY_SYSTEM_CONNECTOR, 1);
        UUID channel = channel(helper);
        connector.connector().setChannel(channel);
        connector.connector().setResistance(7);

        PipeBlockEntity loaded = pipe(helper, PipeKind.INVENTORY_SYSTEM_CONNECTOR, 3);
        loaded.loadWithComponents(connector.saveWithoutMetadata(helper.getLevel().registryAccess()), helper.getLevel().registryAccess());

        helper.assertTrue(loaded.connector().channel().filter(channel::equals).isPresent(), "the channel stays");
        helper.assertTrue(loaded.connector().resistance() == 7, "the resistance stays");
        helper.succeed();
    }

    private static int slotOf(ChannelMenu menu, String name) {
        for (int slot = 0; slot < ChannelMenu.CHANNELS_PER_PAGE; slot++) {
            if (menu.getSlot(ChannelMenu.FIRST_CHANNEL_SLOT + slot).getItem().getHoverName().getString().equals(name)) {
                return slot;
            }
        }
        return -1;
    }

    private static Channel named(GameTestHelper helper, String name) {
        return channels(helper).all().stream().filter(channel -> channel.name().equals(name)).findFirst().orElseThrow();
    }

    @GameTest(template = EMPTY)
    public static void channelsAreMadePickedChangedAndDeletedInTheScreenOfAConnector(GameTestHelper helper) {
        PipeBlockEntity connector = pipe(helper, PipeKind.INVENTORY_SYSTEM_CONNECTOR, 1);
        TestPlayer owner = TestPlayer.in(helper);
        TestPlayer stranger = TestPlayer.in(helper);
        String name = "mine " + UUID.randomUUID();
        String shorter = name.substring(0, Channels.MAX_NAME);
        owner.openMenu(connector.menuProvider());
        ChannelMenu menu = (ChannelMenu) owner.containerMenu;

        helper.assertTrue(menu.onPipe() && menu.getSlot(ChannelMenu.CURRENT_SLOT).getItem().isEmpty(), "a new connector is on no channel");
        menu.clickMenuButton(owner, ChannelMenu.BUTTON_NEW_ACCESS);
        helper.assertTrue(menu.newAccess() == ChannelAccess.PRIVATE, "without a station a channel is public or private");
        menu.setText(ChannelMenu.TEXT_ADD, name);
        Channel made = named(helper, shorter);
        helper.assertTrue(made.access() == ChannelAccess.PRIVATE && made.ownedBy(owner.getUUID()), "the channel belongs to who made it");
        helper.assertTrue(menu.selectedSlot() == slotOf(menu, shorter) && menu.ownsSelected(), "and is selected");

        menu.clickMenuButton(owner, ChannelMenu.BUTTON_USE);
        helper.assertTrue(connector.connector().channel().filter(made.id()::equals).isPresent(), "the pipe is put on the selected channel");
        menu.broadcastChanges();
        helper.assertTrue(menu.getSlot(ChannelMenu.CURRENT_SLOT).getItem().getHoverName().getString().equals(shorter), "and shows it");

        menu.clickMenuButton(owner, ChannelMenu.BUTTON_RESISTANCE_UP_10);
        menu.clickMenuButton(owner, ChannelMenu.BUTTON_RESISTANCE_DOWN);
        menu.clickMenuButton(owner, ChannelMenu.BUTTON_RESISTANCE_DOWN_10);
        menu.clickMenuButton(owner, ChannelMenu.BUTTON_RESISTANCE_UP);
        menu.clickMenuButton(owner, ChannelMenu.BUTTON_RESISTANCE_UP);
        helper.assertTrue(connector.connector().resistance() == 2 && menu.resistance() == 2, "the resistance never goes below zero");

        stranger.openMenu(connector.menuProvider());
        ChannelMenu theirs = (ChannelMenu) stranger.containerMenu;
        helper.assertTrue(slotOf(theirs, shorter) < 0, "nobody else sees a private channel");
        helper.assertTrue(theirs.getSlot(ChannelMenu.CURRENT_SLOT).getItem().getHoverName().getString().equals(shorter),
                "but they see what the pipe is on");
        menu.clickMenuButton(owner, ChannelMenu.BUTTON_ACCESS);
        theirs.broadcastChanges();
        int listed = slotOf(theirs, shorter);
        helper.assertTrue(named(helper, shorter).access() == ChannelAccess.PUBLIC && listed >= 0, "a public one is for everyone");
        theirs.clicked(ChannelMenu.FIRST_CHANNEL_SLOT + listed, 0, ClickType.PICKUP, stranger);
        helper.assertTrue(theirs.selectedSlot() == listed && !theirs.ownsSelected() && theirs.getCarried().isEmpty(),
                "a click selects and picks nothing up");
        theirs.setText(ChannelMenu.TEXT_RENAME, "stolen");
        theirs.clickMenuButton(stranger, ChannelMenu.BUTTON_DELETE);
        helper.assertTrue(channels(helper).find(made.id()).filter(channel -> channel.name().equals(shorter)).isPresent(),
                "only the owner changes or deletes a channel");

        String renamed = "new " + UUID.randomUUID().toString().substring(0, 8);
        menu.setText(ChannelMenu.TEXT_RENAME, renamed);
        helper.assertTrue(slotOf(menu, renamed) == menu.selectedSlot() && menu.selectedSlot() >= 0, "renamed");
        menu.clickMenuButton(owner, ChannelMenu.BUTTON_DELETE);
        helper.assertTrue(channels(helper).find(made.id()).isEmpty() && menu.selectedSlot() < 0, "deleted");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void aStationSecuresChannelsForThePlayersItLets(GameTestHelper helper) {
        BlockPos pos = new BlockPos(0, 1, 3);
        helper.setBlock(pos, Content.SECURITY_STATION.get());
        SecurityStationBlockEntity station = helper.getBlockEntity(pos);
        TestPlayer owner = TestPlayer.in(helper);
        TestPlayer friend = TestPlayer.in(helper);
        TestPlayer stranger = TestPlayer.in(helper);
        station.station().settings().setOwner(owner.getUUID());
        station.station().settings().set(friend.getUUID(), Permission.ACCESS_ROUTING_CHANNELS, true);
        String name = "safe " + UUID.randomUUID().toString().substring(0, 8);

        BlockPos absolute = helper.absolutePos(pos);
        helper.getBlockState(pos).useWithoutItem(helper.getLevel(), owner,
                new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false));
        owner.containerMenu.clickMenuButton(owner, SecurityStationMenu.BUTTON_CHANNELS);
        ChannelMenu menu = (ChannelMenu) owner.containerMenu;
        helper.assertTrue(!menu.onPipe() && !menu.getSlot(ChannelMenu.FIRST_WAITING_SLOT).isActive(), "the station only lists channels");
        menu.clickMenuButton(owner, ChannelMenu.BUTTON_NEW_ACCESS);
        helper.assertTrue(menu.newAccess() == ChannelAccess.SECURED, "at a station channels can be secured");
        menu.setText(ChannelMenu.TEXT_ADD, name);
        helper.assertTrue(named(helper, name).station().equals(station.id()), "with that station");

        PipeBlockEntity connector = pipe(helper, PipeKind.INVENTORY_SYSTEM_CONNECTOR, 1);
        friend.openMenu(connector.menuProvider());
        stranger.openMenu(connector.menuProvider());
        helper.assertTrue(slotOf((ChannelMenu) friend.containerMenu, name) >= 0, "whoever the station lets sees the channel");
        helper.assertTrue(slotOf((ChannelMenu) stranger.containerMenu, name) < 0, "nobody else does");
        channels(helper).delete(named(helper, name).id(), owner.getUUID());
        helper.succeed();
    }
}
