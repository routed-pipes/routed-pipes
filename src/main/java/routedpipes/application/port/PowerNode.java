package routedpipes.application.port;

import java.util.List;

// what a routed pipe has to do with the energy of power providers
public interface PowerNode {

    // whether that energy may go into and through this pipe
    boolean passes();

    // the power providers next to the pipe
    List<PowerProvider> providers();

    // energy a provider sends for the machines next to the pipe
    void receive(int amount);
}
