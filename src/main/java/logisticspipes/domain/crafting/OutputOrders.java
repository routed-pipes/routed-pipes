package logisticspipes.domain.crafting;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;
import java.util.function.ToIntFunction;

import logisticspipes.domain.item.ItemBatch;

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

    // offers every order to the shipper, which says how many items it managed to send
    public void ship(ToIntFunction<OutputOrder> shipper) {
        for (ListIterator<OutputOrder> iterator = orders.listIterator(); iterator.hasNext();) {
            OutputOrder order = iterator.next();
            int left = order.batch().count() - shipper.applyAsInt(order);
            if (left <= 0) {
                iterator.remove();
            } else if (left < order.batch().count()) {
                iterator.set(new OutputOrder(order.destination(), new ItemBatch(order.batch().type(), left)));
            }
        }
    }
}
