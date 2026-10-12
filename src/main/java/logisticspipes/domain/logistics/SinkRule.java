package logisticspipes.domain.logistics;

import java.util.Map;
import java.util.OptionalInt;

import logisticspipes.domain.item.ItemType;

// decides how much a pipe wants an item nobody asked for
public interface SinkRule {

    // empty when the item is not wanted, contents is what the inventory next to the pipe holds
    OptionalInt priority(ItemType type, Map<ItemType, Integer> contents);

    // rules that look at the inventory cost more to ask
    default boolean needsContents() {
        return false;
    }
}
