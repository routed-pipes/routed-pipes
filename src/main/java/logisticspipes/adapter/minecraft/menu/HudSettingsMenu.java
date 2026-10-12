package logisticspipes.adapter.minecraft.menu;

import logisticspipes.adapter.minecraft.HudGlassesItem;
import logisticspipes.domain.hud.HudCategory;
import logisticspipes.domain.hud.HudSettings;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

// which panels the glasses show, with the hotbar and the armour slots to put them on
// a button press is the category to toggle
public class HudSettingsMenu extends AbstractContainerMenu {

    public static final int WIDTH = 180;
    public static final int HEIGHT = 160;
    public static final int HOTBAR_X = 10;
    public static final int HOTBAR_Y = 134;
    public static final int ARMOR_X = 154;
    public static final int ARMOR_Y = 10;
    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    // where vanilla keeps the helmet in the inventory, the rest follow downwards
    private static final int HELMET_INDEX = 39;

    private final Inventory inventory;
    // the inventory slot of the glasses that are set up
    private final int glassesSlot;
    private final ContainerData shown;

    // client side
    public HudSettingsMenu(MenuType<?> type, int id, Inventory inventory) {
        this(type, id, inventory, -1, new SimpleContainerData(1));
    }

    public HudSettingsMenu(MenuType<?> type, int id, Inventory inventory, int glassesSlot) {
        this(type, id, inventory, glassesSlot, new ContainerData() {
            @Override
            public int get(int index) {
                return HudGlassesItem.settingsOf(inventory.getItem(glassesSlot)).bits();
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int getCount() {
                return 1;
            }
        });
    }

    private HudSettingsMenu(MenuType<?> type, int id, Inventory inventory, int glassesSlot, ContainerData shown) {
        super(type, id);
        this.inventory = inventory;
        this.glassesSlot = glassesSlot;
        this.shown = shown;
        addDataSlots(shown);
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, HOTBAR_X + column * 18, HOTBAR_Y));
        }
        for (int row = 0; row < ARMOR.length; row++) {
            EquipmentSlot equipment = ARMOR[row];
            addSlot(new Slot(inventory, HELMET_INDEX - row, ARMOR_X, ARMOR_Y + row * 18) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return inventory.player.getEquipmentSlotForItem(stack) == equipment;
                }

                @Override
                public int getMaxStackSize() {
                    return 1;
                }
            });
        }
    }

    public HudSettings settings() {
        return HudSettings.ofBits(shown.get(0));
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id < 0 || id >= HudCategory.values().length) {
            return false;
        }
        ItemStack glasses = inventory.getItem(glassesSlot);
        HudGlassesItem.setSettings(glasses, HudGlassesItem.settingsOf(glasses).toggled(HudCategory.values()[id]));
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    // closes once the glasses are moved away, even onto the head
    @Override
    public boolean stillValid(Player player) {
        return glassesSlot < 0 || inventory.getItem(glassesSlot).getItem() instanceof HudGlassesItem;
    }
}
