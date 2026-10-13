package routedpipes.domain.logistics;

import java.util.Set;

import routedpipes.domain.item.ItemType;

// what a firewall pipe lets through on the ways that lead out of it
public record FirewallRule(Set<ItemType> listed, boolean blacklist, boolean blockProviding, boolean blockCrafting,
        boolean blockSorting, boolean blockPower) {

    // a fresh firewall stops nothing but power
    public static final FirewallRule DEFAULT = new FirewallRule(Set.of(), true, false, false, false, true);

    public FirewallRule {
        listed = Set.copyOf(listed);
    }

    // the list is about items whatever their damage or data: as a blacklist it stops those, otherwise all others
    public boolean rejects(ItemType type) {
        return blacklist == listed.contains(type.plain());
    }

    // items nobody ordered, on their way to a sink
    public boolean stopsSorted(ItemType type) {
        return blockSorting || rejects(type);
    }

    // ordered items come from providers or crafters, so they pass as long as one of the two may
    public boolean stopsOrdered(ItemType type) {
        return blockProviding && blockCrafting || rejects(type);
    }
}
