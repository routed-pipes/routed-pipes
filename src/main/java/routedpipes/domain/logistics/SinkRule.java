package routedpipes.domain.logistics;

import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;

import routedpipes.domain.item.ItemType;

// decides how much a pipe wants an item nobody asked for
public interface SinkRule {

    // empty when the item is not wanted, contents is what the inventory next to the pipe holds
    OptionalInt priority(ItemType type, Map<ItemType, Integer> contents);

    // energy for taking in one stack
    int energy();

    // the items this rule is about
    Set<ItemType> wants();

    // true if the rule may also take items it does not list, like a default route
    default boolean takesUnlisted() {
        return false;
    }

    // how many more of the item are wanted, 0 for no limit
    default int maxItems(ItemType type, Map<ItemType, Integer> contents) {
        return 0;
    }

    default Optional<SinkBid> bid(ItemType type, Map<ItemType, Integer> contents) {
        OptionalInt priority = priority(type, contents);
        return priority.isPresent()
                ? Optional.of(new SinkBid(priority.getAsInt(), energy(), maxItems(type, contents)))
                : Optional.empty();
    }

    // rules that look at the inventory cost more to ask
    default boolean needsContents() {
        return false;
    }
}
