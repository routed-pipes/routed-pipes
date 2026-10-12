package logisticspipes.adapter.minecraft;

import logisticspipes.domain.item.ItemBatch;
import logisticspipes.domain.item.ItemType;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public record McItemType(Item item, DataComponentPatch components) implements ItemType {

    // stack must not be empty
    public static ItemBatch batchOf(ItemStack stack) {
        return new ItemBatch(new McItemType(stack.getItem(), stack.getComponentsPatch()), stack.getCount());
    }

    public static ItemStack stackOf(ItemBatch batch) {
        McItemType type = (McItemType) batch.type();
        return new ItemStack(type.item.builtInRegistryHolder(), batch.count(), type.components);
    }
}
