package logisticspipes.domain.crafting;

import logisticspipes.domain.item.ItemBatch;
import logisticspipes.domain.routing.RouterId;

// crafted items a crafting pipe still owes to someone, since marks when it was placed or last made progress
public record OutputOrder(RouterId destination, ItemBatch batch, long since) {

    public OutputOrder(RouterId destination, ItemBatch batch) {
        this(destination, batch, 0);
    }
}
