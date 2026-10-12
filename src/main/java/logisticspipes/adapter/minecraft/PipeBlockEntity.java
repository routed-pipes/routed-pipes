package logisticspipes.adapter.minecraft;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
import java.util.UUID;

import logisticspipes.adapter.minecraft.menu.PipeConfigMenu;
import logisticspipes.adapter.minecraft.menu.RequestMenu;
import logisticspipes.application.LogisticsNetwork;
import logisticspipes.application.port.RouterNode;
import logisticspipes.domain.crafting.OutputOrder;
import logisticspipes.domain.crafting.OutputOrders;
import logisticspipes.domain.crafting.Recipe;
import logisticspipes.domain.item.ItemBatch;
import logisticspipes.domain.item.ItemType;
import logisticspipes.domain.logistics.ItemSink;
import logisticspipes.domain.logistics.PipeKind;
import logisticspipes.domain.request.RequestPlan;
import logisticspipes.domain.routing.RouterId;
import logisticspipes.domain.routing.Side;
import logisticspipes.domain.transport.Departure;
import logisticspipes.domain.transport.PipeTransport;
import logisticspipes.domain.transport.TravellingItem;
import logisticspipes.domain.transport.UnroutedPipe;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class PipeBlockEntity extends BlockEntity implements RouterNode {

    // settings slots: 0-8 are recipe inputs or supplier targets, 9 is the recipe output
    public static final int CONFIG_SLOTS = 10;
    public static final int OUTPUT_SLOT = 9;
    private static final int UPKEEP_INTERVAL = 20;

    private final PipeTransport transport = new PipeTransport();
    private final SimpleContainer config = new SimpleContainer(CONFIG_SLOTS);
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
            setChanged();
        }

        @Override
        public int getCount() {
            return PipeConfigMenu.DATA_COUNT;
        }
    };
    private RouterId routerId = new RouterId(UUID.randomUUID());
    private int satelliteId;
    private boolean defaultRoute = true;
    private boolean announced;
    private boolean linksDirty;

    public PipeBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
        config.addListener(container -> setChanged());
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
        return pipe().services().networks().of(level);
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

    public Map<ItemType, Integer> available() {
        return network().available(routerId);
    }

    public RequestPlan request(ItemBatch wanted) {
        return network().request(routerId, wanted, level.getGameTime());
    }

    MenuProvider menuProvider() {
        Component title = getBlockState().getBlock().getName();
        PipeServices services = pipe().services();
        if (kind() == PipeKind.REQUEST) {
            return new SimpleMenuProvider(
                    (id, inventory, player) -> new RequestMenu(services.requestMenu().get(), id, this), title);
        }
        return new SimpleMenuProvider((id, inventory, player) -> new PipeConfigMenu(services.configMenu(kind()), id,
                inventory, kind(), config, data, user -> Container.stillValidBlockEntity(this, user), this::importRecipe), title);
    }

    // copies the recipe of a crafting table next to the pipe into the pipe settings
    public void importRecipe() {
        for (Direction side : Direction.values()) {
            if (level.getBlockEntity(worldPosition.relative(side)) instanceof CraftingTableBlockEntity table) {
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
        for (Departure departure : transport.advance()) {
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
            pipe().refreshConnections(getBlockState(), level, worldPosition);
            if (pipe().routed()) {
                network().addRouter(routerId, this);
            }
            PipeTopology.markRoutersDirty(level, worldPosition);
        }
        if (linksDirty && pipe().routed()) {
            linksDirty = false;
            network().updateLinks(routerId, PipeTopology.discoverLinks(level, worldPosition));
        }
        if ((kind() == PipeKind.CRAFTING || kind() == PipeKind.SUPPLIER)
                && Math.floorMod(level.getGameTime() + worldPosition.asLong(), UPKEEP_INTERVAL) == 0) {
            network().tick(routerId, level.getGameTime());
            setChanged();
        }
        if (!transport.isEmpty()) {
            for (Departure departure : transport.tick(this::chooseExit)) {
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
            return network().chooseExit(routerId, item);
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
            Block.popResource(level, worldPosition, stack);
            return;
        }
        Side exit = departure.exit().get();
        Direction side = Directions.toMinecraft(exit);
        BlockPos target = worldPosition.relative(side);
        if (!level.isLoaded(target)) {
            // wait at the chunk border and try again next tick
            transport.insert(TravellingItem.restore(item.id(), item.batch(), item.origin(),
                    item.destination().orElse(null), item.nextHop().orElse(null), exit, PipeTransport.TICKS_PER_PIPE - 1));
            return;
        }
        if (level.getBlockEntity(target) instanceof PipeBlockEntity neighbour) {
            neighbour.transport.insert(item.enteringFrom(exit.opposite()));
            neighbour.setChanged();
            return;
        }
        ItemStack rest = storage().exists(level, target, side.getOpposite())
                ? storage().insert(level, target, side.getOpposite(), stack, false)
                : stack;
        if (rest.isEmpty()) {
            return;
        }
        if (pipe().routed()) {
            // did not fit after all, send it somewhere else
            transport.insert(new TravellingItem(McItemType.batchOf(rest), exit, null));
        } else {
            Block.popResource(level, worldPosition, rest);
        }
    }

    // sides that touch an inventory
    private List<Direction> inventorySides() {
        List<Direction> sides = new ArrayList<>();
        for (Direction side : Direction.values()) {
            BlockPos target = worldPosition.relative(side);
            if (getBlockState().getValue(LogisticsPipeBlock.CONNECTED.get(side))
                    && !(level.getBlockState(target).getBlock() instanceof LogisticsPipeBlock)
                    && storage().exists(level, target, side.getOpposite())) {
                sides.add(side);
            }
        }
        return sides;
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
    public Set<Side> inventoriesAccepting(ItemBatch batch) {
        Set<Side> sides = EnumSet.noneOf(Side.class);
        ItemStack stack = McItemType.stackOf(batch);
        for (Direction side : inventorySides()) {
            ItemStack rest = storage().insert(level, worldPosition.relative(side), side.getOpposite(), stack, true);
            if (rest.getCount() < stack.getCount()) {
                sides.add(Directions.toDomain(side));
            }
        }
        return sides;
    }

    @Override
    public Map<ItemType, Integer> contents() {
        Map<ItemType, Integer> contents = new LinkedHashMap<>();
        for (Direction side : inventorySides()) {
            for (ItemStack stack : storage().contents(level, worldPosition.relative(side), side.getOpposite())) {
                contents.merge(McItemType.batchOf(stack).type(), stack.getCount(), Integer::sum);
            }
        }
        return contents;
    }

    @Override
    public int send(ItemType type, int count, RouterId destination) {
        ItemStack match = McItemType.stackOf(new ItemBatch(type, 1));
        int remaining = count;
        for (Direction side : inventorySides()) {
            while (remaining > 0) {
                ItemStack taken = storage().extract(level, worldPosition.relative(side), side.getOpposite(), match,
                        Math.min(remaining, match.getMaxStackSize()));
                if (taken.isEmpty()) {
                    break;
                }
                transport.insert(new TravellingItem(McItemType.batchOf(taken), Directions.toDomain(side), destination));
                remaining -= taken.getCount();
            }
        }
        if (remaining < count) {
            setChanged();
        }
        return count - remaining;
    }

    @Override
    public OptionalInt sinkPriority(ItemType type) {
        if (kind() != PipeKind.BASIC) {
            return OptionalInt.empty();
        }
        return new ItemSink(supplyTargets().keySet(), defaultRoute).priority(type);
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
        for (TravellingItem item : transport.clear()) {
            Block.popResource(level, worldPosition, McItemType.stackOf(item.batch()));
        }
    }

    @Override
    public void setRemoved() {
        if (announced && level != null && !level.isClientSide && pipe().routed()) {
            network().removeRouter(routerId);
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
        tag.put("Items", saveItems(registries));
        return tag;
    }

    private ListTag saveItems(HolderLookup.Provider registries) {
        ListTag items = new ListTag();
        for (TravellingItem item : transport.items()) {
            CompoundTag entry = new CompoundTag();
            entry.putLong("Id", item.id());
            entry.put("Stack", McItemType.stackOf(item.batch()).save(registries));
            entry.putString("Origin", item.origin().name());
            item.exit().ifPresent(exit -> entry.putString("Exit", exit.name()));
            item.destination().ifPresent(destination -> entry.putUUID("Destination", destination.value()));
            item.nextHop().ifPresent(nextHop -> entry.putUUID("NextHop", nextHop.value()));
            entry.putInt("Progress", item.progress());
            items.add(entry);
        }
        return items;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putUUID("Router", routerId.value());
        tag.put("Items", saveItems(registries));
        tag.putInt("Satellite", satelliteId);
        tag.putBoolean("DefaultRoute", defaultRoute);
        NonNullList<ItemStack> settings = NonNullList.withSize(CONFIG_SLOTS, ItemStack.EMPTY);
        for (int slot = 0; slot < CONFIG_SLOTS; slot++) {
            settings.set(slot, config.getItem(slot));
        }
        tag.put("Config", ContainerHelper.saveAllItems(new CompoundTag(), settings, registries));
        ListTag orders = new ListTag();
        for (OutputOrder order : outputOrders.all()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Destination", order.destination().value());
            entry.put("Stack", McItemType.stackOf(new ItemBatch(order.batch().type(), 1)).save(registries));
            entry.putInt("Count", order.batch().count());
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
        // an item the viewer already shows keeps its place, the update only fills in the rest
        PipeTransport shown = new PipeTransport();
        transport.clear().forEach(shown::insert);
        ListTag items = tag.getList("Items", Tag.TAG_COMPOUND);
        for (int index = 0; index < items.size(); index++) {
            CompoundTag entry = items.getCompound(index);
            Optional<ItemStack> stack = ItemStack.parse(registries, entry.getCompound("Stack"));
            if (stack.isEmpty() || stack.get().isEmpty()) {
                continue;
            }
            long id = entry.getLong("Id");
            transport.insert(TravellingItem.restore(
                    id,
                    McItemType.batchOf(stack.get()),
                    Side.valueOf(entry.getString("Origin")),
                    entry.hasUUID("Destination") ? new RouterId(entry.getUUID("Destination")) : null,
                    entry.hasUUID("NextHop") ? new RouterId(entry.getUUID("NextHop")) : null,
                    entry.contains("Exit") ? Side.valueOf(entry.getString("Exit")) : null,
                    shown.progressOf(id).orElse(entry.getInt("Progress"))));
        }
        if (tag.contains("Config")) {
            satelliteId = tag.getInt("Satellite");
            defaultRoute = !tag.contains("DefaultRoute") || tag.getBoolean("DefaultRoute");
            NonNullList<ItemStack> settings = NonNullList.withSize(CONFIG_SLOTS, ItemStack.EMPTY);
            ContainerHelper.loadAllItems(tag.getCompound("Config"), settings, registries);
            for (int slot = 0; slot < CONFIG_SLOTS; slot++) {
                config.setItem(slot, settings.get(slot));
            }
        }
        ListTag orders = tag.getList("Orders", Tag.TAG_COMPOUND);
        for (int index = 0; index < orders.size(); index++) {
            CompoundTag entry = orders.getCompound(index);
            ItemStack.parse(registries, entry.getCompound("Stack")).filter(stack -> !stack.isEmpty())
                    .ifPresent(stack -> outputOrders.add(new OutputOrder(new RouterId(entry.getUUID("Destination")),
                            new ItemBatch(McItemType.batchOf(stack).type(), entry.getInt("Count")))));
        }
    }
}
