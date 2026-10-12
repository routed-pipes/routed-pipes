package logisticspipes.domain.crafting;

import logisticspipes.domain.item.ItemBatch;
import logisticspipes.domain.routing.RouterId;

// crafted items a crafting pipe still owes to someone
public record OutputOrder(RouterId destination, ItemBatch batch) {
}
