package routedpipes.adapter.neoforge.gametest;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import routedpipes.adapter.minecraft.PipeBlockEntity;
import routedpipes.adapter.minecraft.PowerJunctionBlockEntity;
import routedpipes.adapter.minecraft.RemoteOrdererItem;
import routedpipes.adapter.minecraft.SecurityCardItem;
import routedpipes.adapter.minecraft.SecurityStationBlockEntity;
import routedpipes.adapter.minecraft.SecurityStations;
import routedpipes.adapter.minecraft.SettingsCopierItem;
import routedpipes.adapter.minecraft.menu.PipeConfigMenu;
import routedpipes.adapter.minecraft.menu.RequestMenu;
import routedpipes.adapter.minecraft.menu.SecurityStationMenu;
import routedpipes.adapter.minecraft.menu.UpgradeMenu;
import routedpipes.adapter.neoforge.LogisticsPipes;
import routedpipes.domain.logistics.PipeKind;
import routedpipes.domain.logistics.UpgradeKind;
import routedpipes.domain.security.Permission;
import routedpipes.domain.security.SecuritySettings;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ExplosionEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(LogisticsPipes.MODID)
@PrefixGameTestTemplate(false)
public class SecurityGameTest {

    private static final String EMPTY = "empty";
    private static final int SETTLE = 5;
    // long enough for a card to fall out of its pipe
    private static final int CARD_GONE = 30;
    // north of it is the first junction of GameTestPower
    private static final BlockPos STATION = new BlockPos(0, 1, 3);

    private static PipeBlockEntity pipe(GameTestHelper helper, PipeKind kind, int x) {
        BlockPos pos = new BlockPos(x, 1, 1);
        helper.setBlock(pos, LogisticsPipes.pipe(kind));
        return helper.getBlockEntity(pos);
    }

    private static BlockHitResult hit(GameTestHelper helper, BlockPos relative) {
        BlockPos pos = helper.absolutePos(relative);
        return new BlockHitResult(Vec3.atCenterOf(pos), Direction.UP, pos, false);
    }

    private static void use(GameTestHelper helper, Player player, BlockPos relative) {
        ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (held.isEmpty() || helper.getBlockState(relative).useItemOn(held, helper.getLevel(), player, InteractionHand.MAIN_HAND,
                hit(helper, relative)) == ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION) {
            helper.getBlockState(relative).useWithoutItem(helper.getLevel(), player, hit(helper, relative));
        }
    }

    // opens nothing for the player first, so that what the click opens can be told apart
    private static boolean opens(GameTestHelper helper, Player player, int x, Class<?> menu) {
        player.containerMenu = player.inventoryMenu;
        use(helper, player, new BlockPos(x, 1, 1));
        return menu.isInstance(player.containerMenu);
    }

    // what the player gets when using a pipe controller on the pipe at x
    private static UpgradeMenu controller(GameTestHelper helper, Player player, int x) {
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(LogisticsPipes.PIPE_CONTROLLER.get()));
        use(helper, player, new BlockPos(x, 1, 1));
        return (UpgradeMenu) player.containerMenu;
    }

    // a station at the given place that belongs to the owner
    private static SecurityStationBlockEntity station(GameTestHelper helper, BlockPos pos, Player owner) {
        helper.setBlock(pos, LogisticsPipes.SECURITY_STATION.get());
        SecurityStationBlockEntity station = helper.getBlockEntity(pos);
        if (owner != null) {
            helper.getBlockState(pos).getBlock().setPlacedBy(helper.getLevel(), helper.absolutePos(pos), helper.getBlockState(pos),
                    owner, ItemStack.EMPTY);
        }
        return station;
    }

    private static ItemStack card(SecurityStationBlockEntity station) {
        return LogisticsPipes.SECURITY_CARD.get().cardFor(station.id());
    }

    private static void lock(PipeBlockEntity pipe, SecurityStationBlockEntity station) {
        pipe.security().card().setItem(0, card(station));
    }

    @GameTest(template = EMPTY)
    public static void stationBelongsToWhoPlacesItAndOpensForNobodyElse(GameTestHelper helper) {
        TestPlayer owner = TestPlayer.in(helper);
        TestPlayer stranger = TestPlayer.in(helper);
        SecurityStationBlockEntity station = station(helper, STATION, owner);

        helper.assertTrue(station.station().settings().isOwner(owner.getUUID()), "whoever places the station owns it");
        use(helper, stranger, STATION);
        helper.assertTrue(!(stranger.containerMenu instanceof SecurityStationMenu), "a stranger does not get at the station");
        use(helper, owner, STATION);
        helper.assertTrue(owner.containerMenu instanceof SecurityStationMenu, "the owner does");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void stationNobodyPlacedBelongsToTheFirstWhoUsesIt(GameTestHelper helper) {
        TestPlayer first = TestPlayer.in(helper);
        TestPlayer second = TestPlayer.in(helper);
        SecurityStationBlockEntity station = station(helper, STATION, null);

        use(helper, first, STATION);
        use(helper, second, STATION);

        helper.assertTrue(station.station().settings().isOwner(first.getUUID()), "the first one to use it owns it");
        helper.assertTrue(first.containerMenu instanceof SecurityStationMenu && !(second.containerMenu instanceof SecurityStationMenu),
                "and is the only one who gets in");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void stationMakesCardsAsLongAsItHasEnergy(GameTestHelper helper) {
        TestPlayer owner = TestPlayer.in(helper);
        // enough for one card and a half
        PowerJunctionBlockEntity junction = GameTestPower.junction(helper, STATION.north(), SecurityStationBlockEntity.CARD_ENERGY * 3 / 2);
        SecurityStationBlockEntity station = station(helper, STATION, owner);
        use(helper, owner, STATION);
        SecurityStationMenu menu = (SecurityStationMenu) owner.containerMenu;

        menu.clickMenuButton(owner, SecurityStationMenu.BUTTON_ADD_CARD);
        ItemStack made = menu.getSlot(SecurityStationMenu.CARD_SLOT).getItem();
        helper.assertTrue(made.getCount() == 1 && SecurityCardItem.stationOf(made).filter(station.id()::equals).isPresent(),
                "one card of this station");
        helper.assertTrue(junction.stored() == SecurityStationBlockEntity.CARD_ENERGY / 2, "the card was paid for");
        helper.assertTrue(!menu.getSlot(SecurityStationMenu.CARD_SLOT).mayPlace(made), "nothing can be put into the card slot");

        menu.clickMenuButton(owner, SecurityStationMenu.BUTTON_ADD_CARD);
        helper.assertTrue(station.cards().getItem(0).getCount() == 1, "no second card without energy");
        menu.clickMenuButton(owner, SecurityStationMenu.BUTTON_REMOVE_CARD);
        helper.assertTrue(station.cards().isEmpty(), "the minus button takes a card away");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void ownerSetsUpPlayersInTheStationScreen(GameTestHelper helper) {
        TestPlayer owner = TestPlayer.in(helper);
        UUID friend = UUID.randomUUID();
        SecurityStationBlockEntity station = station(helper, STATION, owner);
        SecuritySettings settings = station.station().settings();
        use(helper, owner, STATION);
        SecurityStationMenu menu = (SecurityStationMenu) owner.containerMenu;
        int firstPlayer = SecurityStationMenu.FIRST_PLAYER_SLOT;

        helper.assertTrue(menu.stationId().equals(station.id()), "the screen knows the id of the station");
        helper.assertTrue(menu.getSlot(SecurityStationMenu.CARD_SLOT).isActive() && !menu.getSlot(firstPlayer).isActive(),
                "the station tab comes first");
        menu.clickMenuButton(owner, SecurityStationMenu.Tab.PLAYERS.ordinal());
        helper.assertTrue(!menu.getSlot(SecurityStationMenu.CARD_SLOT).isActive() && menu.getSlot(firstPlayer).isActive(),
                "the players tab shows the players and no cards");

        menu.clickMenuButton(owner, SecurityStationMenu.BUTTON_PERMISSION + Permission.REMOVE_PIPES.ordinal());
        helper.assertTrue(settings.players().isEmpty(), "without a selected player the buttons do nothing");

        menu.addPlayer(friend, "Friend");
        menu.addPlayer(UUID.randomUUID(), "Other");
        helper.assertTrue(menu.getSlot(firstPlayer).getItem().getHoverName().getString().equals("Friend"), "listed with their name");
        helper.assertTrue(menu.selectedSlot() == 1, "a new player is selected");
        menu.clicked(firstPlayer, 0, ClickType.PICKUP, owner);
        helper.assertTrue(menu.selectedSlot() == 0 && owner.containerMenu.getCarried().isEmpty(), "a click selects and picks nothing up");

        menu.clickMenuButton(owner, SecurityStationMenu.BUTTON_PERMISSION + Permission.REMOVE_PIPES.ordinal());
        helper.assertTrue(settings.permissions(friend).equals(Set.of(Permission.REMOVE_PIPES)), "one permission switched on");
        helper.assertTrue(menu.allowed(Permission.REMOVE_PIPES) && !menu.allowed(Permission.CHECK_NETWORK), "and shown");
        menu.clickMenuButton(owner, SecurityStationMenu.BUTTON_PERMISSION + Permission.REMOVE_PIPES.ordinal());
        helper.assertTrue(settings.permissions(friend).isEmpty(), "and off again");

        menu.clickMenuButton(owner, SecurityStationMenu.BUTTON_AUTHORIZED);
        menu.clickMenuButton(owner, SecurityStationMenu.BUTTON_AUTOMATED_REMOVAL);
        helper.assertTrue(!settings.authorized() && !menu.authorized(), "cards can be declared void");
        helper.assertTrue(settings.automatedRemoval() && menu.automatedRemoval(), "automated removal can be allowed");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void stationSettingsSurviveSaveAndLoad(GameTestHelper helper) {
        UUID id = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        UUID friend = UUID.randomUUID();
        SecurityStations stations = new SecurityStations();
        SecurityStations.Station station = stations.station(id);
        station.settings().setOwner(owner);
        station.settings().setAuthorized(false);
        station.settings().setAutomatedRemoval(true);
        station.add(friend, "Friend");
        station.settings().set(friend, Permission.ACTIVE_REQUESTING, true);

        SecurityStations.Station loaded = SecurityStations.load(stations.save(new CompoundTag(), helper.getLevel().registryAccess()),
                helper.getLevel().registryAccess()).find(id).orElseThrow();

        SecuritySettings settings = loaded.settings();
        helper.assertTrue(settings.isOwner(owner) && !settings.authorized() && settings.automatedRemoval(), "the settings of the station");
        helper.assertTrue(settings.players().equals(List.of(friend)) && loaded.name(friend).equals("Friend")
                && settings.permissions(friend).equals(Set.of(Permission.ACTIVE_REQUESTING)), "the players and what they may do");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void cardLocksThePipeItIsUsedOnAndEverythingConnected(GameTestHelper helper) {
        TestPlayer owner = TestPlayer.in(helper);
        SecurityStationBlockEntity station = station(helper, STATION, owner);
        PipeBlockEntity first = pipe(helper, PipeKind.BASIC, 0);
        helper.setBlock(new BlockPos(1, 1, 1), LogisticsPipes.TRANSPORT_PIPE.get());
        PipeBlockEntity second = pipe(helper, PipeKind.BASIC, 2);
        PipeBlockEntity third = pipe(helper, PipeKind.BASIC, 3);

        ItemStack stray = LogisticsPipes.SECURITY_CARD.get().cardFor(UUID.randomUUID());
        owner.setItemInHand(InteractionHand.MAIN_HAND, stray);
        use(helper, owner, new BlockPos(0, 1, 1));
        helper.assertTrue(first.security().station().isEmpty() && stray.getCount() == 1, "a card of no station does not go in");

        ItemStack cards = card(station);
        cards.setCount(2);
        owner.setItemInHand(InteractionHand.MAIN_HAND, cards);
        use(helper, owner, new BlockPos(0, 1, 1));
        use(helper, owner, new BlockPos(0, 1, 1));
        helper.assertTrue(first.security().station().filter(station.id()::equals).isPresent() && cards.getCount() == 1,
                "one card goes in and locks the pipe");

        UpgradeMenu controller = controller(helper, owner, 0);
        helper.assertTrue(!controller.getSlot(UpgradeMenu.SECURITY_SLOT).isActive(), "the card slot has its own tab");
        controller.clickMenuButton(owner, UpgradeMenu.Tab.SECURITY.ordinal());
        helper.assertTrue(controller.getSlot(UpgradeMenu.SECURITY_SLOT).isActive() && controller.cardAuthorized(), "where the card shows");
        helper.assertTrue(!controller.getSlot(UpgradeMenu.SECURITY_SLOT).mayPickup(owner), "the card never comes out again");

        helper.runAfterDelay(SETTLE, () -> {
            helper.assertTrue(second.security().station().filter(station.id()::equals).isPresent(), "the next routed pipe got a card");
            helper.assertTrue(third.security().station().filter(station.id()::equals).isPresent(), "and the one after it");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void controllerTakesOnlyCardsTheirStationVouchesFor(GameTestHelper helper) {
        TestPlayer owner = TestPlayer.in(helper);
        SecurityStationBlockEntity station = station(helper, STATION, owner);
        PipeBlockEntity pipe = pipe(helper, PipeKind.BASIC, 0);
        UpgradeMenu controller = controller(helper, owner, 0);
        controller.clickMenuButton(owner, UpgradeMenu.Tab.SECURITY.ordinal());
        // the second slot of the hotbar
        int inventorySlot = PipeBlockEntity.UPGRADE_SLOTS + 28;

        station.station().settings().setAuthorized(false);
        owner.getInventory().setItem(1, card(station));
        controller.quickMoveStack(owner, inventorySlot);
        helper.assertTrue(pipe.security().station().isEmpty(), "a card that was declared void stays out");

        station.station().settings().setAuthorized(true);
        ItemStack cards = card(station);
        cards.setCount(3);
        owner.getInventory().setItem(1, cards);
        controller.quickMoveStack(owner, inventorySlot);
        helper.assertTrue(pipe.security().card().getItem(0).getCount() == 1 && owner.getInventory().getItem(1).getCount() == 2,
                "one valid card goes in with a shift click");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void lockedPipesOnlyOpenForThoseTheStationLets(GameTestHelper helper) {
        GameTestPower.supply(helper);
        TestPlayer owner = TestPlayer.in(helper);
        TestPlayer friend = TestPlayer.in(helper);
        SecurityStationBlockEntity station = station(helper, STATION, owner);
        SecuritySettings settings = station.station().settings();
        PipeBlockEntity basic = pipe(helper, PipeKind.BASIC, 0);
        PipeBlockEntity request = pipe(helper, PipeKind.REQUEST, 1);
        lock(basic, station);
        lock(request, station);
        ItemStack controller = new ItemStack(LogisticsPipes.PIPE_CONTROLLER.get());
        ItemStack upgrades = new ItemStack(LogisticsPipes.UPGRADES.get(UpgradeKind.SPEED).get(), 4);
        ItemStack orderer = new ItemStack(LogisticsPipes.REMOTE_ORDERER.get());
        ItemStack copier = new ItemStack(LogisticsPipes.SETTINGS_COPIER.get());
        BlockPos requestPos = helper.absolutePos(new BlockPos(1, 1, 1));
        UseOnContext link = new UseOnContext(helper.getLevel(), friend, InteractionHand.MAIN_HAND, orderer,
                new BlockHitResult(Vec3.atCenterOf(requestPos), Direction.UP, requestPos, false));
        BlockPos basicPos = helper.absolutePos(new BlockPos(0, 1, 1));
        UseOnContext copy = new UseOnContext(helper.getLevel(), friend, InteractionHand.MAIN_HAND, copier,
                new BlockHitResult(Vec3.atCenterOf(basicPos), Direction.UP, basicPos, false));

        helper.runAfterDelay(SETTLE, () -> {
            friend.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            helper.assertTrue(!opens(helper, friend, 0, PipeConfigMenu.class), "no settings without permission");
            helper.assertTrue(!opens(helper, friend, 1, RequestMenu.class), "no requesting without permission");
            friend.setShiftKeyDown(true);
            copier.useOn(copy);
            friend.setShiftKeyDown(false);
            helper.assertTrue(!SettingsCopierItem.hasSettings(copier), "no copying of settings without permission");
            orderer.useOn(link);
            helper.assertTrue(!RemoteOrdererItem.isLinked(orderer), "no linking of an orderer without permission");
            friend.setItemInHand(InteractionHand.MAIN_HAND, controller);
            helper.assertTrue(!opens(helper, friend, 0, UpgradeMenu.class), "no controller without permission");
            friend.setItemInHand(InteractionHand.MAIN_HAND, upgrades);
            use(helper, friend, new BlockPos(0, 1, 1));
            helper.assertTrue(upgrades.getCount() == 4 && basic.upgrades().isEmpty(), "no upgrading without permission");

            settings.set(friend.getUUID(), Permission.CONFIGURE_SETTINGS, true);
            friend.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            helper.assertTrue(opens(helper, friend, 0, PipeConfigMenu.class), "settings with permission");
            helper.assertTrue(!opens(helper, friend, 1, RequestMenu.class), "which does not cover requesting");
            friend.setShiftKeyDown(true);
            copier.useOn(copy);
            friend.setShiftKeyDown(false);
            helper.assertTrue(SettingsCopierItem.hasSettings(copier), "copying settings with permission");

            settings.set(friend.getUUID(), Permission.ACTIVE_REQUESTING, true);
            helper.assertTrue(opens(helper, friend, 1, RequestMenu.class), "requesting with permission");
            orderer.useOn(link);
            helper.assertTrue(RemoteOrdererItem.isLinked(orderer), "and linking an orderer");
            friend.setItemInHand(InteractionHand.MAIN_HAND, orderer);
            friend.containerMenu = friend.inventoryMenu;
            orderer.use(helper.getLevel(), friend, InteractionHand.MAIN_HAND);
            helper.assertTrue(friend.containerMenu instanceof RequestMenu, "and ordering from afar");
            settings.set(friend.getUUID(), Permission.ACTIVE_REQUESTING, false);
            friend.containerMenu = friend.inventoryMenu;
            orderer.use(helper.getLevel(), friend, InteractionHand.MAIN_HAND);
            helper.assertTrue(!(friend.containerMenu instanceof RequestMenu), "a linked orderer stops working with the permission gone");

            settings.set(friend.getUUID(), Permission.CHECK_NETWORK, true);
            friend.setItemInHand(InteractionHand.MAIN_HAND, controller);
            helper.assertTrue(opens(helper, friend, 0, UpgradeMenu.class), "the controller with permission");
            UpgradeMenu menu = (UpgradeMenu) friend.containerMenu;
            helper.assertTrue(!menu.mayUpgrade() && !menu.getSlot(0).mayPlace(upgrades), "which does not cover the upgrade slots");

            settings.set(friend.getUUID(), Permission.UPGRADE_PIPES, true);
            helper.assertTrue(opens(helper, friend, 0, UpgradeMenu.class) && ((UpgradeMenu) friend.containerMenu).getSlot(0).mayPlace(upgrades),
                    "upgrade slots with permission");
            friend.setItemInHand(InteractionHand.MAIN_HAND, upgrades);
            use(helper, friend, new BlockPos(0, 1, 1));
            helper.assertTrue(upgrades.getCount() == 3, "and upgrading by hand");

            owner.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
            helper.assertTrue(opens(helper, owner, 0, PipeConfigMenu.class) && opens(helper, owner, 1, RequestMenu.class),
                    "the owner needs no permission");
            owner.setItemInHand(InteractionHand.MAIN_HAND, controller);
            helper.assertTrue(opens(helper, owner, 0, UpgradeMenu.class) && ((UpgradeMenu) owner.containerMenu).mayUpgrade(),
                    "for anything");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void checkingAPlayerCostsEnergyAndWithoutItOnlyTheOwnerGetsIn(GameTestHelper helper) {
        TestPlayer owner = TestPlayer.in(helper);
        TestPlayer friend = TestPlayer.in(helper);
        SecurityStationBlockEntity station = station(helper, STATION, owner);
        station.station().settings().set(friend.getUUID(), Permission.CONFIGURE_SETTINGS, true);
        // enough to look one player up
        PowerJunctionBlockEntity junction = GameTestPower.junction(helper, new BlockPos(0, 1, 2), SecuritySettings.CHECK_ENERGY);
        lock(pipe(helper, PipeKind.BASIC, 0), station);

        helper.runAfterDelay(SETTLE, () -> {
            helper.assertTrue(opens(helper, owner, 0, PipeConfigMenu.class) && junction.stored() == SecuritySettings.CHECK_ENERGY,
                    "the owner gets in for free");
            helper.assertTrue(opens(helper, friend, 0, PipeConfigMenu.class) && junction.stored() == 0, "anyone else is paid for");
            helper.assertTrue(!opens(helper, friend, 0, PipeConfigMenu.class), "and stays out when there is no energy");
            helper.assertTrue(opens(helper, owner, 0, PipeConfigMenu.class), "which does not bother the owner");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY, timeoutTicks = 200)
    public static void cardsFallOutWhenTheirStationIsGoneOrDeclaresThemVoid(GameTestHelper helper) {
        TestPlayer owner = TestPlayer.in(helper);
        SecurityStationBlockEntity voiding = station(helper, STATION, owner);
        SecurityStationBlockEntity breaking = station(helper, STATION.east(3), owner);
        PipeBlockEntity first = pipe(helper, PipeKind.BASIC, 0);
        PipeBlockEntity second = pipe(helper, PipeKind.BASIC, 3);
        lock(first, voiding);
        lock(second, breaking);

        helper.runAfterDelay(CARD_GONE, () -> {
            helper.assertTrue(first.security().station().isPresent() && second.security().station().isPresent(),
                    "cards stay while their station vouches for them");
            voiding.station().settings().setAuthorized(false);
            helper.setBlock(STATION.east(3), Blocks.AIR);
        });
        helper.runAfterDelay(2 * CARD_GONE, () -> {
            helper.assertTrue(first.security().station().isEmpty(), "the card of the station that declared it void is gone");
            helper.assertTrue(second.security().station().isEmpty(), "the card of the broken station is gone");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void pipesLockedToDifferentStationsDoNotWorkTogether(GameTestHelper helper) {
        GameTestPower.supply(helper);
        TestPlayer owner = TestPlayer.in(helper);
        SecurityStationBlockEntity one = station(helper, STATION, owner);
        SecurityStationBlockEntity other = station(helper, STATION.east(3), owner);
        PipeBlockEntity request = pipe(helper, PipeKind.REQUEST, 0);
        PipeBlockEntity provider = pipe(helper, PipeKind.PROVIDER, 1);
        PipeBlockEntity basic = pipe(helper, PipeKind.BASIC, 2);
        lock(request, one);
        lock(provider, other);

        helper.runAfterDelay(SETTLE, () -> {
            helper.assertTrue(request.networkStatus().orElseThrow().routers() == 0, "the request pipe is on its own");
            helper.assertTrue(basic.security().station().filter(other.id()::equals).isPresent(), "the free pipe was locked by its neighbour");
            helper.assertTrue(provider.networkStatus().orElseThrow().routers() == 1, "and the two work together");
            helper.succeed();
        });
    }

    private static boolean breakingStopped(GameTestHelper helper, Player player, int x) {
        BlockPos pos = helper.absolutePos(new BlockPos(x, 1, 1));
        return NeoForge.EVENT_BUS.post(new PlayerInteractEvent.LeftClickBlock(player, pos, Direction.UP,
                PlayerInteractEvent.LeftClickBlock.Action.START)).isCanceled();
    }

    @GameTest(template = EMPTY)
    public static void lockedPipesCanOnlyBeBrokenWithPermission(GameTestHelper helper) {
        GameTestPower.supply(helper);
        TestPlayer owner = TestPlayer.in(helper);
        TestPlayer friend = TestPlayer.in(helper);
        SecurityStationBlockEntity station = station(helper, STATION, owner);
        lock(pipe(helper, PipeKind.BASIC, 0), station);
        // far enough away not to get a card from the locked pipe
        pipe(helper, PipeKind.BASIC, 3);

        helper.runAfterDelay(SETTLE, () -> {
            helper.assertTrue(breakingStopped(helper, friend, 0), "a locked pipe cannot be broken without permission");
            helper.assertTrue(!breakingStopped(helper, friend, 3), "a pipe without a card can");
            helper.assertTrue(!breakingStopped(helper, owner, 0), "the owner breaks what they like");
            station.station().settings().set(friend.getUUID(), Permission.REMOVE_PIPES, true);
            helper.assertTrue(!breakingStopped(helper, friend, 0), "and so does a player with permission");
            helper.succeed();
        });
    }

    // FINDING: mining tools of other mods break extra blocks for a real player through the break event, which only stops fake players
    @GameTest(template = EMPTY)
    public static void aRealPlayerWithoutPermissionCannotBreakALockedPipeThroughTheBreakEvent(GameTestHelper helper) {
        GameTestPower.supply(helper);
        TestPlayer owner = TestPlayer.in(helper);
        TestPlayer stranger = TestPlayer.in(helper);
        SecurityStationBlockEntity station = station(helper, STATION, owner);
        lock(pipe(helper, PipeKind.BASIC, 0), station);
        BlockPos locked = helper.absolutePos(new BlockPos(0, 1, 1));

        helper.runAfterDelay(SETTLE, () -> {
            helper.assertTrue(breakingStopped(helper, stranger, 0), "hitting the locked pipe is stopped");
            // what a hammer or vein miner does for the blocks around the one that was hit
            boolean stopped = NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(helper.getLevel(), locked,
                    helper.getLevel().getBlockState(locked), stranger)).isCanceled();
            helper.assertTrue(stopped, "breaking the locked pipe for a player without permission is stopped too");
            helper.succeed();
        });
    }

    private static boolean survivesExplosion(GameTestHelper helper, BlockPos pos) {
        Explosion explosion = new Explosion(helper.getLevel(), null, pos.getX(), pos.getY(), pos.getZ(), 2, false,
                Explosion.BlockInteraction.DESTROY, List.of(pos));
        NeoForge.EVENT_BUS.post(new ExplosionEvent.Detonate(helper.getLevel(), explosion, new ArrayList<>()));
        return !explosion.getToBlow().contains(pos);
    }

    private static boolean survivesMachine(GameTestHelper helper, BlockPos pos) {
        return NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(helper.getLevel(), pos, helper.getLevel().getBlockState(pos),
                FakePlayerFactory.getMinecraft(helper.getLevel()))).isCanceled();
    }

    @GameTest(template = EMPTY)
    public static void explosionsAndMachinesLeaveLockedPipesUnlessTheStationAllowsIt(GameTestHelper helper) {
        GameTestPower.supply(helper);
        TestPlayer owner = TestPlayer.in(helper);
        SecurityStationBlockEntity station = station(helper, STATION, owner);
        lock(pipe(helper, PipeKind.BASIC, 0), station);
        pipe(helper, PipeKind.BASIC, 3);
        BlockPos locked = helper.absolutePos(new BlockPos(0, 1, 1));
        BlockPos free = helper.absolutePos(new BlockPos(3, 1, 1));

        helper.runAfterDelay(SETTLE, () -> {
            helper.assertTrue(survivesExplosion(helper, locked), "an explosion does not take a locked pipe");
            helper.assertTrue(survivesMachine(helper, locked), "and neither does a machine");
            helper.assertTrue(!survivesExplosion(helper, free) && !survivesMachine(helper, free), "a pipe without a card is fair game");
            station.station().settings().setAutomatedRemoval(true);
            helper.assertTrue(!survivesExplosion(helper, locked) && !survivesMachine(helper, locked),
                    "and so is a locked one when the station allows it");
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void cardOfAPipeSurvivesSaveAndLoad(GameTestHelper helper) {
        TestPlayer owner = TestPlayer.in(helper);
        SecurityStationBlockEntity station = station(helper, STATION, owner);
        PipeBlockEntity original = pipe(helper, PipeKind.BASIC, 0);
        lock(original, station);
        CompoundTag saved = original.saveWithoutMetadata(helper.getLevel().registryAccess());

        PipeBlockEntity loaded = pipe(helper, PipeKind.BASIC, 3);
        loaded.loadWithComponents(saved, helper.getLevel().registryAccess());

        helper.assertTrue(loaded.security().station().filter(station.id()::equals).isPresent(), "the card is saved with the pipe");
        helper.succeed();
    }
}
