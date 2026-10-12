package logisticspipes.domain.request;

import java.util.List;
import java.util.Map;

import logisticspipes.domain.item.ItemType;

public record RequestPlan(List<Provision> provisions, List<CraftJob> crafts, Map<ItemType, Integer> missing) {

    public boolean complete() {
        return missing.isEmpty();
    }
}
