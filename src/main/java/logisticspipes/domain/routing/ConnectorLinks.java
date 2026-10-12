package logisticspipes.domain.routing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import logisticspipes.domain.tube.TubeKind;

// inventory system connectors: routers on the same channel that each sit on an inventory are neighbours
// what one of them puts into its inventory the other takes out again, so the inventory is the pipe between them
public final class ConnectorLinks {

    public enum State {
        // nobody else is on the channel
        DISCONNECTED,
        // somebody is, but this connector has no inventory to reach them through
        MISMATCHED,
        CONNECTED
    }

    public static final int MAX_RESISTANCE = 9999;

    // inventory is null while the connector touches none
    private record Connector(UUID channel, Side inventory, int resistance) {
    }

    private final Map<RouterId, Connector> connectors = new HashMap<>();
    private final Map<UUID, Set<RouterId>> channels = new HashMap<>();

    public boolean isEmpty() {
        return connectors.isEmpty();
    }

    // puts a connector on a channel or tells what changed about it, a null channel takes it off
    // returns the routers whose links may be different now
    public Set<RouterId> set(RouterId router, UUID channel, Side inventory, int resistance) {
        if (channel == null) {
            return remove(router);
        }
        Connector connector = new Connector(channel, inventory, Math.max(0, Math.min(resistance, MAX_RESISTANCE)));
        if (connector.equals(connectors.get(router))) {
            return Set.of();
        }
        Set<RouterId> touched = new HashSet<>(remove(router));
        connectors.put(router, connector);
        channels.computeIfAbsent(channel, key -> new HashSet<>()).add(router);
        touched.addAll(channels.get(channel));
        return touched;
    }

    public Set<RouterId> remove(RouterId router) {
        Connector connector = connectors.remove(router);
        if (connector == null) {
            return Set.of();
        }
        Set<RouterId> members = channels.get(connector.channel());
        Set<RouterId> touched = new HashSet<>(members);
        members.remove(router);
        if (members.isEmpty()) {
            channels.remove(connector.channel());
        }
        return touched;
    }

    // the others on the channel of this connector
    private List<RouterId> partners(RouterId router, Connector connector) {
        List<RouterId> partners = new ArrayList<>(channels.get(connector.channel()));
        partners.remove(router);
        partners.sort(Comparator.comparing(RouterId::value));
        return partners;
    }

    // a link through the inventory to every other connector of the channel that has an inventory too
    // it counts as one pipe plus the resistance, which makes it less attractive than a way through pipes
    public List<Link> linksOf(RouterId router) {
        Connector connector = connectors.get(router);
        if (connector == null || connector.inventory() == null) {
            return List.of();
        }
        List<Link> links = new ArrayList<>();
        for (RouterId partner : partners(router, connector)) {
            if (connectors.get(partner).inventory() != null) {
                links.add(new Link(partner, connector.inventory(), (1 + connector.resistance()) * TubeKind.PIPE_WEIGHT));
            }
        }
        return links;
    }

    // whether what leaves the first router through the given side is picked up by the second
    public boolean linked(RouterId from, Side exit, RouterId to) {
        return linksOf(from).stream().anyMatch(link -> link.target().equals(to) && link.exit() == exit);
    }

    public State state(RouterId router) {
        Connector connector = connectors.get(router);
        if (connector == null || partners(router, connector).isEmpty()) {
            return State.DISCONNECTED;
        }
        return connector.inventory() == null ? State.MISMATCHED : State.CONNECTED;
    }
}
