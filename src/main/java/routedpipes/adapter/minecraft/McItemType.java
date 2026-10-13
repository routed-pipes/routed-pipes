package routedpipes.adapter.minecraft;

import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import routedpipes.domain.item.ItemBatch;
import routedpipes.domain.item.ItemType;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;

public record McItemType(Item item, DataComponentPatch components) implements ItemType {

    // stack must not be empty
    public static ItemBatch batchOf(ItemStack stack) {
        return new ItemBatch(new McItemType(stack.getItem(), stack.getComponentsPatch()), stack.getCount());
    }

    public static ItemStack stackOf(ItemBatch batch) {
        // a type with fuzzy flags is shown as the item of its slot
        McItemType type = (McItemType) batch.type().exact();
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
    public boolean packedFluid() {
        return item instanceof FluidContainerItem;
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
    public Set<String> tags() {
        return item.builtInRegistryHolder().tags().map(tag -> tag.location().toString()).collect(Collectors.toSet());
    }

    @Override
    public int damage() {
        Optional<? extends Integer> damage = components.get(DataComponents.DAMAGE);
        return damage == null || damage.isEmpty() ? 0 : damage.get();
    }

    @Override
    public ItemType undamaged() {
        return damage() == 0 ? this : new McItemType(item, components.forget(type -> type == DataComponents.DAMAGE));
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
