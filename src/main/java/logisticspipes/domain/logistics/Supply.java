package logisticspipes.domain.logistics;

import java.util.LinkedHashMap;
import java.util.Map;

import logisticspipes.domain.item.ItemType;

// what a supplier keeps in its inventory and when it reorders
public record Supply(Map<ItemType, Integer> targets, SupplyMode mode) {

    public Supply {
        targets = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(targets));
    }
}
