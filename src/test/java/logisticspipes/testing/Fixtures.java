package logisticspipes.testing;

import java.util.UUID;

import logisticspipes.domain.item.ItemBatch;
import logisticspipes.domain.item.ItemType;
import logisticspipes.domain.routing.RouterId;

public final class Fixtures {

    public record TestItem(String name) implements ItemType {
    }

    private Fixtures() {
    }

    public static RouterId router(int number) {
        return new RouterId(new UUID(0, number));
    }

    public static ItemBatch batch(String name, int count) {
        return new ItemBatch(new TestItem(name), count);
    }
}
