package logisticspipes.domain.logistics;

// behaviours that can be plugged into a chassis pipe
public enum ModuleKind {
    // takes listed items, like a basic pipe
    ITEM_SINK,
    // pushes the contents of its inventory into the network
    EXTRACTOR,
    // offers the contents of its inventory, like a provider pipe
    PROVIDER
}
