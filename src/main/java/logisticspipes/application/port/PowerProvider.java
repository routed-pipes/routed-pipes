package logisticspipes.application.port;

import logisticspipes.domain.routing.RouterId;

// a block that stores energy of other mods and sends it to the pipes that ask for it
public interface PowerProvider {

    int stored();

    // the pipe wants that much with the next tick of the provider
    void order(RouterId destination, int amount);
}
