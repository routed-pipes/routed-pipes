package logisticspipes.adapter.minecraft;

import logisticspipes.domain.logistics.UpgradeKind;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import logisticspipes.adapter.minecraft.menu.PipeConfigMenu;
import logisticspipes.adapter.minecraft.menu.SatelliteNames;
import logisticspipes.application.LogisticsNetwork;
import logisticspipes.application.NetworkOverview;
import logisticspipes.application.RequestOutcome;
import logisticspipes.application.RouterStatus;
import logisticspipes.application.port.FluidNode;
import logisticspipes.application.port.PowerNode;
import logisticspipes.application.port.PowerSource;
import logisticspipes.application.port.RouterNode;
import logisticspipes.domain.crafting.Cleanup;
import logisticspipes.domain.crafting.CraftingSetup;
import logisticspipes.domain.crafting.OutputOrders;
import logisticspipes.domain.crafting.Recipe;
import logisticspipes.domain.item.ItemBatch;
import logisticspipes.domain.item.ItemType;
import logisticspipes.domain.logistics.Extraction;
import logisticspipes.domain.logistics.FirewallRule;
import logisticspipes.domain.logistics.InventoryRef;
import logisticspipes.domain.logistics.ModuleKind;
import logisticspipes.domain.logistics.PatternSupply;
import logisticspipes.domain.logistics.PipeKind;
import logisticspipes.domain.logistics.ProviderSettings;
import logisticspipes.domain.logistics.SinkRule;
import logisticspipes.domain.logistics.Supply;
import logisticspipes.domain.logistics.SupplyMode;
import logisticspipes.domain.routing.Link;
import logisticspipes.domain.request.RequestSummary;
import logisticspipes.domain.routing.RouterId;
import logisticspipes.domain.routing.Side;
import logisticspipes.domain.statistics.PipeStatistics;
import logisticspipes.domain.transport.Departure;
import logisticspipes.domain.transport.PipeTransport;
import logisticspipes.domain.transport.TransportMode;
import logisticspipes.domain.transport.TravellingItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class PipeBlockEntity extends BlockEntity implements RouterNode {

    // settings slots: 0-8 are recipe inputs or supplier targets, 9 is the recipe output
    public static final int CONFIG_SLOTS = 26;
    public static final int BYPRODUCT_SLOT = 10;
    public static final int CLEANUP_FIRST = 11;
    public static final int CLEANUP_SLOTS = 12;
    public static final int OUTPUT_SLOT = 9;
    // the fluids of a crafter with fluid crafting upgrades
    public static final int FLUID_FIRST = 23;
    // first slot of the recipe row that is delivered to the satellite
    public static final int SATELLITE_ROW = 6;
    // the largest chassis has this many
    public static final int MODULE_SLOTS = 8;
    public static final int UPGRADE_SLOTS = 9;
    public static final int UPGRADE_STACK = 16;
    public static final int UPGRADES_PER_MODULE = 2;
    // every speed upgrade makes the pipe send items on this much faster, in percent
    private static final int SPEED_UPGRADE_PERCENT = 25;

    private final PipeTransport transport = new PipeTransport();
    private int[] upgradeCounts;
    private final PipeUpgrades upgrades = new PipeUpgrades(this);
    private final PipeModules modules = new PipeModules(this);
    private final PipeSettings settings = new PipeSettings(this);
    private final PipeSurroundings surroundings = new PipeSurroundings(this);
    private final OutputOrders outputOrders = new OutputOrders();
    private final PipeStatistics statistics = new PipeStatistics();
    private final PipeSecurity security = new PipeSecurity(this);
    private final PipeLook look = new PipeLook(this);
    private RouterId routerId = new RouterId(UUID.randomUUID());
    private Boolean upkeepCache;
    // only a firewall pipe has these
    private FirewallSettings firewall;
    // only a request table has these
    private RequestTable table;
    // only pipes that take a disk have this
    private DiskDrive drive;
    // only an inventory system connector has these
    private SystemConnector connector;
    // only system entrance and destination pipes have this
    private FrequencySlot frequency;
    private boolean announced;
    // what the tick asks every time is kept right here and not in the parts it is about
    // ticks until the pipe looks whether its network has power, negative before the first tick
    private int lookIn = -1;
    private boolean lookAgain;
    private boolean signsNeedTicks;
    private boolean suppliesPower;
    private LogisticsNetwork network;
    // worked out from modules and settings when first asked for, dropped when those change
    private List<SinkRule> sinkRulesCache;
    private List<Extraction> extractionsCache;
    private boolean linksDirty;
    // what the server told a client about the opaque upgrade
    private boolean opaqueOnClient;
    // null unless this is a fluid pipe
    private final FluidPipe fluids;
    private final PowerPipe power = new PowerPipe(this);
    // null unless this is a routed pipe
    private final PipeSigns signs;

    public PipeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        fluids = FluidPipe.of(this, kind());
        signs = kind() == null ? null : new PipeSigns(this);
        if (kind() == PipeKind.FIREWALL) {
            firewall = new FirewallSettings(this::setChanged);
        }
        if (kind() == PipeKind.REQUEST_TABLE) {
            table = new RequestTable(this);
        }
        if (kind() == PipeKind.REQUEST_MK2 || kind() == PipeKind.REQUEST_TABLE) {
            drive = new DiskDrive(this);
        }
        if (kind() == PipeKind.INVENTORY_SYSTEM_CONNECTOR) {
            connector = new SystemConnector(this);
        }
        if (kind() == PipeKind.SYSTEM_ENTRANCE || kind() == PipeKind.SYSTEM_DESTINATION) {
            frequency = new FrequencySlot(this::setChanged);
        }
        settings.config().addListener(container -> settingsChanged());
        upgrades.slots().addListener(container -> upgradeCounts = null);
        modules.slots().addListener(container -> settingsChanged());
        upgrades.slots().addListener(container -> {
            upgrades.connectWithoutUpgrade();
            transport.setBoost(boost());
            neighbourChanged();
            setChanged();
            if (level != null && !level.isClientSide) {
                // clients have to know about the opaque upgrade
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            }
        });
        modules.upgrades().addListener(container -> settingsChanged());
        upgrades.slots().addListener(container -> {
            upgrades.forgetFaces();
            // a fuzzy upgrade changes what the pipe takes
            settingsChanged();
        });
        upgrades.sneakyRow().addListener(container -> {
            upgrades.forgetFaces();
            neighbourChanged();
            setChanged();
        });
    }

    // filters, modules or the default route changed: forget what was derived from them and tell the network
    void settingsChanged() {
        sinkRulesCache = null;
        extractionsCache = null;
        upkeepCache = null;
        if (announced && level != null && !level.isClientSide && block().routed()) {
            network().sinksChanged(routerId);
        }
        setChanged();
    }

    // a block next to the pipe changed
    void neighbourChanged() {
        surroundings.neighbourChanged();
        if (announced && level != null && !level.isClientSide && block().routed()) {
            network().powerSourcesChanged();
            network().inventoriesChanged();
        }
        if (fluids != null) {
            fluids.neighbourChanged();
        }
    }

    // which inventories the pipe works with depends on its arms, so what was seen with the old ones is forgotten
    @SuppressWarnings("deprecation")
    @Override
    public void setBlockState(BlockState state) {
        super.setBlockState(state);
        surroundings.armsChanged();
    }

    PipeUpgrades pipeUpgrades() {
        return upgrades;
    }

    PipeModules pipeModules() {
        return modules;
    }

    PipeSettings settings() {
        return settings;
    }

    PipeSurroundings surroundings() {
        return surroundings;
    }

    PipeTransport transport() {
        return transport;
    }

    // null unless this is a fluid pipe
    public FluidPipe fluidPipe() {
        return fluids;
    }

    @Override
    public Optional<FluidNode> fluidNode() {
        return Optional.ofNullable(fluids);
    }

    public PowerPipe powerPipe() {
        return power;
    }

    @Override
    public Optional<PowerNode> powerNode() {
        return Optional.of(power);
    }

    void insert(TravellingItem item) {
        transport.insert(item);
        setChanged();
    }

    public RouterId routerId() {
        return routerId;
    }

    void markLinksDirty() {
        linksDirty = true;
        security.linksChanged();
    }

    public PipeSecurity security() {
        return security;
    }

    // takes energy from the network of this pipe, all of it or nothing
    public boolean usePower(int amount) {
        return block().routed() && network().usePower(routerId, amount);
    }

    LogisticsPipeBlock block() {
        return (LogisticsPipeBlock) getBlockState().getBlock();
    }

    PipeServices services() {
        return block().services();
    }

    LogisticsNetwork network() {
        if (network == null) {
            network = block().services().networks().of(level);
        }
        return network;
    }

    public List<TravellingItem> travellingItems() {
        return transport.items();
    }

    // an opaque pipe does not tell clients what travels through it
    public boolean opaque() {
        return level != null && level.isClientSide ? opaqueOnClient : upgradeCount(UpgradeKind.OPAQUE) > 0;
    }

    public Container config() {
        return settings.config();
    }

    public Container modules() {
        return modules.slots();
    }

    boolean isDisconnected(Direction side) {
        return upgrades.isDisconnected(side);
    }

    // cuts a side off or connects it again, the upgrade in the hand goes in if the pipe has none yet
    public void toggleDisconnected(ItemStack held, Direction side) {
        upgrades.toggleDisconnected(held, side);
    }

    public Container moduleUpgrades() {
        return modules.upgrades();
    }

    // puts one upgrade from the stack into the pipe, false when it does not fit this pipe or there is no room
    public boolean installUpgrade(ItemStack stack) {
        return upgrades.install(stack);
    }

    public Container upgrades() {
        return upgrades.slots();
    }

    // what the pipe controller opens
    MenuProvider upgradeMenuProvider() {
        return PipeMenus.upgrades(this);
    }

    // where the routed pipe with this id is, empty while it is not loaded
    public Optional<BlockPos> positionOf(RouterId id) {
        return network().node(id) instanceof PipeBlockEntity pipe ? Optional.of(pipe.getBlockPos()) : Optional.empty();
    }

    // takes all upgrades out again
    public List<ItemStack> removeUpgrades() {
        return upgrades.removeAll();
    }

    public Container sneakyUpgrades() {
        return upgrades.sneakyRow();
    }

    // empties the row of the combination upgrade
    public List<ItemStack> removeSneakyRow() {
        return upgrades.sneakyRow().removeAllItems();
    }

    // asked many times a tick, so counted once until the slots change
    int upgradeCount(UpgradeKind kind) {
        if (upgradeCounts == null) {
            upgradeCounts = new int[UpgradeKind.values().length];
            suppliesPower = false;
            Container slots = upgrades.slots();
            for (int slot = 0; slot < UPGRADE_SLOTS; slot++) {
                if (slots.getItem(slot).getItem() instanceof UpgradeItem upgrade) {
                    upgradeCounts[upgrade.kind().ordinal()] += slots.getItem(slot).getCount();
                    suppliesPower |= upgrade.kind() == UpgradeKind.POWER_SUPPLIER_RF;
                }
            }
        }
        return Math.min(upgradeCounts[kind.ordinal()], kind.most());
    }

    private boolean suppliesPower() {
        if (upgradeCounts == null) {
            upgradeCount(UpgradeKind.POWER_SUPPLIER_RF);
        }
        return suppliesPower;
    }

    void signsNeedTicks(boolean needed) {
        signsNeedTicks = needed;
    }

    boolean hasUpgrade(UpgradeKind kind) {
        return upgradeCount(kind) > 0;
    }

    // how fast this pipe sends items on, in percent of their normal speed, plain pipes do not change it
    private Integer boost() {
        if (!block().routed()) {
            return null;
        }
        return 100 + SPEED_UPGRADE_PERCENT * upgradeCount(UpgradeKind.SPEED);
    }

    // the face of an inventory this pipe uses when the inventory is on the given side of the pipe
    Direction accessFace(Direction side) {
        return upgrades.accessFace(side);
    }

    // empty while the chassis has no inventory next to it
    public Optional<Direction> pointed() {
        return surroundings.pointed();
    }

    // turns a chassis to the next inventory around it
    public void pointAtNext() {
        surroundings.pointAtNext();
    }

    public void setMode(int ordinal) {
        settings.data().set(PipeConfigMenu.DATA_MODE, ordinal);
    }

    public void setExclude(boolean enabled) {
        settings.data().set(PipeConfigMenu.DATA_EXCLUDE, enabled ? 1 : 0);
    }

    ProviderSettings providerSettings() {
        return kind() == PipeKind.PROVIDER ? settings.providerSettings() : modules.providerSettings();
    }

    @Override
    public Map<ItemType, Integer> provided() {
        return surroundings.provided();
    }

    @Override
    public int provide(ItemType type, int count, RouterId destination) {
        return surroundings.provide(type, count, destination);
    }

    @Override
    public int roomFor(ItemType type, int upTo) {
        return surroundings.roomFor(type, upTo);
    }

    @Override
    public SupplyMode supplyMode() {
        return settings.supplyMode();
    }

    public boolean defaultRoute() {
        return settings.defaultRoute;
    }

    // provider mode of a provider pipe, supply mode of a supplier pipe, as its number
    public int mode() {
        return settings.mode;
    }

    public boolean exclude() {
        return settings.exclude;
    }

    // no questions asked, a name a player types goes through satelliteSettings
    // like typing it in the screen: a satellite only takes a name no other satellite in reach has, false if refused
    public boolean pasteSatelliteName(String name) {
        return settings.pasteSatelliteName(name);
    }

    public void setSatelliteName(String name) {
        settings.setSatelliteName(name);
    }

    // what the settings screen shows and sets of satellite names: a satellite its own, a crafter the ones it sends to
    SatelliteNames.Source satelliteSettings() {
        return settings.satelliteSettings();
    }

    public void setDefaultRoute(boolean enabled) {
        settings.data().set(PipeConfigMenu.DATA_DEFAULT_ROUTE, enabled ? 1 : 0);
    }

    // takes a stack coming in from outside through the given side
    public void accept(ItemStack stack, Direction side) {
        transport.insert(new TravellingItem(McItemType.batchOf(stack), Directions.toDomain(side), null));
        setChanged();
    }

    // takes an item coming out of a tube
    void acceptFromTube(TravellingItem item) {
        transport.insert(item);
        setChanged();
        if (!opaque()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // a viewer guessing that an item came out of a tube
    void expectFromTube(TravellingItem item) {
        if (!opaque()) {
            transport.expect(item);
        }
    }

    // empty for plain pipes and on the client
    public Optional<RouterStatus> networkStatus() {
        if (level == null || level.isClientSide || !block().routed()) {
            return Optional.empty();
        }
        return Optional.of(network().status(routerId));
    }

    // every routed pipe of the network of this one with what it is up to, nearest first
    public NetworkOverview networkOverview() {
        return NetworkOverview.of(network(), routerId);
    }

    public Map<ItemType, Integer> available() {
        return network().available(routerId);
    }

    // what the network holds for the taking, the inventory of this pipe included
    public Map<ItemType, Integer> amounts() {
        return network().amounts(routerId);
    }

    // what the crafters of the network still have to make
    public Map<ItemType, Integer> craftingBacklog() {
        return network().craftingBacklog(routerId);
    }

    // how many crafted items this pipe is still waiting for
    public int openOrders() {
        return network().openOrders(routerId);
    }

    // gives up on the crafted items this pipe is still waiting for, returns how many that was
    public int cancelOrders() {
        return network().cancelOrders(routerId);
    }

    public RequestOutcome request(ItemBatch wanted) {
        return network().request(routerId, wanted, level.getGameTime());
    }

    // what the request would come to, without making it
    public RequestSummary previewRequest(ItemBatch wanted) {
        return network().preview(routerId, wanted, level.getGameTime());
    }

    // orders what is needed beyond what is already there, all of it or nothing, with simulate it only plans
    public RequestOutcome requestShortfall(Map<ItemType, Integer> needed, Map<ItemType, Integer> have, boolean simulate) {
        return network().requestShortfall(routerId, needed, have, level.getGameTime(), simulate);
    }

    // several items in one go, all of them or nothing
    public RequestOutcome requestList(List<ItemBatch> wanted) {
        return network().requestList(routerId, wanted, level.getGameTime());
    }

    // null unless this pipe takes a disk
    public DiskDrive diskDrive() {
        return drive;
    }

    // null unless this is a routed pipe
    public PipeSigns signs() {
        return signs;
    }

    // remote means the player does not have to stand next to the pipe
    public void openRequestScreen(Player player, boolean remote) {
        player.openMenu(PipeMenus.request(this, remote));
    }

    public MenuProvider menuProvider() {
        return PipeMenus.of(this);
    }

    public RequestTable requestTable() {
        return table;
    }

    // puts a stack into the pipes if somebody takes it, false if nobody does
    boolean sendOff(ItemStack stack) {
        if (!network().wanted(routerId, McItemType.batchOf(stack), level.getGameTime())) {
            return false;
        }
        accept(stack.copy(), Direction.UP);
        return true;
    }

    public FirewallSettings firewallSettings() {
        return firewall;
    }

    public SystemConnector connector() {
        return connector;
    }

    public FrequencySlot frequencySlot() {
        return frequency;
    }

    @Override
    public Optional<UUID> frequency() {
        return frequency == null ? Optional.empty() : frequency.frequency();
    }

    @Override
    public Optional<FirewallRule> firewall() {
        return firewall == null ? Optional.empty() : Optional.of(firewall.rule());
    }

    public void setPriority(int value) {
        settings.data().set(PipeConfigMenu.DATA_PRIORITY, value);
    }

    // copies the recipe of a crafting table next to the pipe into the pipe settings
    public void importRecipe() {
        settings.importRecipe();
    }

    // pipes that only take items in have nothing to do on their own
    private boolean needsUpkeep() {
        if (upkeepCache == null) {
            upkeepCache = kind() == PipeKind.CRAFTING || kind() == PipeKind.SUPPLIER || kind() == PipeKind.CHASSIS
                    && (!extractions().isEmpty() || modules.has(ModuleKind.CRAFTER) || modules.has(ModuleKind.ACTIVE_SUPPLIER)
                    || modules.has(ModuleKind.BULK_STORAGE_SINK));
        }
        return upkeepCache;
    }

    @Override
    public List<CraftingSetup> crafts() {
        return kind() == PipeKind.CRAFTING ? settings.crafts() : modules.crafts();
    }

    public void setSlotSatellite(int slot, String name) {
        settings.setSlotSatellite(slot, name);
    }

    @Override
    public Optional<Cleanup> cleanup() {
        return kind() == PipeKind.CRAFTING ? settings.cleanup() : modules.cleanup();
    }

    @Override
    public List<Supply> supplies() {
        return kind() == PipeKind.SUPPLIER ? settings.supplies() : modules.supplies();
    }

    public SlotSettings slotSettings() {
        return settings.slotSettings();
    }

    @Override
    public List<PatternSupply> patternSupplies() {
        return kind() == PipeKind.SUPPLIER ? settings.patternSupplies() : modules.patternSupplies();
    }

    // a pattern is about the first inventory of the pipe
    @Override
    public List<ItemBatch> slots() {
        return surroundings.slots();
    }

    // moves items on without waiting for the server, so they do not blink at pipe borders
    void clientTick() {
        for (Departure departure : transport.advance(level.getGameTime())) {
            Side exit = departure.exit().orElseThrow();
            BlockPos target = worldPosition.relative(Directions.toMinecraft(exit));
            if (level.getBlockEntity(target) instanceof PipeBlockEntity neighbour) {
                if (!neighbour.opaque()) {
                    neighbour.transport.expect(departure.item().enteringFrom(exit.opposite()));
                }
            } else {
                TubeBlockEntity.expectFromPipe(level, target, Directions.toMinecraft(exit.opposite()), departure.item());
            }
        }
    }

    void serverTick() {
        if (!announced) {
            announced = true;
            linksDirty = true;
            transport.setBoost(boost());
            block().refreshConnections(getBlockState(), level, worldPosition);
            if (block().routed()) {
                network().addRouter(routerId, this, level.getGameTime());
            }
            PipeTopology.markRoutersDirty(level, worldPosition);
        }
        security.tick();
        if (linksDirty && block().routed()) {
            linksDirty = false;
            List<Link> links = PipeTopology.discoverLinks(level, worldPosition);
            network().updateLinks(routerId, links, level.getGameTime());
            network().subSystemPower().updateStraightLinks(routerId, PipeTopology.straightNeighbours(level, worldPosition));
            look.showLinks(links);
        }
        boolean routed = block().routed();
        if (routed) {
            // once a second, every pipe in a tick of its own so that a large base does not do it all at once
            if (lookIn < 0) {
                lookIn = Math.floorMod(-(level.getGameTime() + worldPosition.hashCode()), LogisticsNetwork.POWER_LOOK_INTERVAL);
            }
            boolean due = lookIn == 0;
            lookIn = due ? LogisticsNetwork.POWER_LOOK_INTERVAL - 1 : lookIn - 1;
            if (due || lookAgain) {
                lookAgain = look.look();
            }
        }
        if (table != null) {
            table.tick();
        }
        if (connector != null) {
            connector.tick();
        }
        if (needsUpkeep()) {
            network().tick(routerId, level.getGameTime());
        }
        if (fluids != null) {
            fluids.serverTick();
        }
        if (routed && suppliesPower()) {
            power.serverTick();
        }
        if (signsNeedTicks) {
            signs.tick();
        }
        if (!transport.isEmpty()) {
            for (Departure departure : transport.tick(level.getGameTime(), item -> PipeDepartures.chooseExit(this, item))) {
                PipeDepartures.depart(this, departure);
            }
            setChanged();
        }
        if (transport.pollChanged() && !opaque()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // an item with nowhere to go ends up lying on top of the pipe
    // dropped inside the pipe it would be squeezed out sideways and fly off
    void popOut(ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        ItemEntity item = new ItemEntity(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.8,
                worldPosition.getZ() + 0.5, stack, 0, 0, 0);
        item.setDefaultPickUpDelay();
        level.addFreshEntity(item);
    }

    // sides that touch an inventory
    List<Direction> inventorySides() {
        return surroundings.inventorySides();
    }

    // pipes next to each other get rounds one tick apart
    @Override
    public int phase() {
        return worldPosition.hashCode() & 0xFFFF;
    }

    @Override
    public PipeKind kind() {
        return block().kind();
    }

    @Override
    public boolean hasInventory() {
        return !surroundings.inventorySides().isEmpty();
    }

    @Override
    public Map<Side, InventoryRef> inventories() {
        return surroundings.inventories();
    }

    @Override
    public Set<Side> inventoriesAccepting(ItemBatch batch) {
        return surroundings.inventoriesAccepting(batch);
    }

    // looked up at most once per tick, sinks that depend on it get asked for every passing item
    @Override
    public Map<ItemType, Integer> contents() {
        return surroundings.contents();
    }

    @Override
    public Map<ItemType, Integer> held() {
        return surroundings.held();
    }

    @Override
    public int send(ItemType type, int count, RouterId destination, TransportMode mode) {
        return surroundings.send(type, count, destination, mode);
    }

    @Override
    public boolean provides() {
        return kind() == PipeKind.PROVIDER || modules.has(ModuleKind.PROVIDER);
    }

    @Override
    public List<PowerSource> powerSources() {
        return surroundings.powerSources();
    }

    @Override
    public int drawPower(int amount, boolean simulate) {
        return surroundings.drawPower(amount, simulate);
    }

    @Override
    public List<Extraction> extractions() {
        if (extractionsCache == null) {
            extractionsCache = modules.extractions();
        }
        return extractionsCache;
    }

    @Override
    public List<SinkRule> sinkRules() {
        if (sinkRulesCache == null) {
            sinkRulesCache = kind() == PipeKind.BASIC ? settings.sinkRules() : modules.sinkRules();
        }
        return sinkRulesCache;
    }

    @Override
    public Optional<Recipe> recipe() {
        return settings.recipe();
    }

    // with a satellite chosen the bottom row of the recipe goes there, the rest to this pipe
    @Override
    public List<ItemBatch> satelliteInputs() {
        return settings.satelliteInputs();
    }

    @Override
    public OutputOrders outputOrders() {
        return outputOrders;
    }

    @Override
    public PipeStatistics statistics() {
        return statistics;
    }

    @Override
    public String satelliteName() {
        return settings.satelliteName;
    }

    @Override
    public Map<ItemType, Integer> supplyTargets() {
        return settings.supplyTargets();
    }

    void dropContents() {
        Containers.dropContents(level, worldPosition, modules.slots());
        Containers.dropContents(level, worldPosition, upgrades.slots());
        Containers.dropContents(level, worldPosition, modules.upgrades());
        Containers.dropContents(level, worldPosition, upgrades.sneakyRow());
        if (table != null) {
            table.drops().forEach(stack -> Block.popResource(level, worldPosition, stack));
        }
        if (drive != null) {
            Containers.dropContents(level, worldPosition, drive.slot());
        }
        if (frequency != null) {
            Containers.dropContents(level, worldPosition, frequency.card());
        }
        for (TravellingItem item : transport.clear()) {
            if (!FluidPipe.spilled(this, item)) {
                Block.popResource(level, worldPosition, McItemType.stackOf(item.batch()));
                // an ordered item lying on the ground has to be ordered again
                if (item.mode() == TransportMode.REQUESTED && item.destination().filter(wanted -> !wanted.equals(routerId)).isPresent()) {
                    network().itemLost(item.destination().get(), item.batch(), level.getGameTime());
                }
            }
        }
    }

    @Override
    public void setRemoved() {
        if (announced && level != null && !level.isClientSide && block().routed()) {
            network().removeRouter(routerId, level.getGameTime());
        }
        super.setRemoved();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // clients only need the items for drawing them, and not even those of an opaque pipe
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        if (opaque()) {
            tag.putBoolean("Opaque", true);
        } else {
            tag.put("Items", TransportTags.save(transport, registries));
        }
        if (signs != null) {
            tag.put("Signs", signs.save(registries, true));
        }
        return tag;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putUUID("Router", routerId.value());
        tag.put("Items", TransportTags.save(transport, registries));
        PipeTags.save(this, tag, registries);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.hasUUID("Router")) {
            routerId = new RouterId(tag.getUUID("Router"));
        }
        TransportTags.load(transport, tag.getList("Items", Tag.TAG_COMPOUND), registries);
        opaqueOnClient = tag.getBoolean("Opaque");
        PipeTags.load(this, tag, registries);
    }
}
