package logisticspipes.domain.logistics;

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
    FIREWALL
}
