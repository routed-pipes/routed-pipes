package logisticspipes.domain.logistics;

import java.util.Map;
import java.util.OptionalInt;
import java.util.Set;

import logisticspipes.domain.item.ItemType;

// takes enchanted items: any of them, or with a list only enchanted versions of the listed items
public record EnchantmentSink(Set<ItemType> items, boolean listedOnly) implements SinkRule {

    public static final int PRIORITY = 30;

    public EnchantmentSink {
        items = Set.copyOf(items);
    }

    public static EnchantmentSink any() {
        return new EnchantmentSink(Set.of(), false);
    }

    public static EnchantmentSink of(Set<ItemType> items) {
        return new EnchantmentSink(items, true);
    }

    @Override
    public OptionalInt priority(ItemType type, Map<ItemType, Integer> contents) {
        if (!type.enchanted()) {
            return OptionalInt.empty();
        }
        if (!listedOnly) {
            return OptionalInt.of(PRIORITY);
        }
        // the one with a list wins over the one that takes anything enchanted
        return items.contains(type.plain()) ? OptionalInt.of(PRIORITY + 1) : OptionalInt.empty();
    }

    @Override
    public int energy() {
        return 2;
    }

    @Override
    public Set<ItemType> wants() {
        return Set.of();
    }

    // enchanted items differ in their data, so they cannot be listed up front
    @Override
    public boolean takesUnlisted() {
        return !listedOnly || !items.isEmpty();
    }
}
