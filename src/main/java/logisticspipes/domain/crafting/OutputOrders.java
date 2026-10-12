package logisticspipes.domain.crafting;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.function.ToIntFunction;

import logisticspipes.domain.item.ItemBatch;
import logisticspipes.domain.item.ItemType;

public final class OutputOrders {

    private final List<OutputOrder> orders = new ArrayList<>();

    public void add(OutputOrder order) {
        orders.add(order);
    }

    public List<OutputOrder> all() {
        return List.copyOf(orders);
    }

    public boolean isEmpty() {
        return orders.isEmpty();
    }

    // how many of an item are still owed in total
    public int owed(ItemType type) {
        return orders.stream().filter(order -> order.batch().type().equals(type)).mapToInt(order -> order.batch().count()).sum();
    }

    public int owedInTotal() {
        return orders.stream().mapToInt(order -> order.batch().count()).sum();
    }

    // offers every order to the shipper, which says how many items it managed to send
    // an order that made no progress for the given time is dropped, its ingredients were probably lost
    public void ship(long now, long patience, ToIntFunction<OutputOrder> shipper) {
        for (ListIterator<OutputOrder> iterator = orders.listIterator(); iterator.hasNext();) {
            OutputOrder order = iterator.next();
            if (now - order.since() >= patience) {
                iterator.remove();
                continue;
            }
            int left = order.batch().count() - shipper.applyAsInt(order);
            if (left <= 0) {
                iterator.remove();
            } else if (left < order.batch().count()) {
                iterator.set(new OutputOrder(order.destination(), new ItemBatch(order.batch().type(), left), now));
            }
        }
    }
}
