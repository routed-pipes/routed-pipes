package logisticspipes.domain.logistics;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

import logisticspipes.domain.item.ItemType;

// decides how much a pipe wants an item nobody asked for
public interface SinkRule {

    // empty when the item is not wanted, contents is what the inventory next to the pipe holds
    OptionalInt priority(ItemType type, Map<ItemType, Integer> contents);

    // energy for taking in one stack
    int energy();

    default Optional<SinkBid> bid(ItemType type, Map<ItemType, Integer> contents) {
        OptionalInt priority = priority(type, contents);
        return priority.isPresent() ? Optional.of(new SinkBid(priority.getAsInt(), energy())) : Optional.empty();
    }

    // rules that look at the inventory cost more to ask
    default boolean needsContents() {
        return false;
    }
}
