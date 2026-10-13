package routedpipes.adapter.neoforge.gametest;

import java.util.UUID;

import routedpipes.adapter.minecraft.ChannelRegistry;
import routedpipes.adapter.minecraft.ConnectorPipeBlock;
import routedpipes.adapter.minecraft.ConnectorPipeBlock.LinkState;
import routedpipes.adapter.minecraft.HudSources;
import routedpipes.adapter.minecraft.McItemType;
import routedpipes.adapter.minecraft.PipeBlock;
import routedpipes.adapter.minecraft.PipeBlockEntity;
import routedpipes.adapter.minecraft.PowerJunctionBlockEntity;
import routedpipes.adapter.minecraft.menu.ChannelMenu;
import routedpipes.adapter.neoforge.RoutedPipes;
import routedpipes.application.RequestOutcome;
import routedpipes.domain.channel.ChannelAccess;
import routedpipes.domain.hud.HudCategory;
import routedpipes.domain.hud.HudSource;
import routedpipes.domain.logistics.PipeKind;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// two networks far apart: a provider and a teleport pipe at one end of the row, a teleport pipe and a request pipe at the other
// nothing but air between them and no inventory at the teleport pipes
@GameTestHolder(RoutedPipes.MODID)
@PrefixGameTestTemplate(false)
public class TeleportPipeGameTest {

    private static final String EMPTY = "empty";
    private static final String LONG = "empty_long";
    private static final int SETTLE = 5;
    private static final int NEAR = 1;
    private static final int FAR = 9;

    private static PipeBlockEntity pipe(GameTestHelper helper, PipeKind kind, int x) {
        BlockPos pos = new BlockPos(x, 1, 1);
        helper.setBlock(pos, RoutedPipes.pipe(kind));
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

    // a channel nobody else sees, the tests of a run share one world
    private static UUID channel(GameTestHelper helper) {
        return ChannelRegistry.of(helper.getLevel()).channels()
                .create("test", UUID.randomUUID(), "Tester", ChannelAccess.PRIVATE, null).orElseThrow().id();
    }

    private static LinkState shown(GameTestHelper helper, int x) {
        return helper.getBlockState(new BlockPos(x, 1, 1)).getValue(ConnectorPipeBlock.LINK);
    }

    private static int routers(PipeBlockEntity pipe) {
        return pipe.networkStatus().orElseThrow().routers();
    }

    // the provider with its chest and the first teleport pipe, the junctions of GameTestPower are beside them
    private static PipeBlockEntity providerSide(GameTestHelper helper) {
        pipe(helper, PipeKind.PROVIDER, 0);
        chest(helper, new BlockPos(0, 2, 1), new ItemStack(Items.COBBLESTONE, 64));
        return pipe(helper, PipeKind.TELEPORT, NEAR);
    }

    @GameTest(template = LONG, timeoutTicks = 300)
    public static void aProviderServesARequestOnTheOtherSideOfTwoTeleportPipes(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity near = providerSide(helper);
        PipeBlockEntity far = pipe(helper, PipeKind.TELEPORT, FAR);
        PipeBlockEntity request = pipe(helper, PipeKind.REQUEST, FAR + 1);
        BlockPos target = chest(helper, new BlockPos(FAR + 1, 2, 1));
        // a junction of its own on the request side, which is the first one asked
        PowerJunctionBlockEntity junction = GameTestPower.junction(helper, new BlockPos(FAR + 1, 1, 2), PowerJunctionBlockEntity.CAPACITY);
        UUID channel = channel(helper);
        near.connector().setChannel(channel);
        far.connector().setChannel(channel);
        helper.onEachTick(() -> helper.assertItemEntityNotPresent(Items.COBBLESTONE, new BlockPos(5, 1, 1), 12));

        helper.runAfterDelay(SETTLE, () -> {
            helper.assertTrue(shown(helper, NEAR) == LinkState.CONNECTED && shown(helper, FAR) == LinkState.CONNECTED,
                    "both teleport pipes show that they found each other, got " + shown(helper, NEAR) + " and " + shown(helper, FAR));
            helper.assertTrue(request.available().getOrDefault(McItemType.batchOf(new ItemStack(Items.COBBLESTONE)).type(), 0) == 64,
                    "the request pipe sees what the provider of the other network has, it sees " + request.available());
            RequestOutcome outcome = request.request(McItemType.batchOf(new ItemStack(Items.COBBLESTONE, 10)));
            helper.assertTrue(outcome.started(), "and can order it, missing " + outcome.missing() + ", powered " + outcome.powered());
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(count(helper, target, Items.COBBLESTONE) == 10, "the order arrives on the other side, the chest holds "
                    + count(helper, target, Items.COBBLESTONE));
            helper.assertTrue(near.connector().expected().isEmpty() && far.connector().expected().isEmpty(), "nothing waits anywhere");
            helper.assertTrue(junction.stored() == PowerJunctionBlockEntity.CAPACITY - 10 * 2,
                    "the hand over is free, only the order was paid for, used " + (PowerJunctionBlockEntity.CAPACITY - junction.stored()));
        });
    }

    // the request side has no junction at all, the only one is beside the provider on the other side of the pair
    @GameTest(template = LONG, timeoutTicks = 300)
    public static void aRequestIsPaidFromTheOnlyJunctionOnTheOtherSideOfTheTeleportPipes(GameTestHelper helper) {
        PowerJunctionBlockEntity junction = GameTestPower.junction(helper, new BlockPos(0, 1, 2), PowerJunctionBlockEntity.CAPACITY);
        PipeBlockEntity near = providerSide(helper);
        PipeBlockEntity far = pipe(helper, PipeKind.TELEPORT, FAR);
        PipeBlockEntity request = pipe(helper, PipeKind.REQUEST, FAR + 1);
        BlockPos target = chest(helper, new BlockPos(FAR + 1, 2, 1));
        UUID channel = channel(helper);

        helper.runAfterDelay(SETTLE, () -> {
            helper.assertTrue(!request.usePower(1) && request.networkStatus().orElseThrow().energy() == 0,
                    "before the link the request side has no power, it sees " + request.networkStatus().orElseThrow().energy());
            near.connector().setChannel(channel);
            far.connector().setChannel(channel);
        });
        helper.runAfterDelay(2 * SETTLE, () -> {
            helper.assertTrue(request.networkStatus().orElseThrow().energy() == PowerJunctionBlockEntity.CAPACITY,
                    "the request side sees the junction of the other side, it sees " + request.networkStatus().orElseThrow().energy());
            RequestOutcome outcome = request.request(McItemType.batchOf(new ItemStack(Items.COBBLESTONE, 10)));
            helper.assertTrue(outcome.started(), "the order starts, missing " + outcome.missing() + ", powered " + outcome.powered());
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(count(helper, target, Items.COBBLESTONE) == 10, "the order arrives, the chest holds "
                    + count(helper, target, Items.COBBLESTONE));
            helper.assertTrue(junction.stored() == PowerJunctionBlockEntity.CAPACITY - 10 * 2,
                    "the far junction paid for the order, used " + (PowerJunctionBlockEntity.CAPACITY - junction.stored()));
            helper.assertTrue(helper.getBlockState(new BlockPos(FAR + 1, 1, 1)).getValue(PipeBlock.POWERED),
                    "and the request pipe looks powered");
        });
    }

    @GameTest(template = LONG, timeoutTicks = 100)
    public static void aTeleportPipeWithSixConnectionsDoesNotLinkUntilASideIsFree(GameTestHelper helper) {
        PipeBlockEntity near = providerSide(helper);
        BlockPos above = new BlockPos(NEAR, 2, 1);
        // the provider is on its west, plain pipes go on the other five sides
        for (BlockPos beside : new BlockPos[] {above, new BlockPos(NEAR, 0, 1), new BlockPos(NEAR, 1, 0), new BlockPos(NEAR, 1, 2),
                new BlockPos(NEAR + 1, 1, 1)}) {
            helper.setBlock(beside, RoutedPipes.TRANSPORT_PIPE.get());
        }
        PipeBlockEntity far = pipe(helper, PipeKind.TELEPORT, FAR);
        UUID channel = channel(helper);
        near.connector().setChannel(channel);
        far.connector().setChannel(channel);

        helper.runAfterDelay(SETTLE, () -> {
            helper.assertTrue(shown(helper, NEAR) == LinkState.MISMATCHED, "no side to link through, it shows " + shown(helper, NEAR));
            helper.assertTrue(routers(near) == 1 && routers(far) == 0, "the networks stay apart, the near one reaches " + routers(near)
                    + " and the far one " + routers(far));
            helper.setBlock(above, Blocks.AIR);
        });
        helper.runAfterDelay(4 * SETTLE, () -> {
            helper.assertTrue(shown(helper, NEAR) == LinkState.CONNECTED && shown(helper, FAR) == LinkState.CONNECTED,
                    "with a free side they link, they show " + shown(helper, NEAR) + " and " + shown(helper, FAR));
            helper.assertTrue(routers(near) == 2 && routers(far) == 2, "the networks are joined, the near one reaches " + routers(near)
                    + " and the far one " + routers(far));
            helper.succeed();
        });
    }

    @GameTest(template = LONG, timeoutTicks = 100)
    public static void aTeleportPipeAndAnInventoryConnectorOnOneChannelDoNotLink(GameTestHelper helper) {
        PipeBlockEntity teleport = providerSide(helper);
        PipeBlockEntity connector = pipe(helper, PipeKind.INVENTORY_SYSTEM_CONNECTOR, FAR);
        chest(helper, new BlockPos(FAR, 2, 1));
        UUID channel = channel(helper);
        teleport.connector().setChannel(channel);
        connector.connector().setChannel(channel);

        helper.runAfterDelay(4 * SETTLE, () -> {
            helper.assertTrue(shown(helper, NEAR) == LinkState.DISCONNECTED && shown(helper, FAR) == LinkState.DISCONNECTED,
                    "neither finds one of its kind, they show " + shown(helper, NEAR) + " and " + shown(helper, FAR));
            helper.assertTrue(routers(teleport) == 1 && routers(connector) == 0, "the networks stay apart, the teleport pipe reaches "
                    + routers(teleport) + " and the connector " + routers(connector));
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void channelAndResistanceSurviveSaveAndLoad(GameTestHelper helper) {
        PipeBlockEntity teleport = pipe(helper, PipeKind.TELEPORT, 1);
        UUID channel = channel(helper);
        teleport.connector().setChannel(channel);
        teleport.connector().setResistance(7);

        PipeBlockEntity loaded = pipe(helper, PipeKind.TELEPORT, 3);
        loaded.loadWithComponents(teleport.saveWithoutMetadata(helper.getLevel().registryAccess()), helper.getLevel().registryAccess());

        helper.assertTrue(loaded.connector().channel().filter(channel::equals).isPresent(), "the channel stays");
        helper.assertTrue(loaded.connector().resistance() == 7, "the resistance stays");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void itsScreenIsTheOneOfAConnector(GameTestHelper helper) {
        PipeBlockEntity teleport = pipe(helper, PipeKind.TELEPORT, 1);
        TestPlayer player = TestPlayer.in(helper);

        player.openMenu(teleport.menuProvider());

        helper.assertTrue(player.containerMenu instanceof ChannelMenu menu && menu.onPipe(), "the channels open on the pipe");
        player.containerMenu.clickMenuButton(player, ChannelMenu.BUTTON_RESISTANCE_UP);
        helper.assertTrue(teleport.connector().resistance() == 1, "and its resistance is set there");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void theGlassesTreatItLikeAConnectorThatWaitsForNothing(GameTestHelper helper) {
        PipeBlockEntity teleport = pipe(helper, PipeKind.TELEPORT, 1);

        helper.assertTrue(HudSources.categoryOf(teleport).filter(HudCategory.CONNECTOR::equals).isPresent(), "it goes with the connectors");
        helper.assertTrue(HudSources.of(teleport).orElseThrow() instanceof HudSource.Connector connector
                && connector.expected().isEmpty(), "and expects nothing, got " + HudSources.of(teleport));
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void itIsCraftedAroundAnInventorySystemConnector(GameTestHelper helper) {
        Item eye = Items.ENDER_EYE;
        Item diamonds = Items.DIAMOND_BLOCK;
        Item obsidian = Items.OBSIDIAN;
        ItemStack crafted = RecipeGameTest.crafted(helper, 3, 3,
                eye, diamonds, eye,
                obsidian, RoutedPipes.pipe(PipeKind.INVENTORY_SYSTEM_CONNECTOR).asItem(), obsidian,
                eye, diamonds, eye);

        helper.assertTrue(crafted.is(RoutedPipes.pipe(PipeKind.TELEPORT).asItem()) && crafted.getCount() == 1, "got " + crafted);
        helper.succeed();
    }
}
