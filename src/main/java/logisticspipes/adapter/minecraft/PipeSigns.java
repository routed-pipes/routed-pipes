package logisticspipes.adapter.minecraft;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import logisticspipes.adapter.minecraft.menu.SignItemMenu;
import logisticspipes.domain.item.ItemType;
import logisticspipes.domain.logistics.PipeKind;
import logisticspipes.domain.routing.Side;
import logisticspipes.domain.sign.AmountText;
import logisticspipes.domain.sign.SignFaces;
import logisticspipes.domain.sign.SignKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

// the signs on the faces of a routed pipe and what is written on them
public final class PipeSigns {

    // what a sign shows is looked at again this often, every pipe in a tick of its own
    static final int INTERVAL = 20;

    private final PipeBlockEntity pipe;
    private final SignFaces faces = new SignFaces();
    private final Map<Direction, SignView> views = new EnumMap<>(Direction.class);
    // the item every item amount sign counts, one slot per face
    private final SimpleContainer items = new SimpleContainer(Direction.values().length);
    // what the network held when it was last counted, and when that was
    private Map<ItemType, Integer> amounts = Map.of();
    private long countedAt = -INTERVAL;

    PipeSigns(PipeBlockEntity pipe) {
        this.pipe = pipe;
        items.addListener(container -> changed());
    }

    public Optional<SignKind> at(Direction face) {
        return faces.at(Directions.toDomain(face));
    }

    // what to draw on which face, on clients this is what the server sent
    public Map<Direction, SignView> views() {
        return Collections.unmodifiableMap(views);
    }

    public boolean pushedOut(Direction face) {
        return faces.pushedOut(Directions.toDomain(face));
    }

    // false when the face has a sign already or this pipe is not one for the kind
    public boolean place(Direction face, SignKind kind) {
        if (!faces.place(Directions.toDomain(face), kind, pipe.kind())) {
            return false;
        }
        pipe.signsNeedTicks(true);
        changed();
        return true;
    }

    public boolean remove(Direction face) {
        if (faces.remove(Directions.toDomain(face)).isEmpty()) {
            return false;
        }
        pipe.signsNeedTicks(!faces.isEmpty());
        items.setItem(face.get3DDataValue(), ItemStack.EMPTY);
        changed();
        return true;
    }

    // the item the item amount sign on that face counts
    public ItemStack counted(Direction face) {
        return items.getItem(face.get3DDataValue());
    }

    // the screen that picks the item of the item amount sign on that face
    public void open(Direction face, Player player, MenuType<SignItemMenu> type) {
        player.openMenu(new SimpleMenuProvider((id, inventory, user) -> new SignItemMenu(type, id, inventory, items,
                face.get3DDataValue(), someone -> Container.stillValidBlockEntity(pipe, someone)
                        && at(face).filter(kind -> kind == SignKind.ITEM_AMOUNT).isPresent()),
                Component.translatable("routedpipes.gui.sign_item")));
    }

    private void changed() {
        refresh();
        pipe.setChanged();
        sync();
    }

    private void sync() {
        if (pipe.getLevel() != null && !pipe.getLevel().isClientSide) {
            pipe.getLevel().sendBlockUpdated(pipe.getBlockPos(), pipe.getBlockState(), pipe.getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    void tick() {
        long now = pipe.getLevel().getGameTime();
        if (Math.floorMod(now + pipe.getBlockPos().hashCode(), INTERVAL) != 0) {
            return;
        }
        // not when a pipe next to this one has just counted for it
        if (faces.has(SignKind.ITEM_AMOUNT) && now - countedAt >= INTERVAL) {
            spread(pipe.amounts(), now);
        }
        if (refresh()) {
            sync();
        }
    }

    // a wall of pipes with item amount signs asks the network once and not once per pipe:
    // whoever counts hands the result on to the pipes of the wall, and those to the ones next to them
    private void spread(Map<ItemType, Integer> counted, long now) {
        Set<PipeSigns> told = new HashSet<>(Set.of(this));
        Deque<PipeSigns> open = new ArrayDeque<>(told);
        while (!open.isEmpty()) {
            PipeSigns next = open.poll();
            next.take(counted, now, next != this);
            for (PipeSigns neighbour : next.wall()) {
                if (told.add(neighbour)) {
                    open.add(neighbour);
                }
            }
        }
    }

    private void take(Map<ItemType, Integer> counted, long now, boolean fromAnother) {
        amounts = counted;
        if (fromAnother) {
            countedAt = now;
            if (refresh()) {
                sync();
            }
        }
    }

    // the pipes right next to this one that have item amount signs too
    // what is behind a firewall can differ, so a wall ends there
    private List<PipeSigns> wall() {
        List<PipeSigns> wall = new ArrayList<>();
        Level level = pipe.getLevel();
        for (Direction side : Direction.values()) {
            BlockPos neighbour = pipe.getBlockPos().relative(side);
            if (pipe.kind() != PipeKind.FIREWALL && LogisticsPipeBlock.connected(pipe.getBlockState(), side)
                    && level.isLoaded(neighbour) && level.getBlockEntity(neighbour) instanceof PipeBlockEntity other
                    && other.signs() != null && other.kind() != PipeKind.FIREWALL
                    && other.signs().faces.has(SignKind.ITEM_AMOUNT)) {
                wall.add(other.signs());
            }
        }
        return wall;
    }

    // works out again what every sign shows, true when one of them changed
    private boolean refresh() {
        Map<Direction, SignView> shown = new EnumMap<>(Direction.class);
        faces.all().forEach((side, kind) -> shown.put(Directions.toMinecraft(side), view(kind, Directions.toMinecraft(side))));
        boolean same = shown.keySet().equals(views.keySet())
                && shown.entrySet().stream().allMatch(entry -> entry.getValue().sameAs(views.get(entry.getKey())));
        if (!same) {
            views.clear();
            views.putAll(shown);
        }
        return !same;
    }

    private SignView view(SignKind kind, Direction face) {
        return switch (kind) {
            case CRAFTING -> crafting();
            case ITEM_AMOUNT -> amount(counted(face));
        };
    }

    private static SignView empty() {
        return new SignView(ItemStack.EMPTY, List.of(Component.translatable("routedpipes.sign.empty")));
    }

    // what the crafting pipe makes and the satellite it sends ingredients to
    private SignView crafting() {
        if (pipe.recipe().isEmpty()) {
            return empty();
        }
        ItemStack result = pipe.config().getItem(PipeBlockEntity.OUTPUT_SLOT).copyWithCount(1);
        List<Component> lines = new ArrayList<>();
        if (!pipe.satelliteName().isEmpty()) {
            // as text, a number does not come back the same from the way to the client
            lines.add(Component.translatable("routedpipes.sign.satellite", pipe.satelliteName()));
        }
        lines.add(result.getHoverName());
        return new SignView(result, lines);
    }

    // how many of the item the network holds for the taking
    private SignView amount(ItemStack item) {
        if (item.isEmpty()) {
            return empty();
        }
        int count = amounts.getOrDefault(McItemType.batchOf(item).type(), 0);
        return new SignView(item.copy(), List.of(Component.translatable("routedpipes.sign.amount"),
                Component.literal(AmountText.of(count)), item.getHoverName()));
    }

    // clients get what is written on the signs instead of the items that are counted
    CompoundTag save(HolderLookup.Provider registries, boolean forClients) {
        if (forClients) {
            // a client may ask before the first look
            refresh();
        }
        ListTag signs = new ListTag();
        faces.all().forEach((side, kind) -> {
            CompoundTag sign = new CompoundTag();
            sign.putString("Side", side.name());
            sign.putString("Kind", kind.name());
            if (forClients) {
                sign.put("View", views.get(Directions.toMinecraft(side)).save(registries));
            }
            signs.add(sign);
        });
        CompoundTag tag = new CompoundTag();
        tag.put("Signs", signs);
        if (!forClients) {
            tag.put("Items", ContainerTags.save(items, registries));
        }
        return tag;
    }

    void load(CompoundTag tag, HolderLookup.Provider registries) {
        faces.clear();
        views.clear();
        ListTag signs = tag.getList("Signs", Tag.TAG_COMPOUND);
        for (int index = 0; index < signs.size(); index++) {
            CompoundTag sign = signs.getCompound(index);
            Optional<Side> side = named(Side.values(), sign.getString("Side"));
            Optional<SignKind> kind = named(SignKind.values(), sign.getString("Kind"));
            if (side.isEmpty() || kind.isEmpty() || !faces.place(side.get(), kind.get(), pipe.kind())) {
                continue;
            }
            if (sign.contains("View")) {
                views.put(Directions.toMinecraft(side.get()), SignView.load(sign.getCompound("View"), registries));
            }
        }
        pipe.signsNeedTicks(!faces.isEmpty());
        if (tag.contains("Items")) {
            ContainerTags.load(items, tag.getCompound("Items"), registries);
        }
    }

    private static <E extends Enum<E>> Optional<E> named(E[] values, String name) {
        for (E value : values) {
            if (value.name().equals(name)) {
                return Optional.of(value);
            }
        }
        return Optional.empty();
    }
}
