package logisticspipes.adapter.minecraft;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import logisticspipes.adapter.minecraft.menu.FluidConfigMenu;
import logisticspipes.adapter.minecraft.menu.FluidRequestMenu;
import logisticspipes.application.FluidLogistics;
import logisticspipes.application.RequestOutcome;
import logisticspipes.application.port.FluidNode;
import logisticspipes.domain.fluid.ExtractionBudget;
import logisticspipes.domain.fluid.FluidBatch;
import logisticspipes.domain.fluid.FluidSink;
import logisticspipes.domain.fluid.FluidSupply;
import logisticspipes.domain.fluid.FluidType;
import logisticspipes.domain.fluid.InsertionTimer;
import logisticspipes.domain.fluid.PipeTanks;
import logisticspipes.domain.fluid.Tank;
import logisticspipes.domain.fluid.TankAccess;
import logisticspipes.domain.item.ItemType;
import logisticspipes.domain.logistics.InventoryRef;
import logisticspipes.domain.logistics.PipeKind;
import logisticspipes.domain.routing.RouterId;
import logisticspipes.domain.routing.Side;
import logisticspipes.domain.transport.Departure;
import logisticspipes.domain.transport.TransportMode;
import logisticspipes.domain.transport.TravellingItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

// the fluid half of a fluid pipe: its tanks, the tanks around it and what the pipe does with them
// fluid never flows from pipe to pipe, it is packed into a container that travels like any item
public final class FluidPipe implements FluidNode {

    // slots of the pipe settings that list fluids
    public static final int FILTER_SLOTS = 9;
    // tanks of a neighbour can change without its block changing, so they are not trusted for longer
    private static final int NEIGHBOURS_MAX_AGE = 40;
    // ticks between a container supplier looking at its tank, and what emptying one container costs
    private static final int CONTAINER_INTERVAL = 100;
    private static final int ENERGY_PER_CONTAINER = 5;

    private final PipeBlockEntity pipe;
    private final PipeKind kind;
    private final PipeTanks tanks = new PipeTanks();
    private final Map<Side, InsertionTimer> timers = new EnumMap<>(Side.class);
    private final Map<Side, ExtractionBudget> budgets = new EnumMap<>(Side.class);
    private Map<Side, TankAccess> neighboursCache;
    private long neighboursCachedAt;
    // the settings as players have them open, the pipe takes nothing while it is being set up
    private final List<FluidConfigMenu> open = new ArrayList<>();
    // supplier pipes: how many mB to keep in the tank and how to order them
    private int stockTarget;
    private boolean partial;
    private FluidSupply.Minimum minimum = FluidSupply.Minimum.ONE_BUCKET;
    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case FluidConfigMenu.DATA_SATELLITE -> pipe.satelliteId();
                case FluidConfigMenu.DATA_BUCKETS -> stockTarget / FluidConfigMenu.BUCKET;
                case FluidConfigMenu.DATA_REST -> stockTarget % FluidConfigMenu.BUCKET;
                case FluidConfigMenu.DATA_PARTIAL -> partial ? 1 : 0;
                default -> minimum.ordinal();
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case FluidConfigMenu.DATA_SATELLITE -> pipe.setSatelliteId(value);
                case FluidConfigMenu.DATA_BUCKETS -> stockTarget = value * FluidConfigMenu.BUCKET + stockTarget % FluidConfigMenu.BUCKET;
                case FluidConfigMenu.DATA_REST -> stockTarget = stockTarget - stockTarget % FluidConfigMenu.BUCKET + value;
                case FluidConfigMenu.DATA_PARTIAL -> partial = value != 0;
                default -> minimum = FluidSupply.Minimum.values()[Math.floorMod(value, FluidSupply.Minimum.values().length)];
            }
            pipe.setChanged();
        }

        @Override
        public int getCount() {
            return FluidConfigMenu.DATA_COUNT;
        }
    };

    private FluidPipe(PipeBlockEntity pipe, PipeKind kind) {
        this.pipe = pipe;
        this.kind = kind;
        for (Side side : Side.values()) {
            timers.put(side, new InsertionTimer());
            budgets.put(side, new ExtractionBudget());
        }
    }

    // null for pipes that have nothing to do with fluids
    static FluidPipe of(PipeBlockEntity pipe, PipeKind kind) {
        return kind != null && kind.fluid() ? new FluidPipe(pipe, kind) : null;
    }

    private Level level() {
        return pipe.getLevel();
    }

    private FluidServices services() {
        return ((LogisticsPipeBlock) pipe.getBlockState().getBlock()).services().fluids();
    }

    private FluidLogistics logistics() {
        return pipe.network().fluids();
    }

    public PipeTanks pipeTanks() {
        return tanks;
    }

    // takes fluid pumped in from outside
    public boolean receives() {
        return kind == PipeKind.FLUID_INSERTION || kind == PipeKind.FLUID_EXTRACTOR;
    }

    // unpacks fluid sent to it and empties its own tanks into the tanks around it
    private boolean unpacks() {
        return !receives() && kind != PipeKind.FLUID_CONTAINER_SUPPLIER;
    }

    // what a supplier keeps in its tank
    public void stock(FluidType fluid, int amount, boolean partial, FluidSupply.Minimum minimum) {
        pipe.config().setItem(0, FluidContainerItem.marker(fluid));
        this.stockTarget = amount;
        this.partial = partial;
        this.minimum = minimum;
        pipe.setChanged();
    }

    @Override
    public Optional<FluidSupply> supply() {
        if (kind != PipeKind.FLUID_SUPPLIER) {
            return Optional.empty();
        }
        return listed(1).stream().findFirst().map(fluid -> new FluidSupply(fluid, stockTarget, partial, minimum));
    }

    // the tank next to the pipe and what is waiting in the pipe for it
    @Override
    public List<Integer> stocked(FluidType type) {
        List<Integer> stocked = new ArrayList<>();
        neighbours().forEach((side, tank) -> stocked.add(tank.contents().getOrDefault(type, 0)
                + tanks.side(side).holding(type) + tanks.centre().holding(type)));
        return stocked;
    }

    void neighbourChanged() {
        neighboursCache = null;
    }

    // the tanks next to the pipe, other pipes never count as one
    private Map<Side, TankAccess> neighbours() {
        long now = level().getGameTime();
        if (neighboursCache == null || now - neighboursCachedAt >= NEIGHBOURS_MAX_AGE) {
            Map<Side, TankAccess> found = new EnumMap<>(Side.class);
            for (Direction direction : Direction.values()) {
                BlockPos target = pipe.getBlockPos().relative(direction);
                if (level().isLoaded(target) && !(level().getBlockState(target).getBlock() instanceof LogisticsPipeBlock)) {
                    services().storage().tanks(level(), target, direction.getOpposite())
                            .ifPresent(access -> found.put(Directions.toDomain(direction), access));
                }
            }
            neighboursCache = found;
            neighboursCachedAt = now;
        }
        return neighboursCache;
    }

    @Override
    public Set<InventoryRef> tanks() {
        Set<InventoryRef> refs = new HashSet<>();
        for (Side side : neighbours().keySet()) {
            refs.add(new InventoryRef(pipe.getBlockPos().relative(Directions.toMinecraft(side)).asLong()));
        }
        return refs;
    }

    private Set<FluidType> listed(int slots) {
        Set<FluidType> fluids = new LinkedHashSet<>();
        for (int slot = 0; slot < slots; slot++) {
            FluidContainerItem.fluidIn(pipe.config().getItem(slot)).ifPresent(batch -> fluids.add(batch.type()));
        }
        return fluids;
    }

    @Override
    public Optional<FluidSink> sink() {
        if ((kind != PipeKind.FLUID_BASIC && kind != PipeKind.FLUID_TERMINUS) || beingSetUp()) {
            return Optional.empty();
        }
        Set<FluidType> filter = listed(FluidConfigMenu.markerSlots(kind));
        return filter.isEmpty() ? Optional.empty() : Optional.of(new FluidSink(filter,
                kind == PipeKind.FLUID_BASIC ? FluidSink.Priority.FLUID_SINK : FluidSink.Priority.TERMINUS));
    }

    private boolean beingSetUp() {
        open.removeIf(menu -> !menu.inUse());
        return !open.isEmpty();
    }

    @Override
    public int sinkRoom(FluidType type) {
        return tanks.roomFor(type, neighbours());
    }

    // fluid pumped into a side of the pipe from outside, returns how much went in
    public int fill(Direction side, FluidBatch batch, boolean simulate) {
        if (!receives()) {
            return 0;
        }
        int filled = tanks.side(Directions.toDomain(side)).fill(batch, simulate);
        if (filled > 0 && !simulate) {
            pipe.setChanged();
        }
        return filled;
    }

    void serverTick() {
        if (unpacks() && !tanks.isEmpty()) {
            Map<Side, TankAccess> neighbours = neighbours();
            for (Side side : Side.values()) {
                if (!neighbours.containsKey(side) && !tanks.side(side).isEmpty()) {
                    tanks.sideLost(side);
                    pipe.setChanged();
                }
            }
            if (tanks.pushOut(neighbours)) {
                pipe.setChanged();
            }
        }
        if (receives()) {
            sendOff();
        }
        logistics().tick(pipe.routerId(), level().getGameTime());
        if (kind == PipeKind.FLUID_CONTAINER_SUPPLIER && level().getGameTime() % CONTAINER_INTERVAL == 0) {
            orderContainers();
        }
        if (kind == PipeKind.FLUID_EXTRACTOR && level().getGameTime() % ExtractionBudget.INTERVAL == 0) {
            neighbours().forEach((side, source) -> {
                if (budgets.get(side).pull(tanks.side(side), source,
                        () -> logistics().usePower(pipe.routerId(), ExtractionBudget.ENERGY)) > 0) {
                    pipe.setChanged();
                }
            });
        }
    }

    @Override
    public boolean provides() {
        return kind == PipeKind.FLUID_PROVIDER;
    }

    @Override
    public Map<FluidType, Integer> providable() {
        Map<FluidType, Integer> stock = new LinkedHashMap<>();
        for (TankAccess neighbour : neighbours().values()) {
            neighbour.contents().forEach((type, amount) -> {
                if (neighbour.drain(new FluidBatch(type, 1), true) > 0) {
                    stock.merge(type, amount, (first, second) -> (int) Math.min((long) first + second, Integer.MAX_VALUE));
                }
            });
        }
        return stock;
    }

    // one container for every tank that fluid came out of
    @Override
    public int provide(FluidType type, int amount, RouterId destination) {
        int left = amount;
        for (Map.Entry<Side, TankAccess> neighbour : neighbours().entrySet()) {
            int drained = left == 0 ? 0 : neighbour.getValue().drain(new FluidBatch(type, left), false);
            if (drained > 0) {
                pipe.insert(packed(new FluidBatch(type, drained), neighbour.getKey(), destination, TransportMode.REQUESTED));
                left -= drained;
            }
        }
        return amount - left;
    }

    // orders the listed full containers until the tanks hold what that many of them hold
    // a bucket does not stack, so the same container in several slots adds up
    private void orderContainers() {
        Map<ItemType, Integer> wanted = new LinkedHashMap<>();
        Map<ItemType, FluidBatch> holds = new LinkedHashMap<>();
        for (int slot = 0; slot < FILTER_SLOTS; slot++) {
            ItemStack listed = pipe.config().getItem(slot);
            services().storage().contained(listed).ifPresent(contained -> {
                ItemType container = McItemType.batchOf(listed).type();
                wanted.merge(container, listed.getCount(), Integer::sum);
                holds.put(container, contained);
            });
        }
        wanted.forEach((container, count) -> {
            FluidBatch contained = holds.get(container);
            long stocked = 0;
            for (TankAccess tank : neighbours().values()) {
                stocked += tank.contents().getOrDefault(contained.type(), 0);
            }
            // a container that is not quite covered by the tank counts as there, or the tank would overflow
            int covered = (int) Math.min(count, (stocked + contained.amount() - 1) / contained.amount());
            pipe.requestShortfall(Map.of(container, count), Map.of(container, covered), false);
        });
    }

    // empties full containers that were ordered into the tanks and sends the empty ones off
    // what the tanks have no room for is sent off full, true if the item was looked after
    private boolean emptied(TravellingItem item) {
        ItemStack stack = McItemType.stackOf(item.batch());
        Optional<FluidBatch> contained = kind == PipeKind.FLUID_CONTAINER_SUPPLIER ? services().storage().contained(stack) : Optional.empty();
        if (contained.isEmpty()) {
            return false;
        }
        int done = 0;
        for (TankAccess tank : neighbours().values()) {
            while (done < stack.getCount() && tank.fill(contained.get(), true) == contained.get().amount()
                    && logistics().usePower(pipe.routerId(), ENERGY_PER_CONTAINER)) {
                tank.fill(contained.get(), false);
                done++;
            }
        }
        Item empty = stack.getItem().getCraftingRemainingItem();
        if (done > 0 && empty != null) {
            pipe.insert(new TravellingItem(McItemType.batchOf(new ItemStack(empty, done)), item.origin(), null));
        }
        if (done < stack.getCount()) {
            pipe.insert(new TravellingItem(McItemType.batchOf(stack.copyWithCount(stack.getCount() - done)), item.origin(), null));
        }
        return true;
    }

    // packs what was pumped in and sends it to a pipe that takes it
    private void sendOff() {
        long now = level().getGameTime();
        for (Side side : Side.values()) {
            Tank tank = tanks.side(side);
            if (tank.isEmpty() || !timers.get(side).due(tank.isFull())) {
                continue;
            }
            FluidType type = tank.content().get().type();
            Optional<FluidLogistics.Shipment> shipment = logistics().insert(pipe.routerId(), tank.content().get(), now);
            if (shipment.isEmpty()) {
                timers.get(side).failed();
                continue;
            }
            tank.drain(shipment.get().amount());
            send(new FluidBatch(type, shipment.get().amount()), side, shipment.get().destination());
            timers.get(side).sent();
        }
    }

    private void send(FluidBatch batch, Side origin, RouterId destination) {
        pipe.insert(packed(batch, origin, destination, TransportMode.SORTED));
    }

    private static TravellingItem packed(FluidBatch batch, Side origin, RouterId destination, TransportMode mode) {
        return new TravellingItem(McItemType.batchOf(FluidContainerItem.of(batch)), origin, destination, mode);
    }

    // packed fluid that was sent to this pipe stops in the middle of it
    boolean stops(TravellingItem item) {
        if (unpacks() && item.batch().type().packedFluid() && item.destination().filter(pipe.routerId()::equals).isPresent()) {
            item.abandon();
            return true;
        }
        return false;
    }

    // looks after packed fluid that is about to leave a pipe, true if it went no further
    // it only ever moves on into another pipe, anywhere else it would end up as an item
    static boolean ended(PipeBlockEntity pipe, Departure departure) {
        TravellingItem item = departure.item();
        if (!item.batch().type().packedFluid()) {
            // a container supplier takes the full containers it ordered
            return pipe.fluidPipe() != null && departure.exit().isEmpty()
                    && item.destination().filter(pipe.routerId()::equals).isPresent() && pipe.fluidPipe().emptied(item);
        }
        Optional<FluidBatch> packed = FluidContainerItem.fluidIn(McItemType.stackOf(item.batch()));
        if (packed.isEmpty()) {
            return true;
        }
        if (departure.exit().isPresent()) {
            BlockPos target = pipe.getBlockPos().relative(Directions.toMinecraft(departure.exit().get()));
            if (!pipe.getLevel().isLoaded(target) || pipe.getLevel().getBlockEntity(target) instanceof PipeBlockEntity) {
                return false;
            }
        }
        FluidPipe here = pipe.fluidPipe();
        if (here != null && here.unpacks() && item.destination().filter(pipe.routerId()::equals).isPresent()) {
            here.unpack(packed.get(), item.origin());
            return true;
        }
        FluidLogistics logistics = pipe.network().fluids();
        item.destination().ifPresent(destination -> logistics.lost(destination, packed.get()));
        if (pipe.kind() != null) {
            // a routed pipe can still find it a new home, in a plain pipe it is gone
            logistics.reroute(pipe.routerId(), packed.get(), pipe.getLevel().getGameTime())
                    .ifPresent(shipment -> pipe.insert(packed(new FluidBatch(packed.get().type(), shipment.amount()),
                            item.origin(), shipment.destination(), TransportMode.SORTED)));
        }
        return true;
    }

    // packed fluid in a pipe that breaks is gone, true if the item was that
    static boolean spilled(PipeBlockEntity pipe, TravellingItem item) {
        if (!item.batch().type().packedFluid()) {
            return false;
        }
        item.destination().ifPresent(destination -> FluidContainerItem.fluidIn(McItemType.stackOf(item.batch()))
                .ifPresent(packed -> pipe.network().fluids().lost(destination, packed)));
        return true;
    }

    // what does not fit into the tanks goes to another pipe that takes it, or is gone
    private void unpack(FluidBatch batch, Side origin) {
        int left = tanks.unpack(batch, neighbours());
        logistics().arrived(pipe.routerId(), batch, left);
        if (left > 0) {
            logistics().reroute(pipe.routerId(), new FluidBatch(batch.type(), left), level().getGameTime())
                    .ifPresent(shipment -> send(new FluidBatch(batch.type(), shipment.amount()), origin, shipment.destination()));
        }
        pipe.setChanged();
    }

    // what a slot of the settings remembers of a clicked item, empty if it holds no fluid
    // the container supplier lists the full containers themselves, everyone else the fluid
    private ItemStack markerOf(ItemStack clicked) {
        if (kind == PipeKind.FLUID_CONTAINER_SUPPLIER) {
            return services().storage().contained(clicked).isPresent() ? clicked.copy() : ItemStack.EMPTY;
        }
        return FluidContainerItem.fluidIn(clicked).or(() -> services().storage().contained(clicked))
                .map(batch -> FluidContainerItem.marker(batch.type())).orElse(ItemStack.EMPTY);
    }

    // every fluid this pipe could order and how much of it
    public Map<FluidType, Integer> available() {
        return logistics().available(pipe.routerId());
    }

    // orders fluid for the tanks next to this pipe, all of it or nothing
    public RequestOutcome request(FluidBatch wanted) {
        return logistics().request(pipe.routerId(), wanted, level().getGameTime());
    }

    // null for pipes without settings
    MenuProvider menuProvider() {
        if (kind == PipeKind.FLUID_REQUEST) {
            return new SimpleMenuProvider((id, inventory, player) -> new FluidRequestMenu(services().requestMenu().get(), id,
                    this, user -> Container.stillValidBlockEntity(pipe, user)), pipe.getBlockState().getBlock().getName());
        }
        if (!services().hasSettings(kind)) {
            return null;
        }
        return new SimpleMenuProvider((id, inventory, player) -> {
            FluidConfigMenu menu = new FluidConfigMenu(services().configMenu(kind), id, inventory, kind, pipe.config(), data,
                    user -> Container.stillValidBlockEntity(pipe, user), this::markerOf);
            open.add(menu);
            return menu;
        }, pipe.getBlockState().getBlock().getName());
    }

    CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        for (Side side : Side.values()) {
            saveTank(tag, side.name(), tanks.side(side), registries);
        }
        saveTank(tag, "CENTRE", tanks.centre(), registries);
        tag.putIntArray("WaitForMore", timers.values().stream().mapToInt(InsertionTimer::waitForMore).toArray());
        tag.putIntArray("Pause", timers.values().stream().mapToInt(InsertionTimer::pause).toArray());
        tag.putIntArray("PaidFor", budgets.values().stream().mapToInt(ExtractionBudget::paidFor).toArray());
        tag.putInt("StockTarget", stockTarget);
        tag.putBoolean("Partial", partial);
        tag.putInt("Minimum", minimum.ordinal());
        return tag;
    }

    private static void saveTank(CompoundTag tag, String name, Tank tank, HolderLookup.Provider registries) {
        tank.content().ifPresent(content -> FluidContents.CODEC
                .encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), FluidContents.of(content))
                .ifSuccess(saved -> tag.put(name, saved)));
    }

    void load(CompoundTag tag, HolderLookup.Provider registries) {
        for (Side side : Side.values()) {
            loadTank(tag, side.name(), tanks.side(side), registries);
        }
        loadTank(tag, "CENTRE", tanks.centre(), registries);
        int[] waitForMore = tag.getIntArray("WaitForMore");
        int[] pause = tag.getIntArray("Pause");
        int[] paidFor = tag.getIntArray("PaidFor");
        stockTarget = tag.getInt("StockTarget");
        partial = tag.getBoolean("Partial");
        if (tag.contains("Minimum")) {
            minimum = FluidSupply.Minimum.values()[Math.floorMod(tag.getInt("Minimum"), FluidSupply.Minimum.values().length)];
        }
        for (Side side : Side.values()) {
            if (side.ordinal() < paidFor.length) {
                budgets.put(side, new ExtractionBudget(paidFor[side.ordinal()]));
            }
            if (side.ordinal() < waitForMore.length && side.ordinal() < pause.length) {
                timers.put(side, new InsertionTimer(waitForMore[side.ordinal()], pause[side.ordinal()]));
            }
        }
    }

    private static void loadTank(CompoundTag tag, String name, Tank tank, HolderLookup.Provider registries) {
        tank.drain(tank.amount());
        if (tag.contains(name)) {
            FluidContents.CODEC.parse(registries.createSerializationContext(NbtOps.INSTANCE), tag.get(name))
                    .ifSuccess(contents -> tank.fill(contents.batch(), false));
        }
    }
}
