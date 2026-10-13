package routedpipes.domain.crafting;

import java.util.Set;

import routedpipes.domain.item.ItemType;

// what an idle crafter clears out of its machine: the listed items, or with exclude everything but those
public record Cleanup(Set<ItemType> listed, boolean exclude) {

    public Cleanup {
        listed = Set.copyOf(listed);
    }

    // the list is about items whatever their damage or data
    public boolean removes(ItemType type) {
        return exclude != listed.contains(type.plain());
    }
}
