package routedpipes.domain.request;

import routedpipes.domain.item.ItemBatch;
import routedpipes.domain.routing.RouterId;

// take items out of a provider and send them to the destination
public record Provision(RouterId provider, RouterId destination, ItemBatch batch) {
}
