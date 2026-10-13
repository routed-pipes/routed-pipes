package logisticspipes.domain.crafting;

import logisticspipes.domain.item.ItemBatch;
import logisticspipes.domain.routing.RouterId;

// crafted items a crafting pipe still owes to someone, since marks when it was placed or last made progress
// without a destination it is something extra the machine puts out, sent off to whoever takes it
// placed puts the orders of a world in the order they were made, it never changes and no two orders share it
// request is the same for everything that was ordered in one go, like a thing and the parts it is made of
public record OutputOrder(RouterId destination, ItemBatch batch, long since, long placed, long request) {

    // ticks leave this much room for the orders placed in one of them
    public static final int PLACED_PER_TICK = 1 << 20;

    public OutputOrder(RouterId destination, ItemBatch batch, long since) {
        this(destination, batch, since, since * PLACED_PER_TICK, 0);
    }

    public OutputOrder(RouterId destination, ItemBatch batch) {
        this(destination, batch, 0);
    }

    public static OutputOrder extra(ItemBatch batch, long since) {
        return new OutputOrder(null, batch, since);
    }

    public boolean isExtra() {
        return destination == null;
    }

    // the same order with that much left of it at that time
    public OutputOrder left(int count, long now) {
        return new OutputOrder(destination, new ItemBatch(batch.type(), count), now, placed, request);
    }
}
