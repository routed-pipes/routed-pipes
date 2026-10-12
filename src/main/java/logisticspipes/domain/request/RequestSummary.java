package logisticspipes.domain.request;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import logisticspipes.domain.item.ItemType;

// what a plan comes to: how many of each item are taken from stock, crafted and missing, and the energy for moving them
// ingredients and items crafted on the way count as well, so this is the whole tree of the request laid flat
public record RequestSummary(Map<ItemType, Integer> fromStock, Map<ItemType, Integer> crafted, Map<ItemType, Integer> missing,
        long energy) {

    // the energy is what every item taken from stock and every crafted item costs to send
    public static RequestSummary of(RequestPlan plan, int energyPerProvidedItem, int energyPerCraftedItem) {
        Map<ItemType, Integer> fromStock = new LinkedHashMap<>();
        Map<ItemType, Integer> crafted = new LinkedHashMap<>();
        long energy = 0;
        for (Provision provision : plan.provisions()) {
            fromStock.merge(provision.batch().type(), provision.batch().count(), Integer::sum);
            // fluid providers send for free
            if (!provision.batch().type().packedFluid()) {
                energy += (long) provision.batch().count() * energyPerProvidedItem;
            }
        }
        for (CraftJob craft : plan.crafts()) {
            crafted.merge(craft.output().type(), craft.output().count(), Integer::sum);
            energy += (long) craft.output().count() * energyPerCraftedItem;
        }
        return new RequestSummary(Collections.unmodifiableMap(fromStock), Collections.unmodifiableMap(crafted),
                Collections.unmodifiableMap(new LinkedHashMap<>(plan.missing())), energy);
    }

    public boolean complete() {
        return missing.isEmpty();
    }
}
