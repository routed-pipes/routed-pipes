package logisticspipes.adapter.minecraft;

import logisticspipes.domain.logistics.UpgradeKind;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import logisticspipes.adapter.minecraft.menu.ChassisMenu;
import logisticspipes.adapter.minecraft.menu.FirewallMenu;
import logisticspipes.adapter.minecraft.menu.FrequencyCardMenu;
import logisticspipes.adapter.minecraft.menu.PipeConfigMenu;
import logisticspipes.adapter.minecraft.menu.SatelliteNames;
import logisticspipes.adapter.minecraft.menu.RequestTableMenu;
import logisticspipes.adapter.minecraft.menu.UpgradeMenu;
import logisticspipes.adapter.minecraft.menu.RequestMenu;
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
import logisticspipes.domain.crafting.FluidInput;
import logisticspipes.domain.crafting.SatelliteName;
import logisticspipes.domain.crafting.OutputOrder;
import logisticspipes.domain.crafting.OutputOrders;
import logisticspipes.domain.crafting.Recipe;
import logisticspipes.domain.item.FuzzyFlags;
import logisticspipes.domain.item.FuzzyType;
import logisticspipes.domain.item.ItemBatch;
import logisticspipes.domain.item.ItemType;
import logisticspipes.domain.logistics.Extraction;
import logisticspipes.domain.logistics.FirewallRule;
import logisticspipes.domain.logistics.FuzzySink;
import logisticspipes.domain.logistics.InventoryRef;
import logisticspipes.domain.logistics.ItemSink;
import logisticspipes.domain.logistics.ModuleKind;
import logisticspipes.domain.logistics.PatternSupply;
import logisticspipes.domain.logistics.PipeKind;
import logisticspipes.domain.logistics.ProviderMode;
import logisticspipes.domain.logistics.ProviderSettings;
import logisticspipes.domain.logistics.SinkRule;
import logisticspipes.domain.logistics.SneakyFaces;
import logisticspipes.domain.logistics.Supply;
import logisticspipes.domain.logistics.SupplyMode;
import logisticspipes.domain.routing.Link;
import logisticspipes.domain.request.RequestSummary;
import logisticspipes.domain.routing.RouterId;
import logisticspipes.domain.routing.Side;
import logisticspipes.domain.security.Permission;
import logisticspipes.domain.statistics.PipeStatistics;
import logisticspipes.domain.transport.Departure;
import logisticspipes.domain.transport.PipeTransport;
import logisticspipes.domain.transport.TransportMode;
import logisticspipes.domain.transport.TravellingItem;
import logisticspipes.domain.transport.UnroutedPipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

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
    // capabilities of a neighbour can change without its block changing, so nothing about it is trusted for longer
    private static final int SURROUNDINGS_MAX_AGE = 40;
    // every speed upgrade makes the pipe send items on this much faster, in percent
    private static final int SPEED_UPGRADE_PERCENT = 25;

    private final PipeTransport transport = new PipeTransport();
    private final SimpleContainer config = new SimpleContainer(CONFIG_SLOTS);
    private final SimpleContainer modules = new SimpleContainer(MODULE_SLOTS);
    private final SimpleContainer upgrades = new SimpleContainer(UPGRADE_SLOTS);
    // two slots for every module slot of a chassis, in the order of the modules
    private final SimpleContainer moduleUpgrades = new SimpleContainer(MODULE_SLOTS * UPGRADES_PER_MODULE);
    // the row of sneaky upgrades a combination upgrade unlocks
    private final SimpleContainer sneakyRow = new SimpleContainer(UPGRADE_SLOTS);
    private SneakyFaces facesCache;
    private final SlotSettings slotSettings = new SlotSettings(this::settingsChanged);
    private final FluidCraftingSettings fluidCrafting = new FluidCraftingSettings(this::settingsChanged);
    private final OutputOrders outputOrders = new OutputOrders();
    private final PipeStatistics statistics = new PipeStatistics();
    private final PipeSecurity security = new PipeSecurity(this);
    private final PipeLook look = new PipeLook(this);
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case PipeConfigMenu.DATA_DEFAULT_ROUTE -> defaultRoute ? 1 : 0;
                case PipeConfigMenu.DATA_MODE -> mode;
                case PipeConfigMenu.DATA_EXCLUDE -> exclude ? 1 : 0;
                case PipeConfigMenu.DATA_PRIORITY -> priority;
                case PipeConfigMenu.DATA_BYPRODUCT -> upgradeCount(UpgradeKind.CRAFTING_BYPRODUCT);
                case PipeConfigMenu.DATA_CLEANUP -> upgradeCount(UpgradeKind.CRAFTING_CLEANUP);
                default -> upgradeCount(UpgradeKind.ADVANCED_SATELLITE);
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case PipeConfigMenu.DATA_DEFAULT_ROUTE -> defaultRoute = value != 0;
                case PipeConfigMenu.DATA_MODE -> mode = value;
                case PipeConfigMenu.DATA_EXCLUDE -> exclude = value != 0;
                case PipeConfigMenu.DATA_PRIORITY -> priority = value;
                default -> {
                }
            }
            settingsChanged();
        }

        @Override
        public int getCount() {
            return PipeConfigMenu.DATA_COUNT;
        }
    };
    private RouterId routerId = new RouterId(UUID.randomUUID());
    // the name of a satellite, on a crafting pipe the satellite the bottom row of its recipe goes to
    private String satelliteName = SatelliteName.NONE;
    // the one inventory a chassis works on, null until it finds one
    private Direction pointed;
    private List<Direction> pointableCache = List.of();
    private boolean defaultRoute;
    // provider mode of a provider pipe, supply mode of a supplier pipe
    private int mode;
    private boolean exclude;
    // how eagerly a crafting pipe is used, higher first
    private int priority;
    // with the upgrade for it every ingredient slot can have a satellite of its own
    private final String[] slotSatellites = Collections.nCopies(OUTPUT_SLOT, SatelliteName.NONE).toArray(String[]::new);
    private Boolean upkeepCache;
    // sides cut off with a disconnection upgrade, one bit per direction
    private int disconnected;
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
    private LogisticsNetwork network;
    private Map<ItemType, Integer> contentsCache;
    // what the inventories hold, also what cannot be taken out, looked up at most once per tick
    private Map<ItemType, Integer> heldCache;
    private long heldCachedAt;
    // worked out from modules and settings when first asked for, dropped when those change
    private List<SinkRule> sinkRulesCache;
    private List<Extraction> extractionsCache;
    // what is next to the pipe, looked up again after a neighbour changed and every couple of seconds anyway
    private List<Direction> inventorySidesCache;
    private List<BlockPos> junctionsCache;
    private long surroundingsCachedAt;
    private long contentsCachedAt;
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
        if (kind() == PipeKind.SUPPLIER) {
            mode = SupplyMode.BULK50.ordinal();
        }
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
        // a crafter that cleans up keeps what is on its list unless told otherwise
        exclude = kind() == PipeKind.CRAFTING;
        config.addListener(container -> settingsChanged());
        modules.addListener(container -> settingsChanged());
        upgrades.addListener(container -> {
            if (disconnected != 0 && upgradeCount(UpgradeKind.DISCONNECTION) == 0) {
                // without the upgrade everything is connected again
                disconnected = 0;
                reconnect();
            }
            transport.setBoost(boost());
            neighbourChanged();
            setChanged();
            if (level != null && !level.isClientSide) {
                // clients have to know about the opaque upgrade
                level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
            }
        });
        moduleUpgrades.addListener(container -> settingsChanged());
        upgrades.addListener(container -> {
            facesCache = null;
            // a fuzzy upgrade changes what the pipe takes
            settingsChanged();
        });
        sneakyRow.addListener(container -> {
            facesCache = null;
            neighbourChanged();
            setChanged();
        });
    }

    // filters, modules or the default route changed: forget what was derived from them and tell the network
    private void settingsChanged() {
        sinkRulesCache = null;
        extractionsCache = null;
        upkeepCache = null;
        if (announced && level != null && !level.isClientSide && pipe().routed()) {
            network().sinksChanged(routerId);
        }
        setChanged();
    }

    // a block next to the pipe changed
    void neighbourChanged() {
        inventorySidesCache = null;
        junctionsCache = null;
        contentsCache = null;
        if (fluids != null) {
            fluids.neighbourChanged();
        }
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

    private boolean surroundingsStale() {
        return level.getGameTime() - surroundingsCachedAt >= SURROUNDINGS_MAX_AGE;
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
        return pipe().routed() && network().usePower(routerId, amount);
    }

    private LogisticsPipeBlock pipe() {
        return (LogisticsPipeBlock) getBlockState().getBlock();
    }

    PipeServices services() {
        return pipe().services();
    }

    LogisticsNetwork network() {
        if (network == null) {
            network = pipe().services().networks().of(level);
        }
        return network;
    }

    private ItemStorage storage() {
        return pipe().services().storage();
    }

    public List<TravellingItem> travellingItems() {
        return transport.items();
    }

    // an opaque pipe does not tell clients what travels through it
    public boolean opaque() {
        return level != null && level.isClientSide ? opaqueOnClient : upgradeCount(UpgradeKind.OPAQUE) > 0;
    }

    public Container config() {
        return config;
    }

    public Container modules() {
        return modules;
    }

    boolean isDisconnected(Direction side) {
        return (disconnected & 1 << side.get3DDataValue()) != 0;
    }

    // works out the connections of this pipe and the ones around it again
    private void reconnect() {
        if (level == null || level.isClientSide) {
            return;
        }
        pipe().refreshConnections(getBlockState(), level, worldPosition);
        for (Direction side : Direction.values()) {
            BlockPos neighbour = worldPosition.relative(side);
            if (level.getBlockState(neighbour).getBlock() instanceof LogisticsPipeBlock other) {
                other.refreshConnections(level.getBlockState(neighbour), level, neighbour);
            }
        }
        neighbourChanged();
        // the routers on both sides of the cut have to look again
        markLinksDirty();
        for (Direction side : Direction.values()) {
            BlockPos neighbour = worldPosition.relative(side);
            if (level.getBlockEntity(neighbour) instanceof PipeBlockEntity other) {
                other.markLinksDirty();
            }
            PipeTopology.markRoutersDirty(level, neighbour);
        }
    }

    // cuts a side off or connects it again, the upgrade in the hand goes in if the pipe has none yet
    public void toggleDisconnected(ItemStack held, Direction side) {
        if (upgradeCount(UpgradeKind.DISCONNECTION) == 0 && !installUpgrade(held)) {
            return;
        }
        disconnected ^= 1 << side.get3DDataValue();
        setChanged();
        reconnect();
    }

    public Container moduleUpgrades() {
        return moduleUpgrades;
    }

    // puts one upgrade from the stack into the pipe, false when it does not fit this pipe or there is no room
    public boolean installUpgrade(ItemStack stack) {
        if (!pipe().routed() || !(stack.getItem() instanceof UpgradeItem upgrade) || !upgrade.kind().fitsPipe(kind())) {
            return false;
        }
        // with a combination upgrade in the pipe sneaky upgrades go into its row first
        for (int slot = 0; slot < UPGRADE_SLOTS && upgrade.kind() == UpgradeKind.SNEAKY && hasUpgrade(UpgradeKind.SNEAKY_COMBINATION); slot++) {
            if (sneakyRow.getItem(slot).isEmpty()) {
                sneakyRow.setItem(slot, stack.split(1));
                return true;
            }
        }
        for (int slot = 0; slot < UPGRADE_SLOTS; slot++) {
            ItemStack present = upgrades.getItem(slot);
            if (present.isEmpty()) {
                upgrades.setItem(slot, stack.split(1));
                return true;
            }
            if (ItemStack.isSameItemSameComponents(present, stack) && present.getCount() < UPGRADE_STACK) {
                stack.shrink(1);
                present.grow(1);
                upgrades.setChanged();
                return true;
            }
        }
        return false;
    }

    public Container upgrades() {
        return upgrades;
    }

    // what the pipe controller opens
    MenuProvider upgradeMenuProvider() {
        return new SimpleMenuProvider((id, inventory, player) -> new UpgradeMenu(pipe().services().upgradeMenu().get(), id,
                inventory, this, security.access(player, Permission.UPGRADE_PIPES).allowed(),
                user -> Container.stillValidBlockEntity(this, user)), getBlockState().getBlock().getName());
    }

    // where the routed pipe with this id is, empty while it is not loaded
    public Optional<BlockPos> positionOf(RouterId id) {
        return network().node(id) instanceof PipeBlockEntity pipe ? Optional.of(pipe.getBlockPos()) : Optional.empty();
    }

    // takes all upgrades out again
    public List<ItemStack> removeUpgrades() {
        List<ItemStack> removed = new ArrayList<>(upgrades.removeAllItems());
        removed.addAll(sneakyRow.removeAllItems());
        return removed;
    }

    public Container sneakyUpgrades() {
        return sneakyRow;
    }

    // empties the row of the combination upgrade
    public List<ItemStack> removeSneakyRow() {
        return sneakyRow.removeAllItems();
    }

    private static int count(Container container, int from, int to, UpgradeKind kind) {
        int count = 0;
        for (int slot = from; slot < to; slot++) {
            if (container.getItem(slot).getItem() instanceof UpgradeItem upgrade && upgrade.kind() == kind) {
                count += container.getItem(slot).getCount();
            }
        }
        return Math.min(count, kind.most());
    }

    private int upgradeCount(UpgradeKind kind) {
        return count(upgrades, 0, UPGRADE_SLOTS, kind);
    }

    boolean hasUpgrade(UpgradeKind kind) {
        return upgradeCount(kind) > 0;
    }

    // upgrades in the two slots of a module
    private int moduleUpgradeCount(int moduleSlot, UpgradeKind kind) {
        return count(moduleUpgrades, moduleSlot * UPGRADES_PER_MODULE, (moduleSlot + 1) * UPGRADES_PER_MODULE, kind);
    }

    // how fast this pipe sends items on, in percent of their normal speed, plain pipes do not change it
    private Integer boost() {
        if (!pipe().routed()) {
            return null;
        }
        return 100 + SPEED_UPGRADE_PERCENT * upgradeCount(UpgradeKind.SPEED);
    }

    // the face of an inventory this pipe uses when the inventory is on the given side of the pipe
    Direction accessFace(Direction side) {
        return Directions.toMinecraft(faces().reading(Directions.toDomain(side.getOpposite())));
    }

    private static Optional<Side> sneakyFace(ItemStack stack) {
        return stack.getItem() instanceof UpgradeItem upgrade && upgrade.kind() == UpgradeKind.SNEAKY
                ? Optional.of(Directions.toDomain(UpgradeItem.sideOf(stack))) : Optional.empty();
    }

    // what the sneaky upgrades of the pipe itself say
    private SneakyFaces faces() {
        if (facesCache == null) {
            List<SneakyFaces.Slot> slots = new ArrayList<>();
            List<Side> row = new ArrayList<>();
            for (int slot = 0; slot < UPGRADE_SLOTS; slot++) {
                ItemStack stack = upgrades.getItem(slot);
                boolean combination = stack.getItem() instanceof UpgradeItem upgrade && upgrade.kind() == UpgradeKind.SNEAKY_COMBINATION;
                slots.add(combination ? SneakyFaces.Slot.COMBINATION
                        : sneakyFace(stack).map(SneakyFaces.Slot::sneaky).orElse(SneakyFaces.Slot.OTHER));
                sneakyFace(sneakyRow.getItem(slot)).ifPresent(row::add);
            }
            facesCache = SneakyFaces.of(slots, row);
        }
        return facesCache;
    }

    // the sneaky face of the sink module that takes the item, empty if none does or it has no sneaky upgrade
    private Optional<Side> moduleFace(ItemType type) {
        List<SneakyFaces.ModuleFace> sinks = new ArrayList<>();
        boolean any = false;
        boolean looksInside = false;
        for (int slot = 0; slot < pipe().moduleSlots(); slot++) {
            int first = slot * UPGRADES_PER_MODULE;
            Optional<Side> own = sneakyFace(moduleUpgrades.getItem(first)).or(() -> sneakyFace(moduleUpgrades.getItem(first + 1)));
            for (SinkRule rule : moduleRules(slot)) {
                any |= own.isPresent();
                looksInside |= rule.needsContents();
                sinks.add(new SneakyFaces.ModuleFace(rule, own));
            }
        }
        return any ? SneakyFaces.ofModuleTaking(type, looksInside ? contents() : Map.of(), sinks) : Optional.empty();
    }

    // how the module in a slot takes items nobody asked for, with a fuzzy upgrade of its own its flags count too
    private List<SinkRule> moduleRules(int slot) {
        List<SinkRule> rules = new ArrayList<>();
        ModuleItem.ruleOf(modules.getItem(slot)).ifPresent(rules::add);
        if (moduleUpgradeCount(slot, UpgradeKind.FUZZY) > 0) {
            ModuleItem.fuzzySinkOf(modules.getItem(slot)).ifPresent(rules::add);
        }
        return rules;
    }

    // a recipe slot as the planner sees it, with fuzzy flags it stands for everything that matches
    private ItemBatch marked(int slot) {
        return slotSettings.batch(slot, config.getItem(slot), hasUpgrade(UpgradeKind.FUZZY));
    }

    // the faces of the inventory on that side an arriving item tries, in order
    // ordered items are not for a sink module, so the sneaky upgrade of one does not count for them
    private List<Direction> insertFaces(Direction side, ItemType type, boolean ordered) {
        SneakyFaces faces = ordered ? faces() : faces().forModule(moduleFace(type));
        return faces.inserting(Directions.toDomain(side.getOpposite())).stream().map(Directions::toMinecraft).toList();
    }

    // returns what did not fit through any of the faces
    private ItemStack insertThrough(BlockPos target, List<Direction> faces, ItemStack stack, boolean simulate) {
        ItemStack rest = stack;
        for (Direction face : faces) {
            if (!rest.isEmpty() && storage().exists(level, target, face)) {
                rest = storage().insert(level, target, face, rest, simulate);
            }
        }
        return rest;
    }

    // the modules in the slots this chassis really has
    private List<ItemStack> pluggedModules() {
        List<ItemStack> plugged = new ArrayList<>();
        for (int slot = 0; slot < pipe().moduleSlots(); slot++) {
            if (modules.getItem(slot).getItem() instanceof ModuleItem) {
                plugged.add(modules.getItem(slot));
            }
        }
        return plugged;
    }

    private boolean hasModule(ModuleKind wanted) {
        return pluggedModules().stream().anyMatch(stack -> ((ModuleItem) stack.getItem()).kind() == wanted);
    }

    // empty while the chassis has no inventory next to it
    public Optional<Direction> pointed() {
        return inventorySides().isEmpty() || kind() != PipeKind.CHASSIS ? Optional.empty() : Optional.of(pointed);
    }

    // turns a chassis to the next inventory around it
    public void pointAtNext() {
        if (pointed().isEmpty()) {
            return;
        }
        pointed = pointableCache.get((pointableCache.indexOf(pointed) + 1) % pointableCache.size());
        neighbourChanged();
        settingsChanged();
    }

    public void setMode(int ordinal) {
        data.set(PipeConfigMenu.DATA_MODE, ordinal);
    }

    public void setExclude(boolean enabled) {
        data.set(PipeConfigMenu.DATA_EXCLUDE, enabled ? 1 : 0);
    }

    private ProviderSettings providerSettings() {
        if (kind() == PipeKind.PROVIDER) {
            ProviderMode[] modes = ProviderMode.values();
            return new ProviderSettings(modes[Math.floorMod(mode, modes.length)], supplyTargets().keySet(), exclude);
        }
        return pluggedModules().stream().filter(stack -> ((ModuleItem) stack.getItem()).kind() == ModuleKind.PROVIDER)
                .findFirst().map(ModuleItem::providerOf).orElse(ProviderSettings.EVERYTHING);
    }

    private static List<ItemBatch> batchesOf(List<ItemStack> slots) {
        List<ItemBatch> batches = new ArrayList<>();
        for (ItemStack stack : slots) {
            batches.add(stack.isEmpty() ? null : McItemType.batchOf(stack));
        }
        return batches;
    }

    @Override
    public Map<ItemType, Integer> provided() {
        ProviderSettings settings = providerSettings();
        Map<ItemType, Integer> stock = new LinkedHashMap<>();
        for (Direction side : inventorySides()) {
            List<ItemStack> slots = storage().slots(level, worldPosition.relative(side), accessFace(side));
            settings.stock(batchesOf(slots)).forEach((type, count) -> stock.merge(type, count, Integer::sum));
        }
        return stock;
    }

    @Override
    public int provide(ItemType type, int count, RouterId destination) {
        ProviderSettings settings = providerSettings();
        int remaining = count;
        for (Direction side : inventorySides()) {
            BlockPos target = worldPosition.relative(side);
            List<ItemBatch> slots = batchesOf(storage().slots(level, target, accessFace(side)));
            int[] takeable = settings.takeable(slots);
            for (int slot = 0; slot < takeable.length && remaining > 0; slot++) {
                if (takeable[slot] == 0 || !slots.get(slot).type().equals(type)) {
                    continue;
                }
                // bins and drawers hand out one stack per call, so a slot is asked until it has given its share
                int allowed = takeable[slot];
                while (allowed > 0 && remaining > 0) {
                    ItemStack taken = storage().extractSlot(level, target, accessFace(side), slot, Math.min(allowed, remaining));
                    if (taken.isEmpty()) {
                        break;
                    }
                    transport.insert(new TravellingItem(McItemType.batchOf(taken), Directions.toDomain(side), destination));
                    allowed -= taken.getCount();
                    remaining -= taken.getCount();
                }
            }
        }
        if (remaining < count) {
            contentsCache = null;
            setChanged();
        }
        return count - remaining;
    }

    @Override
    public int roomFor(ItemType type, int upTo) {
        ItemStack stack = McItemType.stackOf(new ItemBatch(type, upTo));
        int room = 0;
        for (Direction side : inventorySides()) {
            room += upTo - insertThrough(worldPosition.relative(side), insertFaces(side, type, true), stack, true).getCount();
        }
        return Math.min(room, upTo);
    }

    @Override
    public SupplyMode supplyMode() {
        SupplyMode[] modes = SupplyMode.values();
        return modes[Math.floorMod(mode, modes.length)];
    }

    public boolean defaultRoute() {
        return defaultRoute;
    }

    // provider mode of a provider pipe, supply mode of a supplier pipe, as its number
    public int mode() {
        return mode;
    }

    public boolean exclude() {
        return exclude;
    }

    // no questions asked, a name a player types goes through satelliteSettings
    public void setSatelliteName(String name) {
        satelliteName = SatelliteName.clean(name);
        settingsChanged();
    }

    // what the settings screen shows and sets of satellite names: a satellite its own, a crafter the ones it sends to
    SatelliteNames.Source satelliteSettings() {
        return new SatelliteNames.Source() {
            @Override
            public String name(int field) {
                if (field >= SatelliteNames.FIELD_FLUID) {
                    return fluidCrafting.satellite(field - SatelliteNames.FIELD_FLUID);
                }
                return field == SatelliteNames.FIELD_OWN ? satelliteName : slotSatellites[field - SatelliteNames.FIELD_SLOT];
            }

            @Override
            public SatelliteName.Verdict setName(int field, String name) {
                if (field >= SatelliteNames.FIELD_FLUID) {
                    fluidCrafting.setSatellite(field - SatelliteNames.FIELD_FLUID, name);
                    return SatelliteName.Verdict.ACCEPTED;
                }
                if (field != SatelliteNames.FIELD_OWN) {
                    setSlotSatellite(field - SatelliteNames.FIELD_SLOT, name);
                    return SatelliteName.Verdict.ACCEPTED;
                }
                // a crafter may point at any satellite or at none, a satellite needs a name of its own
                SatelliteName.Verdict verdict = kind() == PipeKind.CRAFTING ? SatelliteName.Verdict.ACCEPTED
                        : SatelliteName.check(name, network().satelliteNames(routerId, kind()));
                if (verdict == SatelliteName.Verdict.ACCEPTED) {
                    setSatelliteName(name);
                }
                return verdict;
            }

            @Override
            public List<String> offered(PipeKind satellites) {
                return kind() == PipeKind.CRAFTING ? network().satelliteNames(routerId, satellites) : List.of();
            }
        };
    }

    public void setDefaultRoute(boolean enabled) {
        data.set(PipeConfigMenu.DATA_DEFAULT_ROUTE, enabled ? 1 : 0);
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
        if (level == null || level.isClientSide || !pipe().routed()) {
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
        player.openMenu(requestScreen(remote));
    }

    private MenuProvider requestScreen(boolean remote) {
        return new SimpleMenuProvider((id, inventory, player) -> new RequestMenu(pipe().services().requestMenu().get(), id,
                this, user -> !isRemoved() && (remote || Container.stillValidBlockEntity(this, user)),
                // the disk is only at hand when standing at the pipe
                remote || kind() != PipeKind.REQUEST_MK2 ? null : drive),
                getBlockState().getBlock().getName());
    }

    public MenuProvider menuProvider() {
        Component title = getBlockState().getBlock().getName();
        PipeServices services = pipe().services();
        if (fluids != null) {
            return fluids.menuProvider();
        }
        if (kind().requestPipe()) {
            return requestScreen(false);
        }
        if (kind() == PipeKind.REQUEST_TABLE) {
            RequestTableMenu.Actions actions = new RequestTableMenu.Actions() {
                @Override
                public ItemStack result() {
                    return table.result();
                }

                @Override
                public void craft() {
                    table.craft();
                }

                @Override
                public void order(Player player, int crafts, boolean missingOnly) {
                    // opening the table needs the settings permission, ordering from it the one for requests
                    if (security.allows(player, Permission.ACTIVE_REQUESTING)) {
                        tellWhatIsMissing(player, table.orderIngredients(crafts, missingOnly));
                    }
                }

                @Override
                public void openList(Player player) {
                    if (security.allows(player, Permission.ACTIVE_REQUESTING)) {
                        openRequestScreen(player, false);
                    }
                }

                @Override
                public void openDisk(Player player) {
                    if (drive.holdsDisk() && security.allows(player, Permission.ACTIVE_REQUESTING)) {
                        drive.open(player);
                    }
                }
            };
            return new SimpleMenuProvider((id, inventory, player) -> new RequestTableMenu(services.requestTableMenu().get(), id,
                    inventory, table.grid(), table.buffer(), table.sort(), drive.slot(),
                    user -> Container.stillValidBlockEntity(this, user),
                    actions), title);
        }
        if (connector != null) {
            return connector.menuProvider();
        }
        if (frequency != null) {
            return new SimpleMenuProvider((id, inventory, player) -> new FrequencyCardMenu(services.frequencyCardMenu().get(), id,
                    inventory, frequency.card(), user -> Container.stillValidBlockEntity(this, user)), title);
        }
        if (kind() == PipeKind.FIREWALL) {
            return new SimpleMenuProvider((id, inventory, player) -> new FirewallMenu(services.firewallMenu().get(), id, inventory,
                    firewall.list(), firewall.data(), user -> Container.stillValidBlockEntity(this, user)), title);
        }
        if (kind() == PipeKind.CHASSIS) {
            ContainerData chassis = new ContainerData() {
                @Override
                public int get(int index) {
                    return switch (index) {
                        case ChassisMenu.DATA_SLOTS -> pipe().moduleSlots();
                        case ChassisMenu.DATA_MODULE_UPGRADES -> upgradeCount(UpgradeKind.MODULE_UPGRADE);
                        default -> pointed().map(Direction::get3DDataValue).orElse(-1);
                    };
                }

                @Override
                public void set(int index, int value) {
                }

                @Override
                public int getCount() {
                    return ChassisMenu.DATA_COUNT;
                }
            };
            return new SimpleMenuProvider((id, inventory, player) -> new ChassisMenu(services.chassisMenu().get(), id,
                    inventory, modules, moduleUpgrades, chassis, user -> Container.stillValidBlockEntity(this, user), this::pointAtNext,
                    this::openModuleSettings), title);
        }
        return new SimpleMenuProvider((id, inventory, player) -> new PipeConfigMenu(services.configMenu(kind()), id,
                inventory, kind(), config, data, slotSettings.data(() -> upgradeCount(UpgradeKind.PATTERN), () -> upgradeCount(UpgradeKind.FUZZY)),
                user -> Container.stillValidBlockEntity(this, user), this::importRecipe, satelliteSettings(),
                fluidCrafting.data(() -> upgradeCount(UpgradeKind.FLUID_CRAFTING))), title);
    }

    // the settings of a module while it sits in the chassis
    private void openModuleSettings(Player player, int slot) {
        ItemStack module = modules.getItem(slot);
        if (slot < pipe().moduleSlots() && module.getItem() instanceof ModuleItem item) {
            item.settingsProvider(module, user -> Container.stillValidBlockEntity(this, user) && modules.getItem(slot) == module,
                    this::settingsChanged, this::importRecipe, upgrade -> moduleUpgradeCount(slot, upgrade),
                    kind -> network().satelliteNames(routerId, kind)).ifPresent(player::openMenu);
        }
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

    void tellWhatIsMissing(Player player, RequestOutcome outcome) {
        if (outcome.started()) {
            return;
        }
        if (!outcome.powered()) {
            player.displayClientMessage(Component.translatable("logisticspipes.request.no_power"), false);
            return;
        }
        MutableComponent missing = Component.translatable("logisticspipes.request.missing");
        outcome.missing().forEach((type, count) -> missing.append("\n").append(Component.translatable(
                "logisticspipes.request.missing_entry", count, McItemType.stackOf(new ItemBatch(type, 1)).getHoverName())));
        player.displayClientMessage(missing, false);
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
        data.set(PipeConfigMenu.DATA_PRIORITY, value);
    }

    // copies the recipe of a crafting table next to the pipe into the pipe settings
    public void importRecipe() {
        importRecipe(config);
    }

    private void importRecipe(Container target) {
        for (Direction side : Direction.values()) {
            BlockPos neighbour = worldPosition.relative(side);
            if (level.isLoaded(neighbour) && level.getBlockEntity(neighbour) instanceof CraftingTableBlockEntity table) {
                for (int slot = 0; slot < OUTPUT_SLOT; slot++) {
                    target.setItem(slot, table.pattern().getItem(slot).copy());
                }
                target.setItem(OUTPUT_SLOT, table.result());
                return;
            }
        }
    }

    // pipes that only take items in have nothing to do on their own
    private boolean needsUpkeep() {
        if (upkeepCache == null) {
            upkeepCache = kind() == PipeKind.CRAFTING || kind() == PipeKind.SUPPLIER || kind() == PipeKind.CHASSIS
                    && (!extractions().isEmpty() || hasModule(ModuleKind.CRAFTER) || hasModule(ModuleKind.ACTIVE_SUPPLIER)
                    || hasModule(ModuleKind.BULK_STORAGE_SINK));
        }
        return upkeepCache;
    }

    @Override
    public List<CraftingSetup> crafts() {
        if (kind() == PipeKind.CRAFTING) {
            ItemStack extra = config.getItem(BYPRODUCT_SLOT);
            Optional<ItemBatch> byproduct = extra.isEmpty() || upgradeCount(UpgradeKind.CRAFTING_BYPRODUCT) == 0
                    ? Optional.empty() : Optional.of(McItemType.batchOf(extra));
            List<FluidInput> fluids = fluidCrafting.inputs(config, upgradeCount(UpgradeKind.FLUID_CRAFTING),
                    hasUpgrade(UpgradeKind.ADVANCED_SATELLITE));
            return recipe().map(recipe -> List.of(new CraftingSetup(recipe, bySatellite(), priority, byproduct, fluids)))
                    .orElse(List.of());
        }
        List<CraftingSetup> crafts = new ArrayList<>();
        for (int slot = 0; slot < pipe().moduleSlots(); slot++) {
            ModuleItem.craftingOf(modules.getItem(slot), moduleUpgradeCount(slot, UpgradeKind.CRAFTING_BYPRODUCT) > 0,
                    moduleUpgradeCount(slot, UpgradeKind.ADVANCED_SATELLITE) > 0, moduleUpgradeCount(slot, UpgradeKind.FUZZY) > 0,
                    moduleUpgradeCount(slot, UpgradeKind.FLUID_CRAFTING)).ifPresent(crafts::add);
        }
        return crafts;
    }

    // which ingredients go to which satellite: one of its own per slot with the upgrade, else the bottom row to one
    private Map<String, List<ItemBatch>> bySatellite() {
        if (upgradeCount(UpgradeKind.ADVANCED_SATELLITE) == 0) {
            return Map.of(satelliteName, satelliteInputs());
        }
        Map<String, List<ItemBatch>> inputs = new LinkedHashMap<>();
        for (int slot = 0; slot < OUTPUT_SLOT; slot++) {
            if (!slotSatellites[slot].isEmpty() && !config.getItem(slot).isEmpty()) {
                inputs.computeIfAbsent(slotSatellites[slot], key -> new ArrayList<>()).add(marked(slot));
            }
        }
        return inputs;
    }

    public void setSlotSatellite(int slot, String name) {
        slotSatellites[slot] = SatelliteName.clean(name);
        settingsChanged();
    }

    private static Set<ItemType> plainTypes(Container container, int from, int to) {
        Set<ItemType> types = new HashSet<>();
        for (int slot = from; slot < to; slot++) {
            if (!container.getItem(slot).isEmpty()) {
                types.add(McItemType.batchOf(container.getItem(slot)).type().plain());
            }
        }
        return types;
    }

    @Override
    public Optional<Cleanup> cleanup() {
        if (kind() == PipeKind.CRAFTING) {
            int listed = upgradeCount(UpgradeKind.CRAFTING_CLEANUP) * PipeConfigMenu.CLEANUP_PER_UPGRADE;
            return listed == 0 ? Optional.empty()
                    : Optional.of(new Cleanup(plainTypes(config, CLEANUP_FIRST, CLEANUP_FIRST + listed), exclude));
        }
        for (int slot = 0; slot < pipe().moduleSlots(); slot++) {
            Optional<Cleanup> cleanup = ModuleItem.cleanupOf(modules.getItem(slot), moduleUpgradeCount(slot, UpgradeKind.CRAFTING_CLEANUP));
            if (cleanup.isPresent()) {
                return cleanup;
            }
        }
        return Optional.empty();
    }

    @Override
    public List<Supply> supplies() {
        if (kind() == PipeKind.SUPPLIER) {
            return hasUpgrade(UpgradeKind.PATTERN) ? List.of() : List.of(new Supply(supplyTargets(), supplyMode()));
        }
        // a module with a pattern upgrade goes by slots, see patternSupplies
        List<Supply> supplies = new ArrayList<>();
        for (int slot = 0; slot < pipe().moduleSlots(); slot++) {
            if (moduleUpgradeCount(slot, UpgradeKind.PATTERN) == 0) {
                ModuleItem.supplyOf(modules.getItem(slot)).ifPresent(supplies::add);
            }
        }
        return supplies;
    }

    public SlotSettings slotSettings() {
        return slotSettings;
    }

    @Override
    public List<PatternSupply> patternSupplies() {
        if (kind() == PipeKind.SUPPLIER) {
            if (!hasUpgrade(UpgradeKind.PATTERN)) {
                return List.of();
            }
            List<PatternSupply.Entry> entries = new ArrayList<>();
            for (int slot = 0; slot < OUTPUT_SLOT; slot++) {
                if (!config.getItem(slot).isEmpty()) {
                    entries.add(new PatternSupply.Entry(slotSettings.patternSlot(slot), McItemType.batchOf(config.getItem(slot))));
                }
            }
            return List.of(new PatternSupply(entries, supplyMode(), slotSettings.limited()));
        }
        List<PatternSupply> patterns = new ArrayList<>();
        for (int slot = 0; slot < pipe().moduleSlots(); slot++) {
            if (moduleUpgradeCount(slot, UpgradeKind.PATTERN) > 0) {
                ModuleItem.patternOf(modules.getItem(slot)).ifPresent(patterns::add);
            }
        }
        return patterns;
    }

    // a pattern is about the first inventory of the pipe
    @Override
    public List<ItemBatch> slots() {
        List<Direction> sides = inventorySides();
        return sides.isEmpty() ? List.of()
                : batchesOf(storage().held(level, worldPosition.relative(sides.get(0)), accessFace(sides.get(0))));
    }

    // puts what a pattern ordered into the slots it is meant for, returns what is left over
    private ItemStack fillPattern(List<PatternSupply> patterns, BlockPos target, Direction face, ItemStack stack) {
        ItemStack rest = stack;
        for (PatternSupply pattern : patterns) {
            if (rest.isEmpty()) {
                break;
            }
            List<ItemBatch> slots = batchesOf(storage().held(level, target, face));
            for (Map.Entry<Integer, Integer> placed : pattern.placement(McItemType.batchOf(rest), slots).entrySet()) {
                ItemStack refused = storage().insertSlot(level, target, face, placed.getKey(), rest.copyWithCount(placed.getValue()), false);
                rest = rest.copyWithCount(rest.getCount() - placed.getValue() + refused.getCount());
            }
        }
        return rest;
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
            pipe().refreshConnections(getBlockState(), level, worldPosition);
            if (pipe().routed()) {
                network().addRouter(routerId, this, level.getGameTime());
            }
            PipeTopology.markRoutersDirty(level, worldPosition);
        }
        security.tick();
        if (linksDirty && pipe().routed()) {
            linksDirty = false;
            List<Link> links = PipeTopology.discoverLinks(level, worldPosition);
            network().updateLinks(routerId, links, level.getGameTime());
            network().subSystemPower().updateStraightLinks(routerId, PipeTopology.straightNeighbours(level, worldPosition));
            look.showLinks(links);
        }
        if (pipe().routed()) {
            look.tick();
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
        if (pipe().routed()) {
            power.serverTick();
            signs.tick();
        }
        if (!transport.isEmpty()) {
            for (Departure departure : transport.tick(level.getGameTime(), this::chooseExit)) {
                depart(departure);
            }
            setChanged();
        }
        if (transport.pollChanged() && !opaque()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private Optional<Side> chooseExit(TravellingItem item) {
        if (fluids != null && fluids.stops(item)) {
            return Optional.empty();
        }
        if (pipe().routed()) {
            return network().chooseExit(routerId, item, level.getGameTime());
        }
        Set<Side> connections = EnumSet.noneOf(Side.class);
        for (Direction side : Direction.values()) {
            if (LogisticsPipeBlock.connected(getBlockState(), side)) {
                connections.add(Directions.toDomain(side));
            }
        }
        return UnroutedPipe.chooseExit(connections, item, router -> PipeTopology
                .branchTowards(level, worldPosition, router).map(Directions::toDomain));
    }

    private void depart(Departure departure) {
        TravellingItem item = departure.item();
        if (FluidPipe.ended(this, departure)) {
            return;
        }
        ItemStack stack = McItemType.stackOf(item.batch());
        if (departure.exit().isEmpty() && table != null && item.destination().filter(routerId::equals).isPresent()) {
            // what a request table ordered ends up in its buffer
            popOut(table.takeIn(stack));
            return;
        }
        if (departure.exit().isEmpty()) {
            if (item.mode() == TransportMode.REQUESTED && item.destination().filter(wanted -> !wanted.equals(routerId)).isPresent()) {
                network().itemLost(item.destination().get(), item.batch(), level.getGameTime());
            }
            popOut(stack);
            return;
        }
        Side exit = departure.exit().get();
        Direction side = Directions.toMinecraft(exit);
        BlockPos target = worldPosition.relative(side);
        if (!level.isLoaded(target)) {
            // wait at the chunk border and try again next tick
            transport.insert(TravellingItem.restore(item.id(), item.batch(), item.origin(),
                    item.destination().orElse(null), item.nextHop().orElse(null), item.mode(), exit,
                    PipeTransport.PIPE_LENGTH - item.speed(), item.speed()));
            return;
        }
        if (TubeBlockEntity.takeFromPipe(level, target, side.getOpposite(), item)) {
            return;
        }
        if (level.getBlockEntity(target) instanceof PipeBlockEntity neighbour) {
            neighbour.transport.insert(item.enteringFrom(exit.opposite()));
            neighbour.setChanged();
            // tell clients now, a neighbour that already ticked would only do so a tick later and the item would blink
            if (!neighbour.opaque()) {
                level.sendBlockUpdated(target, neighbour.getBlockState(), neighbour.getBlockState(), Block.UPDATE_CLIENTS);
            }
            return;
        }
        if (connector != null && connector.handsOver(item, side)) {
            return;
        }
        List<PatternSupply> patterns = item.mode() == TransportMode.REQUESTED && item.destination().filter(routerId::equals).isPresent()
                && inventorySides().indexOf(side) == 0 ? patternSupplies() : List.of();
        if (!patterns.isEmpty()) {
            stack = fillPattern(patterns, target, accessFace(side), stack);
            contentsCache = null;
            if (stack.isEmpty()) {
                return;
            }
            if (patterns.stream().anyMatch(pattern -> pattern.limited() && pattern.lists(item.batch().type()))) {
                // limited: what does not fit its slot must not end up in another one, so it goes back into the pipes
                transport.insert(new TravellingItem(McItemType.batchOf(stack), exit, null));
                return;
            }
        }
        ItemStack rest = insertThrough(target, insertFaces(side, item.batch().type(), item.mode() == TransportMode.REQUESTED), stack, false);
        contentsCache = null;
        if (rest.isEmpty()) {
            return;
        }
        if (pipe().routed() && item.mode() == TransportMode.REQUESTED && item.destination().filter(routerId::equals).isPresent()) {
            // ordered for this inventory and it did not all fit, the rest waits in the pipe for room
            transport.insert(item.remainder(McItemType.batchOf(rest)));
        } else if (pipe().routed()) {
            // did not fit after all, send it somewhere else
            transport.insert(new TravellingItem(McItemType.batchOf(rest), exit, null));
        } else {
            popOut(rest);
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
        if (inventorySidesCache == null || surroundingsStale()) {
            lookAround();
        }
        return inventorySidesCache;
    }

    private void lookAround() {
        List<Direction> sides = new ArrayList<>();
        List<BlockPos> junctions = new ArrayList<>();
        for (Direction side : Direction.values()) {
            BlockPos target = worldPosition.relative(side);
            // never look into a chunk that is not loaded, that would load it
            if (!level.isLoaded(target)) {
                continue;
            }
            if (level.getBlockEntity(target) instanceof PowerJunctionBlockEntity) {
                junctions.add(target);
            } else if (fluids == null && LogisticsPipeBlock.connected(getBlockState(), side)
                    && !(level.getBlockState(target).getBlock() instanceof LogisticsPipeBlock)
                    && storage().exists(level, target, accessFace(side))) {
                sides.add(side);
            }
        }
        if (table != null) {
            // a request table keeps what it gets, it does not fill inventories next to it
            sides.clear();
        }
        pointableCache = sides;
        if (kind() == PipeKind.CHASSIS && !sides.isEmpty()) {
            // a chassis works on one inventory only, the first one it finds unless it was turned
            if (pointed == null || !sides.contains(pointed)) {
                pointed = sides.get(0);
                setChanged();
            }
            sides = List.of(pointed);
        }
        inventorySidesCache = sides;
        junctionsCache = junctions;
        surroundingsCachedAt = level.getGameTime();
    }

    @Override
    public PipeKind kind() {
        return pipe().kind();
    }

    @Override
    public boolean hasInventory() {
        return !inventorySides().isEmpty();
    }

    @Override
    public Map<Side, InventoryRef> inventories() {
        Map<Side, InventoryRef> inventories = new EnumMap<>(Side.class);
        for (Direction side : inventorySides()) {
            inventories.put(Directions.toDomain(side), inventoryRef(worldPosition.relative(side)));
        }
        return inventories;
    }

    // both halves of a double chest are the same inventory
    private InventoryRef inventoryRef(BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof ChestBlock && state.getValue(ChestBlock.TYPE) != ChestType.SINGLE) {
            BlockPos other = pos.relative(ChestBlock.getConnectedDirection(state));
            return new InventoryRef(Math.min(pos.asLong(), other.asLong()));
        }
        return new InventoryRef(pos.asLong());
    }

    @Override
    public Set<Side> inventoriesAccepting(ItemBatch batch) {
        Set<Side> sides = EnumSet.noneOf(Side.class);
        ItemStack stack = McItemType.stackOf(batch);
        for (Direction side : inventorySides()) {
            ItemStack rest = insertThrough(worldPosition.relative(side), insertFaces(side, batch.type(), false), stack, true);
            if (rest.getCount() < stack.getCount()) {
                sides.add(Directions.toDomain(side));
            }
        }
        return sides;
    }

    // looked up at most once per tick, sinks that depend on it get asked for every passing item
    @Override
    public Map<ItemType, Integer> contents() {
        if (contentsCache == null || contentsCachedAt != level.getGameTime()) {
            Map<ItemType, Integer> contents = new LinkedHashMap<>();
            for (Direction side : inventorySides()) {
                for (ItemStack stack : storage().contents(level, worldPosition.relative(side), accessFace(side))) {
                    contents.merge(McItemType.batchOf(stack).type(), stack.getCount(), Integer::sum);
                }
            }
            contentsCache = contents;
            contentsCachedAt = level.getGameTime();
        }
        return new LinkedHashMap<>(contentsCache);
    }

    @Override
    public Map<ItemType, Integer> held() {
        if (heldCache == null || heldCachedAt != level.getGameTime()) {
            Map<ItemType, Integer> held = new LinkedHashMap<>();
            for (Direction side : inventorySides()) {
                for (ItemStack stack : storage().held(level, worldPosition.relative(side), accessFace(side))) {
                    if (!stack.isEmpty()) {
                        held.merge(McItemType.batchOf(stack).type(), stack.getCount(), Integer::sum);
                    }
                }
            }
            heldCache = held;
            heldCachedAt = level.getGameTime();
        }
        return new LinkedHashMap<>(heldCache);
    }

    @Override
    public int send(ItemType type, int count, RouterId destination, TransportMode mode) {
        ItemStack match = McItemType.stackOf(new ItemBatch(type, 1));
        int remaining = count;
        for (Direction side : inventorySides()) {
            while (remaining > 0) {
                ItemStack taken = storage().extract(level, worldPosition.relative(side), accessFace(side), match,
                        Math.min(remaining, match.getMaxStackSize()));
                if (taken.isEmpty()) {
                    break;
                }
                transport.insert(new TravellingItem(McItemType.batchOf(taken), Directions.toDomain(side), destination,
                        destination == null ? TransportMode.UNCLAIMED : mode));
                remaining -= taken.getCount();
            }
        }
        if (remaining < count) {
            contentsCache = null;
            setChanged();
        }
        return count - remaining;
    }

    @Override
    public boolean provides() {
        return kind() == PipeKind.PROVIDER || hasModule(ModuleKind.PROVIDER);
    }

    @Override
    public List<PowerSource> powerSources() {
        if (junctionsCache == null || surroundingsStale()) {
            lookAround();
        }
        List<PowerSource> sources = new ArrayList<>();
        for (BlockPos target : junctionsCache) {
            long key = target.asLong();
            sources.add(new PowerSource() {
                @Override
                public Object key() {
                    return key;
                }

                @Override
                public int draw(int amount, boolean simulate) {
                    return level.getBlockEntity(target) instanceof PowerJunctionBlockEntity junction
                            ? junction.draw(amount, simulate) : 0;
                }
            });
        }
        return sources;
    }

    @Override
    public int drawPower(int amount, boolean simulate) {
        if (junctionsCache == null || surroundingsStale()) {
            lookAround();
        }
        int drawn = 0;
        for (BlockPos target : junctionsCache) {
            if (drawn < amount && level.getBlockEntity(target) instanceof PowerJunctionBlockEntity junction) {
                drawn += junction.draw(amount - drawn, simulate);
            }
        }
        return drawn;
    }

    @Override
    public List<Extraction> extractions() {
        if (extractionsCache == null) {
            List<Extraction> extractions = new ArrayList<>();
            for (int slot = 0; slot < pipe().moduleSlots(); slot++) {
                int module = slot;
                ModuleItem.extractionOf(modules.getItem(slot)).ifPresent(extraction -> extractions.add(extraction.with(
                        moduleUpgradeCount(module, UpgradeKind.ACTION_SPEED), moduleUpgradeCount(module, UpgradeKind.ITEM_EXTRACTION),
                        moduleUpgradeCount(module, UpgradeKind.ITEM_STACK_EXTRACTION))));
            }
            extractionsCache = extractions.stream().distinct().toList();
        }
        return extractionsCache;
    }

    @Override
    public List<SinkRule> sinkRules() {
        if (sinkRulesCache == null) {
            if (kind() == PipeKind.BASIC) {
                ItemSink sink = new ItemSink(supplyTargets().keySet(), defaultRoute);
                // a basic pipe with nothing listed and no default route takes nothing
                sinkRulesCache = sink.filter().isEmpty() && !sink.defaultRoute() ? List.of() : List.of(sink);
                List<FuzzyType> fuzzy = hasUpgrade(UpgradeKind.FUZZY)
                        ? slotSettings.fuzzyTypes(config.getItems().subList(0, OUTPUT_SLOT)) : List.of();
                if (!fuzzy.isEmpty()) {
                    sinkRulesCache = List.of(sink, new FuzzySink(fuzzy));
                }
            } else {
                List<SinkRule> rules = new ArrayList<>();
                for (int slot = 0; slot < pipe().moduleSlots(); slot++) {
                    rules.addAll(moduleRules(slot));
                }
                sinkRulesCache = rules;
            }
        }
        return sinkRulesCache;
    }

    @Override
    public Optional<Recipe> recipe() {
        ItemStack output = config.getItem(OUTPUT_SLOT);
        List<ItemBatch> inputs = new ArrayList<>();
        for (int slot = 0; slot < OUTPUT_SLOT; slot++) {
            if (!config.getItem(slot).isEmpty()) {
                inputs.add(marked(slot));
            }
        }
        if (output.isEmpty() || inputs.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Recipe(inputs, marked(OUTPUT_SLOT)));
    }

    // with a satellite chosen the bottom row of the recipe goes there, the rest to this pipe
    @Override
    public List<ItemBatch> satelliteInputs() {
        List<ItemBatch> inputs = new ArrayList<>();
        for (int slot = SATELLITE_ROW; slot < OUTPUT_SLOT && !satelliteName.isEmpty(); slot++) {
            if (!config.getItem(slot).isEmpty()) {
                inputs.add(marked(slot));
            }
        }
        return inputs;
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
        return satelliteName;
    }

    @Override
    public Map<ItemType, Integer> supplyTargets() {
        Map<ItemType, Integer> targets = new LinkedHashMap<>();
        for (int slot = 0; slot < OUTPUT_SLOT; slot++) {
            ItemStack stack = config.getItem(slot);
            if (!stack.isEmpty()) {
                targets.merge(McItemType.batchOf(stack).type(), stack.getCount(), Integer::sum);
            }
        }
        return targets;
    }

    void dropContents() {
        Containers.dropContents(level, worldPosition, modules);
        Containers.dropContents(level, worldPosition, upgrades);
        Containers.dropContents(level, worldPosition, moduleUpgrades);
        Containers.dropContents(level, worldPosition, sneakyRow);
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
            }
        }
    }

    @Override
    public void setRemoved() {
        if (announced && level != null && !level.isClientSide && pipe().routed()) {
            network().removeRouter(routerId, level.getGameTime());
        }
        super.setRemoved();
    }

    // the item of a saved order, with the fuzzy flags of the result slot it was placed for
    private static ItemType orderedType(ItemStack stack, int fuzzy) {
        ItemType type = McItemType.batchOf(stack).type();
        return fuzzy == 0 ? type : new FuzzyType(type, FuzzyFlags.of(fuzzy));
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
        tag.putString("Satellite", satelliteName);
        tag.putBoolean("DefaultRoute", defaultRoute);
        tag.putInt("Mode", mode);
        tag.putInt("Priority", priority);
        ListTag slotNames = new ListTag();
        for (String name : slotSatellites) {
            slotNames.add(StringTag.valueOf(name));
        }
        tag.put("SlotSatellites", slotNames);
        tag.putInt("Disconnected", disconnected);
        if (firewall != null) {
            tag.put("Firewall", firewall.save(registries));
        }
        if (table != null) {
            tag.put("Table", table.save(registries));
        }
        if (drive != null) {
            tag.put("Disk", ContainerTags.save(drive.slot(), registries));
        }
        if (connector != null) {
            tag.put("Connector", connector.save(registries));
        }
        if (frequency != null) {
            tag.put("Frequency", frequency.save(registries));
        }
        tag.putBoolean("Exclude", exclude);
        if (pointed != null) {
            tag.putInt("Pointed", pointed.get3DDataValue());
        }
        tag.put("Config", ContainerTags.save(config, registries));
        tag.put("Modules", ContainerTags.save(modules, registries));
        tag.put("Upgrades", ContainerTags.save(upgrades, registries));
        tag.put("ModuleUpgrades", ContainerTags.save(moduleUpgrades, registries));
        tag.put("SneakyUpgrades", ContainerTags.save(sneakyRow, registries));
        tag.put("SlotSettings", slotSettings.save());
        tag.put("FluidCrafting", fluidCrafting.save());
        ListTag orders = new ListTag();
        for (OutputOrder order : outputOrders.all()) {
            CompoundTag entry = new CompoundTag();
            if (!order.isExtra()) {
                entry.putUUID("Destination", order.destination().value());
            }
            entry.put("Stack", McItemType.stackOf(new ItemBatch(order.batch().type(), 1)).save(registries));
            if (order.batch().type() instanceof FuzzyType fuzzy) {
                entry.putInt("Fuzzy", fuzzy.flags().bits());
            }
            entry.putInt("Count", order.batch().count());
            entry.putLong("Since", order.since());
            orders.add(entry);
        }
        tag.put("Orders", orders);
        if (fluids != null) {
            tag.put("Fluids", fluids.save(registries));
        }
        if (power.buffered() > 0) {
            tag.putInt("Power", power.buffered());
        }
        tag.putLongArray("Statistics", StatisticsTags.save(statistics));
        tag.put("Security", security.save(registries));
        if (signs != null) {
            tag.put("Signs", signs.save(registries, false));
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.hasUUID("Router")) {
            routerId = new RouterId(tag.getUUID("Router"));
        }
        TransportTags.load(transport, tag.getList("Items", Tag.TAG_COMPOUND), registries);
        opaqueOnClient = tag.getBoolean("Opaque");
        // updates for clients carry only the items, and commands may set any part alone, so every part is optional
        if (tag.contains("Satellite")) {
            // worlds from before satellites had names kept a number here
            satelliteName = tag.contains("Satellite", Tag.TAG_STRING) ? tag.getString("Satellite")
                    : SatelliteName.ofNumber(tag.getInt("Satellite"));
        }
        if (tag.contains("DefaultRoute")) {
            defaultRoute = tag.getBoolean("DefaultRoute");
        }
        if (tag.contains("Mode")) {
            mode = tag.getInt("Mode");
        }
        if (table != null && tag.contains("Table")) {
            table.load(tag.getCompound("Table"), registries);
        }
        if (drive != null && tag.contains("Disk")) {
            ContainerTags.load(drive.slot(), tag.getCompound("Disk"), registries);
        }
        if (frequency != null && tag.contains("Frequency")) {
            frequency.load(tag.getCompound("Frequency"), registries);
        }
        if (connector != null && tag.contains("Connector")) {
            connector.load(tag.getCompound("Connector"), registries);
        }
        if (firewall != null && tag.contains("Firewall")) {
            firewall.load(tag.getCompound("Firewall"), registries);
        }
        if (tag.contains("Disconnected")) {
            disconnected = tag.getInt("Disconnected");
        }
        ListTag slotNames = tag.getList("SlotSatellites", Tag.TAG_STRING);
        int[] slotNumbers = tag.getIntArray("SlotSatellites");
        for (int slot = 0; slot < slotSatellites.length; slot++) {
            if (slot < slotNames.size()) {
                slotSatellites[slot] = slotNames.getString(slot);
            } else if (slot < slotNumbers.length) {
                slotSatellites[slot] = SatelliteName.ofNumber(slotNumbers[slot]);
            }
        }
        if (tag.contains("Priority")) {
            priority = tag.getInt("Priority");
        }
        if (tag.contains("Exclude")) {
            exclude = tag.getBoolean("Exclude");
        }
        if (tag.contains("Pointed")) {
            pointed = Direction.from3DDataValue(tag.getInt("Pointed"));
        }
        if (tag.contains("Config")) {
            ContainerTags.load(config, tag.getCompound("Config"), registries);
        }
        if (tag.contains("Modules")) {
            ContainerTags.load(modules, tag.getCompound("Modules"), registries);
        }
        if (tag.contains("ModuleUpgrades")) {
            ContainerTags.load(moduleUpgrades, tag.getCompound("ModuleUpgrades"), registries);
        }
        if (tag.contains("SlotSettings")) {
            slotSettings.load(tag.getCompound("SlotSettings"));
        }
        if (tag.contains("FluidCrafting")) {
            fluidCrafting.load(tag.getCompound("FluidCrafting"));
        }
        if (tag.contains("SneakyUpgrades")) {
            ContainerTags.load(sneakyRow, tag.getCompound("SneakyUpgrades"), registries);
        }
        if (tag.contains("Upgrades")) {
            ContainerTags.load(upgrades, tag.getCompound("Upgrades"), registries);
        }
        if (tag.contains("Power")) {
            power.load(tag.getInt("Power"));
        }
        StatisticsTags.load(statistics, tag.getLongArray("Statistics"));
        if (tag.contains("Security")) {
            security.load(tag.getCompound("Security"), registries);
        }
        if (fluids != null && tag.contains("Fluids")) {
            fluids.load(tag.getCompound("Fluids"), registries);
        }
        if (signs != null && tag.contains("Signs")) {
            signs.load(tag.getCompound("Signs"), registries);
        }
        ListTag orders = tag.getList("Orders", Tag.TAG_COMPOUND);
        for (int index = 0; index < orders.size(); index++) {
            CompoundTag entry = orders.getCompound(index);
            ItemStack.parse(registries, entry.getCompound("Stack")).filter(stack -> !stack.isEmpty())
                    .ifPresent(stack -> outputOrders.add(new OutputOrder(
                            entry.hasUUID("Destination") ? new RouterId(entry.getUUID("Destination")) : null,
                            new ItemBatch(orderedType(stack, entry.getInt("Fuzzy")), entry.getInt("Count")), entry.getLong("Since"))));
        }
    }
}
