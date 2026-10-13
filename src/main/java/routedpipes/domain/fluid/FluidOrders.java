package routedpipes.domain.fluid;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

import routedpipes.domain.routing.RouterId;

// the fluid a provider still has to send, worked off in the order it was asked for
public final class FluidOrders {

    public record Order(FluidType fluid, int amount, RouterId destination) {
    }

    private final Deque<Order> orders = new ArrayDeque<>();

    public void add(Order order) {
        orders.add(order);
    }

    public boolean isEmpty() {
        return orders.isEmpty();
    }

    public Optional<Order> first() {
        return Optional.ofNullable(orders.peek());
    }

    // that much of the first order went out
    public void sent(int amount) {
        Order order = orders.poll();
        if (order != null && amount < order.amount()) {
            orders.addFirst(new Order(order.fluid(), order.amount() - amount, order.destination()));
        }
    }

    // the first order cannot be served, returns what is left of it
    public Optional<Order> giveUp() {
        return Optional.ofNullable(orders.poll());
    }

    // how much of the fluid is promised to someone
    public int owed(FluidType fluid) {
        return orders.stream().filter(order -> order.fluid().equals(fluid)).mapToInt(Order::amount).sum();
    }

    // how much of the fluid is still to be sent to that pipe
    public int owedTo(RouterId destination, FluidType fluid) {
        return orders.stream().filter(order -> order.fluid().equals(fluid) && order.destination().equals(destination))
                .mapToInt(Order::amount).sum();
    }
}
