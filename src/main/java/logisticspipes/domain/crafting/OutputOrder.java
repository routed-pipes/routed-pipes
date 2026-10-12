package logisticspipes.domain.crafting;

import logisticspipes.domain.item.ItemBatch;
import logisticspipes.domain.routing.RouterId;

// crafted items a crafting pipe still owes to someone, since marks when it was placed or last made progress
// without a destination it is something extra the machine puts out, sent off to whoever takes it
public record OutputOrder(RouterId destination, ItemBatch batch, long since) {

    public OutputOrder(RouterId destination, ItemBatch batch) {
        this(destination, batch, 0);
    }

    public static OutputOrder extra(ItemBatch batch, long since) {
        return new OutputOrder(null, batch, since);
    }

    public boolean isExtra() {
        return destination == null;
    }
}
