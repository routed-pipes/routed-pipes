package logisticspipes.domain.request;

import logisticspipes.domain.item.ItemBatch;
import logisticspipes.domain.routing.RouterId;

// have a crafter send this much of its output to the destination once it is made
public record CraftJob(RouterId crafter, RouterId destination, ItemBatch output) {
}
