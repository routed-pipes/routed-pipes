package logisticspipes.application;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import logisticspipes.application.port.RouterNode;
import logisticspipes.domain.item.ItemType;
import logisticspipes.domain.logistics.BulkStorageSink;
import logisticspipes.domain.logistics.SinkRule;
import logisticspipes.domain.routing.RouterId;

// what the network keeps for bulk storage sink modules, see BulkStorageSink
// looking into every store for every item would not do in a large base, so there is an index of which store
// holds what, and the modules bring it up to date a few stores per tick
final class BulkStorage {

    static final int LOOKS_PER_TICK = 8;

    private final Set<RouterId> modules = new HashSet<>();
    private final Set<RouterId> stores = new HashSet<>();
    // sinks that may want an item they do not list more than a default route does
    private final Set<RouterId> keen = new HashSet<>();
    // stores in the order they are looked into, one that is no store any more drops out when its turn comes
    private final ArrayDeque<RouterId> queue = new ArrayDeque<>();
    private final Set<RouterId> queued = new HashSet<>();
    private final Map<RouterId, Set<ItemType>> held = new HashMap<>();
    private final Map<ItemType, Set<RouterId>> holders = new HashMap<>();

    void rulesChanged(RouterId id, List<SinkRule> rules) {
        forget(id);
        if (rules.stream().anyMatch(rule -> rule instanceof BulkStorageSink)) {
            modules.add(id);
        }
        if (BulkStorageSink.stores(rules)) {
            stores.add(id);
            if (queued.add(id)) {
                // a new store does not wait for a whole round
                queue.addFirst(id);
            }
        } else if (rules.stream().anyMatch(SinkRule::takesUnlisted)) {
            keen.add(id);
        }
    }

    void forget(RouterId id) {
        modules.remove(id);
        stores.remove(id);
        keen.remove(id);
        hold(id, Set.of());
    }

    Set<RouterId> modules() {
        return modules;
    }

    // the stores that held the item when they were last looked into
    Set<RouterId> holding(ItemType type) {
        return holders.getOrDefault(type, Set.of());
    }

    // everybody who may want the item more than a default route does, apart from the sinks that list it
    Set<RouterId> keenOn(ItemType type) {
        Set<RouterId> candidates = new HashSet<>(keen);
        candidates.addAll(holding(type));
        return candidates;
    }

    // looks into the next few stores
    void look(Function<RouterId, RouterNode> nodes) {
        for (int left = Math.min(LOOKS_PER_TICK, queue.size()); left > 0; left--) {
            RouterId id = queue.poll();
            RouterNode node = nodes.apply(id);
            if (node == null || !stores.contains(id)) {
                queued.remove(id);
                continue;
            }
            Set<ItemType> types = new HashSet<>();
            if (node.hasInventory()) {
                node.held().forEach((type, count) -> {
                    if (count > 0) {
                        types.add(type);
                    }
                });
            }
            hold(id, types);
            queue.add(id);
        }
    }

    private void hold(RouterId id, Set<ItemType> types) {
        Set<ItemType> before = types.isEmpty() ? held.remove(id) : held.put(id, Set.copyOf(types));
        if (before != null) {
            for (ItemType type : before) {
                if (!types.contains(type)) {
                    Set<RouterId> others = holders.get(type);
                    others.remove(id);
                    if (others.isEmpty()) {
                        holders.remove(type);
                    }
                }
            }
        }
        types.forEach(type -> holders.computeIfAbsent(type, key -> new HashSet<>()).add(id));
    }
}
