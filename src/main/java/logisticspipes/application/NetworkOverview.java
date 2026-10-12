package logisticspipes.application;

import java.util.ArrayList;
import java.util.List;

import logisticspipes.application.port.RouterNode;
import logisticspipes.domain.logistics.PipeKind;
import logisticspipes.domain.routing.RouteTable;
import logisticspipes.domain.routing.RouterId;
import logisticspipes.domain.statistics.PipeStatistics;

// a whole network at a glance: the routed pipe it is looked at from and every one that pipe can reach, nearest first,
// each with what it moved since it was loaded and the orders it still has open, and the energy they share
public record NetworkOverview(List<Entry> pipes, int energy) {

    // distance is the length of the way from the pipe the overview starts at, 0 for that pipe itself
    public record Entry(RouterId router, PipeKind kind, int distance, int sent, int received, int relayed, int openOrders) {
    }

    // empty for a router the network does not know
    public static NetworkOverview of(LogisticsNetwork network, RouterId at) {
        RouterNode own = network.node(at);
        if (own == null) {
            return new NetworkOverview(List.of(), 0);
        }
        List<Entry> pipes = new ArrayList<>();
        pipes.add(entry(at, own, 0));
        RouteTable routes = network.routes(at);
        for (RouterId id : routes.byDistance()) {
            RouterNode node = network.node(id);
            if (node != null && !id.equals(at)) {
                pipes.add(entry(id, node, routes.distance(id).orElse(0)));
            }
        }
        return new NetworkOverview(List.copyOf(pipes), network.status(at).energy());
    }

    private static Entry entry(RouterId id, RouterNode node, int distance) {
        PipeStatistics counts = node.statistics();
        return new Entry(id, node.kind(), distance, counts.session(PipeStatistics.Kind.SENT),
                counts.session(PipeStatistics.Kind.RECEIVED), counts.session(PipeStatistics.Kind.RELAYED),
                node.outputOrders().all().size());
    }
}
