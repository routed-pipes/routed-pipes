package routedpipes.domain.logistics;

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
    QUICK_SORT,
    // an extractor that only touches the listed items, or all but those
    ADVANCED_EXTRACTOR,
    // takes every item of the listed mods
    MOD_ITEM_SINK,
    // takes every item with one of the listed tags
    TAG_ITEM_SINK,
    // takes every item of the listed creative tabs
    CREATIVE_TAB_ITEM_SINK,
    // takes anything enchanted
    ENCHANTMENT_SINK,
    // takes enchanted versions of the listed items
    ENCHANTMENT_SINK_MK2,
    // crafts like a crafting pipe
    CRAFTER,
    // keeps its inventory stocked like a supplier pipe
    ACTIVE_SUPPLIER,
    // makes every default route of its network take more of what its inventory already holds
    BULK_STORAGE_SINK
}
