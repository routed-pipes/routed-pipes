package logisticspipes.adapter.minecraft;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import logisticspipes.adapter.minecraft.menu.ChassisMenu;
import logisticspipes.adapter.minecraft.menu.PipeConfigMenu;
import logisticspipes.adapter.minecraft.menu.RequestMenu;
import logisticspipes.application.LogisticsNetwork;
import logisticspipes.application.RequestOutcome;
import logisticspipes.application.RouterStatus;
import logisticspipes.application.port.RouterNode;
import logisticspipes.domain.crafting.OutputOrder;
import logisticspipes.domain.crafting.OutputOrders;
import logisticspipes.domain.crafting.Recipe;
import logisticspipes.domain.item.ItemBatch;
import logisticspipes.domain.item.ItemType;
import logisticspipes.domain.logistics.ExtractionMode;
import logisticspipes.domain.logistics.InventoryRef;
import logisticspipes.domain.logistics.ItemSink;
import logisticspipes.domain.logistics.ModuleKind;
import logisticspipes.domain.logistics.PipeKind;
import logisticspipes.domain.logistics.SinkRule;
import logisticspipes.domain.routing.RouterId;
import logisticspipes.domain.routing.Side;
import logisticspipes.domain.transport.Departure;
import logisticspipes.domain.transport.PipeTransport;
import logisticspipes.domain.transport.TravellingItem;
import logisticspipes.domain.transport.UnroutedPipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.ChestType;

public class PipeBlockEntity extends BlockEntity implements RouterNode {

    // settings slots: 0-8 are recipe inputs or supplier targets, 9 is the recipe output
    public static final int CONFIG_SLOTS = 10;
    public static final int OUTPUT_SLOT = 9;
    // first slot of the recipe row that is delivered to the satellite
    public static final int SATELLITE_ROW = 6;
    // the largest chassis has this many
    public static final int MODULE_SLOTS = 8;
    public static final int UPGRADE_SLOTS = 4;
    // capabilities of a neighbour can change without its block changing, so nothing about it is trusted for longer
    private static final int SURROUNDINGS_MAX_AGE = 40;
    // every speed upgrade makes the pipe send items on this much faster, in percent
    private static final int SPEED_UPGRADE_PERCENT = 25;

    private final PipeTransport transport = new PipeTransport();
    private final SimpleContainer config = new SimpleContainer(CONFIG_SLOTS);
    private final SimpleContainer modules = new SimpleContainer(MODULE_SLOTS);
    private final SimpleContainer upgrades = new SimpleContainer(UPGRADE_SLOTS);
    private final OutputOrders outputOrders = new OutputOrders();
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return index == PipeConfigMenu.DATA_DEFAULT_ROUTE ? (defaultRoute ? 1 : 0) : satelliteId;
        }

        @Override
        public void set(int index, int value) {
            if (index == PipeConfigMenu.DATA_DEFAULT_ROUTE) {
                defaultRoute = value != 0;
            } else {
                satelliteId = value;
            }
            settingsChanged();
        }

        @Override
        public int getCount() {
            return PipeConfigMenu.DATA_COUNT;
        }
    };
    private RouterId routerId = new RouterId(UUID.randomUUID());
    private int satelliteId;
    private boolean defaultRoute;
    private boolean announced;
    private LogisticsNetwork network;
    private Map<ItemType, Integer> contentsCache;
    // worked out from modules and settings when first asked for, dropped when those change
    private List<SinkRule> sinkRulesCache;
    private List<ExtractionMode> extractionsCache;
    // what is next to the pipe, looked up again after a neighbour changed and every couple of seconds anyway
    private List<Direction> inventorySidesCache;
    private List<BlockPos> junctionsCache;
    private long surroundingsCachedAt;
    private long contentsCachedAt;
    private boolean linksDirty;

    public PipeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        config.addListener(container -> settingsChanged());
        modules.addListener(container -> settingsChanged());
        upgrades.addListener(container -> {
            transport.setBoost(boost());
            neighbourChanged();
            setChanged();
        });
    }

    // filters, modules or the default route changed: forget what was derived from them and tell the network
    private void settingsChanged() {
        sinkRulesCache = null;
        extractionsCache = null;
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
    }

    private boolean surroundingsStale() {
        return level.getGameTime() - surroundingsCachedAt >= SURROUNDINGS_MAX_AGE;
    }

    RouterId routerId() {
        return routerId;
    }

    void markLinksDirty() {
        linksDirty = true;
    }

    private LogisticsPipeBlock pipe() {
        return (LogisticsPipeBlock) getBlockState().getBlock();
    }

    private LogisticsNetwork network() {
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

    public Container config() {
        return config;
    }

    public Container modules() {
        return modules;
    }

    // puts one upgrade from the stack into the pipe, false when there is no room
    public boolean installUpgrade(ItemStack stack) {
        if (!pipe().routed() || !(stack.getItem() instanceof UpgradeItem)) {
            return false;
        }
        for (int slot = 0; slot < UPGRADE_SLOTS; slot++) {
            if (upgrades.getItem(slot).isEmpty()) {
                upgrades.setItem(slot, stack.split(1));
                return true;
            }
        }
        return false;
    }

    // takes all upgrades out again
    public List<ItemStack> removeUpgrades() {
        return upgrades.removeAllItems();
    }

    private int upgradeCount(UpgradeItem.Kind kind) {
        int count = 0;
        for (int slot = 0; slot < UPGRADE_SLOTS; slot++) {
            if (upgrades.getItem(slot).getItem() instanceof UpgradeItem upgrade && upgrade.kind() == kind) {
                count++;
            }
        }
        return count;
    }

    // how fast this pipe sends items on, in percent of their normal speed, plain pipes do not change it
    private Integer boost() {
        if (!pipe().routed()) {
            return null;
        }
        return 100 + SPEED_UPGRADE_PERCENT * upgradeCount(UpgradeItem.Kind.SPEED);
    }

    // the face of an inventory this pipe uses when the inventory is on the given side of the pipe
    private Direction accessFace(Direction side) {
        for (int slot = 0; slot < UPGRADE_SLOTS; slot++) {
            ItemStack stack = upgrades.getItem(slot);
            if (stack.getItem() instanceof UpgradeItem upgrade && upgrade.kind() == UpgradeItem.Kind.SNEAKY) {
                return UpgradeItem.sideOf(stack);
            }
        }
        return side.getOpposite();
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

    public void setSatelliteId(int id) {
        data.set(PipeConfigMenu.DATA_SATELLITE, id);
    }

    public void setDefaultRoute(boolean enabled) {
        data.set(PipeConfigMenu.DATA_DEFAULT_ROUTE, enabled ? 1 : 0);
    }

    // takes a stack coming in from outside through the given side
    public void accept(ItemStack stack, Direction side) {
        transport.insert(new TravellingItem(McItemType.batchOf(stack), Directions.toDomain(side), null));
        setChanged();
    }

    // empty for plain pipes and on the client
    public Optional<RouterStatus> networkStatus() {
        if (level == null || level.isClientSide || !pipe().routed()) {
            return Optional.empty();
        }
        return Optional.of(network().status(routerId));
    }

    public Map<ItemType, Integer> available() {
        return network().available(routerId);
    }

    public RequestOutcome request(ItemBatch wanted) {
        return network().request(routerId, wanted, level.getGameTime());
    }

    // remote means the player does not have to stand next to the pipe
    public void openRequestScreen(Player player, boolean remote) {
        player.openMenu(requestScreen(remote));
    }

    private MenuProvider requestScreen(boolean remote) {
        return new SimpleMenuProvider((id, inventory, player) -> new RequestMenu(pipe().services().requestMenu().get(), id,
                this, user -> !isRemoved() && (remote || Container.stillValidBlockEntity(this, user))),
                getBlockState().getBlock().getName());
    }

    MenuProvider menuProvider() {
        Component title = getBlockState().getBlock().getName();
        PipeServices services = pipe().services();
        if (kind() == PipeKind.REQUEST) {
            return requestScreen(false);
        }
        if (kind() == PipeKind.CHASSIS) {
            ContainerData slots = new SimpleContainerData(1);
            slots.set(0, pipe().moduleSlots());
            return new SimpleMenuProvider((id, inventory, player) -> new ChassisMenu(services.chassisMenu().get(), id,
                    inventory, modules, slots, user -> Container.stillValidBlockEntity(this, user)), title);
        }
        return new SimpleMenuProvider((id, inventory, player) -> new PipeConfigMenu(services.configMenu(kind()), id,
                inventory, kind(), config, data, user -> Container.stillValidBlockEntity(this, user), this::importRecipe), title);
    }

    // copies the recipe of a crafting table next to the pipe into the pipe settings
    public void importRecipe() {
        for (Direction side : Direction.values()) {
            BlockPos target = worldPosition.relative(side);
            if (level.isLoaded(target) && level.getBlockEntity(target) instanceof CraftingTableBlockEntity table) {
                for (int slot = 0; slot < OUTPUT_SLOT; slot++) {
                    config.setItem(slot, table.pattern().getItem(slot).copy());
                }
                config.setItem(OUTPUT_SLOT, table.result());
                return;
            }
        }
    }

    // moves items on without waiting for the server, so they do not blink at pipe borders
    void clientTick() {
        for (Departure departure : transport.advance(level.getGameTime())) {
            Side exit = departure.exit().orElseThrow();
            BlockPos target = worldPosition.relative(Directions.toMinecraft(exit));
            if (level.getBlockEntity(target) instanceof PipeBlockEntity neighbour) {
                neighbour.transport.expect(departure.item().enteringFrom(exit.opposite()));
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
        if (linksDirty && pipe().routed()) {
            linksDirty = false;
            network().updateLinks(routerId, PipeTopology.discoverLinks(level, worldPosition), level.getGameTime());
        }
        // a chassis that only takes items in has no upkeep
        if (kind() == PipeKind.CRAFTING || kind() == PipeKind.SUPPLIER
                || kind() == PipeKind.CHASSIS && !extractions().isEmpty()) {
            network().tick(routerId, level.getGameTime());
        }
        if (!transport.isEmpty()) {
            for (Departure departure : transport.tick(level.getGameTime(), this::chooseExit)) {
                depart(departure);
            }
            setChanged();
        }
        if (transport.pollChanged()) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private Optional<Side> chooseExit(TravellingItem item) {
        if (pipe().routed()) {
            return network().chooseExit(routerId, item, level.getGameTime());
        }
        Set<Side> connections = EnumSet.noneOf(Side.class);
        for (Direction side : Direction.values()) {
            if (getBlockState().getValue(LogisticsPipeBlock.CONNECTED.get(side))) {
                connections.add(Directions.toDomain(side));
            }
        }
        return UnroutedPipe.chooseExit(connections, item, router -> PipeTopology
                .branchTowards(level, worldPosition, router).map(Directions::toDomain));
    }

    private void depart(Departure departure) {
        TravellingItem item = departure.item();
        ItemStack stack = McItemType.stackOf(item.batch());
        if (departure.exit().isEmpty()) {
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
        if (level.getBlockEntity(target) instanceof PipeBlockEntity neighbour) {
            neighbour.transport.insert(item.enteringFrom(exit.opposite()));
            neighbour.setChanged();
            // tell clients now, a neighbour that already ticked would only do so a tick later and the item would blink
            level.sendBlockUpdated(target, neighbour.getBlockState(), neighbour.getBlockState(), Block.UPDATE_CLIENTS);
            return;
        }
        ItemStack rest = storage().exists(level, target, accessFace(side))
                ? storage().insert(level, target, accessFace(side), stack, false)
                : stack;
        contentsCache = null;
        if (rest.isEmpty()) {
            return;
        }
        if (pipe().routed() && kind() != PipeKind.BASIC && item.destination().filter(routerId::equals).isPresent()) {
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
    private void popOut(ItemStack stack) {
        ItemEntity item = new ItemEntity(level, worldPosition.getX() + 0.5, worldPosition.getY() + 0.8,
                worldPosition.getZ() + 0.5, stack, 0, 0, 0);
        item.setDefaultPickUpDelay();
        level.addFreshEntity(item);
    }

    // sides that touch an inventory
    private List<Direction> inventorySides() {
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
            } else if (getBlockState().getValue(LogisticsPipeBlock.CONNECTED.get(side))
                    && !(level.getBlockState(target).getBlock() instanceof LogisticsPipeBlock)
                    && storage().exists(level, target, accessFace(side))) {
                sides.add(side);
            }
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
            ItemStack rest = storage().insert(level, worldPosition.relative(side), accessFace(side), stack, true);
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
    public int send(ItemType type, int count, RouterId destination) {
        ItemStack match = McItemType.stackOf(new ItemBatch(type, 1));
        int remaining = count;
        for (Direction side : inventorySides()) {
            while (remaining > 0) {
                ItemStack taken = storage().extract(level, worldPosition.relative(side), accessFace(side), match,
                        Math.min(remaining, match.getMaxStackSize()));
                if (taken.isEmpty()) {
                    break;
                }
                transport.insert(new TravellingItem(McItemType.batchOf(taken), Directions.toDomain(side), destination));
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
    public List<ExtractionMode> extractions() {
        if (extractionsCache == null) {
            extractionsCache = pluggedModules().stream().flatMap(stack -> ModuleItem.extractionOf(stack).stream()).distinct().toList();
        }
        return extractionsCache;
    }

    // every speed upgrade doubles what an extractor moves
    @Override
    public int extractionBoost() {
        return 1 << upgradeCount(UpgradeItem.Kind.SPEED);
    }

    @Override
    public List<SinkRule> sinkRules() {
        if (sinkRulesCache == null) {
            if (kind() == PipeKind.BASIC) {
                ItemSink sink = new ItemSink(supplyTargets().keySet(), defaultRoute);
                // a basic pipe with nothing listed and no default route takes nothing
                sinkRulesCache = sink.filter().isEmpty() && !sink.defaultRoute() ? List.of() : List.of(sink);
            } else {
                sinkRulesCache = pluggedModules().stream().flatMap(stack -> ModuleItem.ruleOf(stack).stream()).toList();
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
                inputs.add(McItemType.batchOf(config.getItem(slot)));
            }
        }
        if (output.isEmpty() || inputs.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Recipe(inputs, McItemType.batchOf(output)));
    }

    // with a satellite chosen the bottom row of the recipe goes there, the rest to this pipe
    @Override
    public List<ItemBatch> satelliteInputs() {
        List<ItemBatch> inputs = new ArrayList<>();
        for (int slot = SATELLITE_ROW; slot < OUTPUT_SLOT && satelliteId != 0; slot++) {
            if (!config.getItem(slot).isEmpty()) {
                inputs.add(McItemType.batchOf(config.getItem(slot)));
            }
        }
        return inputs;
    }

    @Override
    public OutputOrders outputOrders() {
        return outputOrders;
    }

    @Override
    public int satelliteId() {
        return satelliteId;
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
        for (TravellingItem item : transport.clear()) {
            Block.popResource(level, worldPosition, McItemType.stackOf(item.batch()));
        }
    }

    @Override
    public void setRemoved() {
        if (announced && level != null && !level.isClientSide && pipe().routed()) {
            network().removeRouter(routerId, level.getGameTime());
        }
        super.setRemoved();
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // clients only need the items for drawing them
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.put("Items", TransportTags.save(transport, registries));
        return tag;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putUUID("Router", routerId.value());
        tag.put("Items", TransportTags.save(transport, registries));
        tag.putInt("Satellite", satelliteId);
        tag.putBoolean("DefaultRoute", defaultRoute);
        tag.put("Config", ContainerTags.save(config, registries));
        tag.put("Modules", ContainerTags.save(modules, registries));
        tag.put("Upgrades", ContainerTags.save(upgrades, registries));
        ListTag orders = new ListTag();
        for (OutputOrder order : outputOrders.all()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Destination", order.destination().value());
            entry.put("Stack", McItemType.stackOf(new ItemBatch(order.batch().type(), 1)).save(registries));
            entry.putInt("Count", order.batch().count());
            entry.putLong("Since", order.since());
            orders.add(entry);
        }
        tag.put("Orders", orders);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.hasUUID("Router")) {
            routerId = new RouterId(tag.getUUID("Router"));
        }
        TransportTags.load(transport, tag.getList("Items", Tag.TAG_COMPOUND), registries);
        // updates for clients carry only the items, and commands may set any part alone, so every part is optional
        if (tag.contains("Satellite")) {
            satelliteId = tag.getInt("Satellite");
        }
        if (tag.contains("DefaultRoute")) {
            defaultRoute = tag.getBoolean("DefaultRoute");
        }
        if (tag.contains("Config")) {
            ContainerTags.load(config, tag.getCompound("Config"), registries);
        }
        if (tag.contains("Modules")) {
            ContainerTags.load(modules, tag.getCompound("Modules"), registries);
        }
        if (tag.contains("Upgrades")) {
            ContainerTags.load(upgrades, tag.getCompound("Upgrades"), registries);
        }
        ListTag orders = tag.getList("Orders", Tag.TAG_COMPOUND);
        for (int index = 0; index < orders.size(); index++) {
            CompoundTag entry = orders.getCompound(index);
            ItemStack.parse(registries, entry.getCompound("Stack")).filter(stack -> !stack.isEmpty())
                    .ifPresent(stack -> outputOrders.add(new OutputOrder(new RouterId(entry.getUUID("Destination")),
                            new ItemBatch(McItemType.batchOf(stack).type(), entry.getInt("Count")), entry.getLong("Since"))));
        }
    }
}
