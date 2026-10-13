package routedpipes.testing;

import java.util.ArrayList;
import java.util.List;

import routedpipes.application.port.PowerNode;
import routedpipes.application.port.PowerProvider;
import routedpipes.domain.routing.RouterId;

// a pipe that lets the energy of power providers in and counts what arrives
public final class FakePowerNode implements PowerNode {

    // a provider that remembers what was ordered from it
    public static final class Provider implements PowerProvider {
        public int stored;
        public final List<RouterId> destinations = new ArrayList<>();
        public final List<Integer> amounts = new ArrayList<>();

        public Provider(int stored) {
            this.stored = stored;
        }

        @Override
        public int stored() {
            return stored;
        }

        @Override
        public void order(RouterId destination, int amount) {
            destinations.add(destination);
            amounts.add(amount);
        }
    }

    public boolean passes = true;
    public final List<PowerProvider> providers = new ArrayList<>();
    public int received;

    @Override
    public boolean passes() {
        return passes;
    }

    @Override
    public List<PowerProvider> providers() {
        return providers;
    }

    @Override
    public void receive(int amount) {
        received += amount;
    }
}
