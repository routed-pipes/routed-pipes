package routedpipes.adapter.minecraft.menu;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import routedpipes.adapter.minecraft.CraftingTableBlockEntity;
import routedpipes.application.RequestOutcome;
import routedpipes.domain.item.ItemType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

// recipe grid of markers, a preview of the result and the ingredient storage
// slots that are never shown carry what the network said about the ingredients a recipe viewer asked about
public class CraftingTableMenu extends AbstractContainerMenu implements MarkerMenu {

    // no pipe next to the table can order
    public static final int NETWORK_NONE = 0;
    // the network can bring everything that is not in storage
    public static final int NETWORK_SUPPLIES = 1;
    public static final int NETWORK_MISSING = 2;

    private static final int PATTERN_END = CraftingTableBlockEntity.PATTERN_SLOTS;
    private static final int RESULT_SLOT = PATTERN_END;
    private static final int STORAGE_START = RESULT_SLOT + 1;
    private static final int STORAGE_END = STORAGE_START + CraftingTableBlockEntity.STORAGE_SLOTS;
    private static final int SHORTFALL_SLOTS = 9;
    private static final int PLAYER_START = STORAGE_END + SHORTFALL_SLOTS;
    private static final int DATA_NETWORK = 0;
    // sent last, so a client that sees the number of its question has the rest of the answer too
    private static final int DATA_QUESTION = 1;

    private final Container preview = new SimpleContainer(1);
    private final Container shortfall = new SimpleContainer(SHORTFALL_SLOTS);
    private final ContainerData answer = new SimpleContainerData(2);
    private final CraftingTableBlockEntity table;

    // client side
    public CraftingTableMenu(MenuType<?> type, int id, Inventory inventory) {
        this(type, id, inventory, new SimpleContainer(CraftingTableBlockEntity.PATTERN_SLOTS),
                new SimpleContainer(CraftingTableBlockEntity.STORAGE_SLOTS), null);
    }

    public CraftingTableMenu(MenuType<?> type, int id, Inventory inventory, CraftingTableBlockEntity table) {
        this(type, id, inventory, table.pattern(), table.storage(), table);
    }

    private CraftingTableMenu(MenuType<?> type, int id, Inventory inventory, Container pattern, Container storage,
            CraftingTableBlockEntity table) {
        super(type, id);
        this.table = table;
        for (int slot = 0; slot < CraftingTableBlockEntity.PATTERN_SLOTS; slot++) {
            addSlot(new MarkerSlot(pattern, slot, 30 + slot % 3 * 18, 17 + slot / 3 * 18));
        }
        addSlot(new MarkerSlot(preview, 0, 124, 35));
        for (int slot = 0; slot < CraftingTableBlockEntity.STORAGE_SLOTS; slot++) {
            addSlot(new Slot(storage, slot, 8 + slot % 9 * 18, 84 + slot / 9 * 18));
        }
        for (int slot = 0; slot < SHORTFALL_SLOTS; slot++) {
            addSlot(new HiddenSlot(shortfall, slot));
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, 9 + row * 9 + column, 8 + column * 18, 134 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 192));
        }
        addDataSlots(answer);
    }

    // finds out whether the network can bring what these ingredients need beyond the storage, with order it is told to
    // the client reads the answer from the menu once it carries the number of its question
    public void askIngredients(Player player, int question, List<ItemStack> ingredients, boolean order) {
        if (table == null) {
            return;
        }
        Optional<RequestOutcome> outcome = table.orderIngredients(player, ingredients, !order);
        Map<ItemType, Integer> missing = outcome.map(RequestOutcome::missing).orElse(Map.of());
        Shortfall.write(shortfall, missing);
        answer.set(DATA_NETWORK, outcome.isEmpty() ? NETWORK_NONE : missing.isEmpty() ? NETWORK_SUPPLIES : NETWORK_MISSING);
        answer.set(DATA_QUESTION, question);
        if (order && outcome.isPresent() && !outcome.get().powered()) {
            player.displayClientMessage(Component.translatable("routedpipes.request.no_power").withStyle(ChatFormatting.RED), false);
        }
    }

    // the number of the question the menu holds the answer to
    public int answeredQuestion() {
        return answer.get(DATA_QUESTION);
    }

    public int network() {
        return answer.get(DATA_NETWORK);
    }

    // what the network cannot bring, at most nine kinds
    public List<ItemStack> shortfall() {
        return Shortfall.read(shortfall);
    }

    @Override
    public int markerSlots() {
        return PATTERN_END;
    }

    // the grid only knows which item, never how many
    @Override
    public void setMarker(int slot, ItemStack stack) {
        slots.get(slot).set(stack.copyWithCount(stack.isEmpty() ? 0 : 1));
    }

    @Override
    public void broadcastChanges() {
        if (table != null) {
            preview.setItem(0, table.result());
        }
        super.broadcastChanges();
    }

    // grid slots take a marker of the held item or get cleared, the preview does nothing
    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= STORAGE_END && slotId < PLAYER_START) {
            return;
        }
        if (slotId < 0 || slotId > RESULT_SLOT) {
            super.clicked(slotId, button, clickType, player);
            return;
        }
        if (slotId < PATTERN_END && clickType == ClickType.PICKUP) {
            slots.get(slotId).set(getCarried().copyWithCount(getCarried().isEmpty() ? 0 : 1));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (index < STORAGE_START || index >= STORAGE_END && index < PLAYER_START || !slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        boolean moved = index < STORAGE_END
                ? moveItemStackTo(stack, PLAYER_START, slots.size(), true)
                : moveItemStackTo(stack, STORAGE_START, STORAGE_END, false);
        if (!moved) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return table == null || Container.stillValidBlockEntity(table, player);
    }
}
