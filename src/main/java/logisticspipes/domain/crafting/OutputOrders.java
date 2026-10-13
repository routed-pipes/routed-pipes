package logisticspipes.domain.crafting;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.OptionalLong;
import java.util.function.ToIntFunction;

import logisticspipes.domain.item.ItemBatch;
import logisticspipes.domain.item.ItemType;

public final class OutputOrders {

    private final List<OutputOrder> orders = new ArrayList<>();

    public void clear() {
        orders.clear();
    }

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

    // what is still owed per item, in the order the items were first asked for
    public Map<ItemType, Integer> owedByType() {
        Map<ItemType, Integer> owed = new LinkedHashMap<>();
        for (OutputOrder order : orders) {
            owed.merge(order.batch().type(), order.batch().count(), Integer::sum);
        }
        return owed;
    }

    public int owedInTotal() {
        return orders.stream().mapToInt(order -> order.batch().count()).sum();
    }

    // when the oldest order for that item that is still open was placed, empty if there is none
    public OptionalLong firstPlaced(ItemType type) {
        return orders.stream().filter(order -> !order.isExtra() && order.batch().type().equals(type))
                .mapToLong(OutputOrder::placed).min();
    }

    // drops everything that was ordered with that request, returns what was dropped
    public List<OutputOrder> cancel(long request) {
        List<OutputOrder> cancelled = orders.stream().filter(order -> order.request() == request).toList();
        orders.removeAll(cancelled);
        return cancelled;
    }

    // offers every order to the shipper, which says how many items it managed to send, true if any items went out
    // an order is dropped when the crafter made no progress at all for the given time, its ingredients were probably lost
    // an order waiting behind another one for the same item is not stale while the crafter keeps delivering
    public boolean ship(long now, long patience, ToIntFunction<OutputOrder> shipper) {
        boolean progress = false;
        for (ListIterator<OutputOrder> iterator = orders.listIterator(); iterator.hasNext();) {
            OutputOrder order = iterator.next();
            if (now - order.since() >= patience) {
                iterator.remove();
                continue;
            }
            int sent = shipper.applyAsInt(order);
            progress |= sent > 0;
            int left = order.batch().count() - sent;
            if (left <= 0) {
                iterator.remove();
            } else if (left < order.batch().count()) {
                iterator.set(order.left(left, now));
            }
        }
        if (progress) {
            orders.replaceAll(order -> order.left(order.batch().count(), now));
        }
        return progress;
    }
}
