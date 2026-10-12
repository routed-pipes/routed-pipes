package logisticspipes.domain.request;

import logisticspipes.domain.item.ItemBatch;
import logisticspipes.domain.routing.RouterId;

// take items out of a provider and send them to the destination
public record Provision(RouterId provider, RouterId destination, ItemBatch batch) {
}
