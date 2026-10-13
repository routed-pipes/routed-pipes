package routedpipes.adapter.minecraft;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import routedpipes.adapter.minecraft.menu.HudSettingsMenu;
import routedpipes.domain.hud.HudCategory;
import routedpipes.domain.hud.HudSettings;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;

// worn on the head they show what the pipes around the player are doing
// a helmet that protects from nothing and never wears out, used in the hand it opens its settings
public class HudGlassesItem extends ArmorItem {

    private static final String HIDDEN_TAG = "LogisticsPipesHudHidden";

    private final Supplier<MenuType<HudSettingsMenu>> settingsMenu;

    public HudGlassesItem(Holder<ArmorMaterial> material, Properties properties, Supplier<MenuType<HudSettingsMenu>> settingsMenu) {
        super(material, Type.HELMET, properties.stacksTo(1));
        this.settingsMenu = settingsMenu;
    }

    // only there for the look of the worn glasses
    public static ArmorMaterial material(ResourceLocation texture) {
        return new ArmorMaterial(Map.of(), 0, SoundEvents.ARMOR_EQUIP_GENERIC, () -> Ingredient.EMPTY,
                List.of(new ArmorMaterial.Layer(texture)), 0, 0);
    }

    public static boolean worn(Player player) {
        return player.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof HudGlassesItem;
    }

    // the hidden categories are stored, so glasses from before a new category show it
    public static HudSettings settingsOf(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        HudSettings settings = HudSettings.ALL;
        for (HudCategory category : HudCategory.values()) {
            if (tag.getCompound(HIDDEN_TAG).getBoolean(category.name())) {
                settings = settings.toggled(category);
            }
        }
        return settings;
    }

    public static void setSettings(ItemStack stack, HudSettings settings) {
        if (!(stack.getItem() instanceof HudGlassesItem)) {
            return;
        }
        CompoundTag hidden = new CompoundTag();
        EnumSet.allOf(HudCategory.class).stream().filter(category -> !settings.shows(category))
                .forEach(category -> hidden.putBoolean(category.name(), true));
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> tag.put(HIDDEN_TAG, hidden));
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!level.isClientSide) {
            int slot = hand == InteractionHand.MAIN_HAND ? player.getInventory().selected : Inventory.SLOT_OFFHAND;
            player.openMenu(new SimpleMenuProvider((id, inventory, user) -> new HudSettingsMenu(settingsMenu.get(), id, inventory, slot),
                    stack.getHoverName()));
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    // no armour line in the tooltip
    @Override
    public ItemAttributeModifiers getDefaultAttributeModifiers() {
        return ItemAttributeModifiers.EMPTY;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".description").withStyle(ChatFormatting.GRAY));
    }
}
