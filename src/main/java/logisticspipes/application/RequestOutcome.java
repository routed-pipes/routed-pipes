package logisticspipes.application;

import java.util.Map;

import logisticspipes.domain.item.ItemType;
import logisticspipes.domain.request.RequestPlan;

// what became of a request: started, short of items, or short of power
public record RequestOutcome(RequestPlan plan, boolean powered) {

    public boolean started() {
        return plan.complete() && powered;
    }

    public Map<ItemType, Integer> missing() {
        return plan.missing();
    }
}
