package logisticspipes.application;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import logisticspipes.application.port.PowerNode;
import logisticspipes.application.port.PowerProvider;
import logisticspipes.application.port.RouterNode;
import logisticspipes.domain.logistics.FirewallRule;
import logisticspipes.domain.power.PowerBuffer;
import logisticspipes.domain.routing.RouterId;

// the energy of power providers on its way to the pipes that feed machines with it
// it is not the energy the network itself runs on, that comes from the junctions
// it only goes from routed pipe to routed pipe in a straight line, and only through pipes that let it pass
public final class SubSystemPower {

    private final LogisticsNetwork network;
    // the routers each router reaches through plain pipes without a corner
    private final Map<RouterId, Set<RouterId>> straight = new HashMap<>();

    SubSystemPower(LogisticsNetwork network) {
        this.network = network;
    }

    private Optional<PowerNode> node(RouterId id) {
        RouterNode node = network.node(id);
        return node == null ? Optional.empty() : node.powerNode();
    }

    public void updateStraightLinks(RouterId id, Set<RouterId> neighbours) {
        straight.put(id, Set.copyOf(neighbours));
    }

    void forget(RouterId id) {
        straight.remove(id);
    }

    private boolean passes(RouterId id) {
        return node(id).filter(PowerNode::passes).isPresent();
    }

    // like the energy of junctions, a firewall that blocks power lets none of it out on the other side
    private boolean stops(RouterId id) {
        return network.node(id).firewall().filter(FirewallRule::blockPower).isPresent();
    }

    // the routers whose power providers the pipe at this router gets its energy from, itself included
    public List<RouterId> sourcesFor(RouterId at) {
        if (!passes(at)) {
            return List.of();
        }
        List<RouterId> found = new ArrayList<>(List.of(at));
        Set<RouterId> seen = new HashSet<>(found);
        for (int next = 0; next < found.size(); next++) {
            RouterId from = found.get(next);
            if (stops(from)) {
                continue;
            }
            for (RouterId to : straight.getOrDefault(from, Set.of())) {
                if (passes(to) && seen.add(to)) {
                    found.add(to);
                }
            }
        }
        return found;
    }

    // a supplying pipe that is short of that much orders it from the providers it can get energy from
    // each is asked for as much of it as it holds of all their energy, and the network pays for the order with its own
    public void refill(RouterId at, int missing) {
        Set<PowerProvider> providers = Collections.newSetFromMap(new IdentityHashMap<>());
        for (RouterId source : sourcesFor(at)) {
            node(source).ifPresent(found -> providers.addAll(found.providers()));
        }
        long available = 0;
        for (PowerProvider provider : providers) {
            available += provider.stored();
        }
        if (available <= 0 || missing <= 0 || !network.usePower(at, PowerBuffer.orderCost(missing))) {
            return;
        }
        for (PowerProvider provider : providers) {
            provider.order(at, PowerBuffer.share(missing, provider.stored(), available));
        }
    }

    // a provider next to these routers sends energy to the pipe that ordered it, false when it cannot get there
    public boolean deliver(Collection<RouterId> neighbours, RouterId destination, int amount) {
        Optional<PowerNode> target = node(destination);
        if (target.isEmpty() || sourcesFor(destination).stream().noneMatch(neighbours::contains)) {
            return false;
        }
        target.get().receive(amount);
        return true;
    }
}
