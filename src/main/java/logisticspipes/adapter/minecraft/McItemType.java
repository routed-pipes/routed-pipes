package logisticspipes.adapter.minecraft;

import logisticspipes.domain.item.ItemBatch;
import logisticspipes.domain.item.ItemType;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

public record McItemType(Item item, DataComponentPatch components) implements ItemType {

    // stack must not be empty
    public static ItemBatch batchOf(ItemStack stack) {
        return new ItemBatch(new McItemType(stack.getItem(), stack.getComponentsPatch()), stack.getCount());
    }

    public static ItemStack stackOf(ItemBatch batch) {
        McItemType type = (McItemType) batch.type();
        return new ItemStack(type.item.builtInRegistryHolder(), batch.count(), type.components);
    }

    private ItemStack one() {
        return new ItemStack(item.builtInRegistryHolder(), 1, components);
    }

    @Override
    public ItemType plain() {
        return components.isEmpty() ? this : new McItemType(item, DataComponentPatch.EMPTY);
    }

    @Override
    public String namespace() {
        return ItemTraits.namespace(item);
    }

    @Override
    public boolean inTag(String tag) {
        return ItemTraits.inTag(item, tag);
    }

    @Override
    public boolean inGroup(String group) {
        return ItemTraits.inGroup(one(), group);
    }

    @Override
    public boolean enchanted() {
        return !components.isEmpty() && EnchantmentHelper.hasAnyEnchantments(one());
    }
}
