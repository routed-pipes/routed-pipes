package routedpipes.domain.logistics;

// what a routed pipe does for the network
public enum PipeKind {
    // takes anything nobody asked for
    BASIC,
    // offers the contents of its inventory
    PROVIDER,
    // where players order items
    REQUEST,
    // keeps its inventory stocked
    SUPPLIER,
    // turns ingredients into its recipe output
    CRAFTING,
    // extra drop off point for crafting ingredients
    SATELLITE,
    // does whatever the modules plugged into it do
    CHASSIS,
    // filters what passes on the ways that lead out of it
    FIREWALL,
    // a request pipe as a block with a crafting grid and a buffer of its own
    REQUEST_TABLE,
    // puts the listed fluid into the tanks next to it
    FLUID_BASIC,
    // packs fluid that is pumped into it and sends it to a pipe that takes it
    FLUID_INSERTION,
    // an insertion pipe that pulls fluid out of the tanks next to it by itself
    FLUID_EXTRACTOR,
    // offers the fluid in the tanks next to it
    FLUID_PROVIDER,
    // takes the listed fluids when no basic fluid pipe does
    FLUID_TERMINUS,
    // where players order fluid
    FLUID_REQUEST,
    // drop off point for fluid, asks again for what got lost on the way
    FLUID_SATELLITE,
    // keeps the tank next to it stocked with a fluid
    FLUID_SUPPLIER,
    // keeps the tank next to it stocked by ordering full buckets and emptying them
    FLUID_CONTAINER_SUPPLIER,
    // a request pipe that keeps a disk with macros
    REQUEST_MK2,
    // joins its network to the ones of the other connectors on its channel through the inventory next to it
    INVENTORY_SYSTEM_CONNECTOR,
    // sends what comes in without a destination to the destination pipe with the same frequency card
    SYSTEM_ENTRANCE,
    // takes what the entrance pipes with its frequency card send
    SYSTEM_DESTINATION,
    // destroys what the network has no other place for
    VOID;

    // the pipes players order items at
    public boolean requestPipe() {
        return this == REQUEST || this == REQUEST_MK2;
    }

    // fluid pipes have tanks of their own and work on the tanks next to them
    public boolean fluid() {
        return name().startsWith("FLUID_");
    }
}
