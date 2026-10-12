package logisticspipes.domain.logistics;

// behaviours that can be plugged into a chassis pipe
public enum ModuleKind {
    // takes listed items, like a basic pipe
    ITEM_SINK,
    // pushes the contents of its inventory into the network
    EXTRACTOR,
    // offers the contents of its inventory, like a provider pipe
    PROVIDER,
    // overflow for listed items when no item sink has room
    TERMINUS,
    // tops its inventory up from passing items
    PASSIVE_SUPPLIER,
    // takes more of what its inventory already holds
    POLYMORPHIC_ITEM_SINK,
    // moves whole stacks to pipes that asked for the item by name
    QUICK_SORT
}
