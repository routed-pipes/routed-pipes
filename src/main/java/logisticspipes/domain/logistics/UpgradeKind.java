package logisticspipes.domain.logistics;

// what can be added to a routed pipe or to a module in a chassis
public enum UpgradeKind {
    // the pipe reaches into inventories through another face than the one it touches
    SNEAKY(1),
    // items leaving the pipe travel faster until the next routed pipe
    SPEED(4),
    // gives every module of a chassis two upgrade slots of its own
    MODULE_UPGRADE(1),
    // an extractor works more often
    ACTION_SPEED(8),
    // an extractor moves more items at a time
    ITEM_EXTRACTION(8),
    // an extractor moves whole stacks, and more kinds of items at a time
    ITEM_STACK_EXTRACTION(8),
    // a crafter also sends off a second thing its machine puts out
    CRAFTING_BYPRODUCT(1),
    // an idle crafter clears its machine, three more items to list per upgrade
    CRAFTING_CLEANUP(4),
    // cuts the pipe off from what is next to it, side by side
    DISCONNECTION(1),
    // every ingredient of a crafter can go to a satellite of its own
    ADVANCED_SATELLITE(1),
    // the pipe feeds the machines next to it with energy from power providers
    POWER_SUPPLIER_RF(1),
    // energy from power providers may go through the pipe
    POWER_TRANSPORTATION(1),
    // nobody sees what travels through the pipe
    OPAQUE(1),
    // a computer at the pipe gets at the other pipes of the network
    CC_REMOTE_CONTROL(1),
    // a row of sneaky upgrades of its own, arriving items try their faces one after the other
    SNEAKY_COMBINATION(1),
    // a supplier keeps every listed item in a slot of its own
    PATTERN(1),
    // marker slots get flags that let similar items pass for the one in them
    FUZZY(1);

    private final int most;

    UpgradeKind(int most) {
        this.most = most;
    }

    // more than this many do nothing more
    public int most() {
        return most;
    }

    public boolean fitsPipe(PipeKind pipe) {
        return switch (this) {
            case SNEAKY, SPEED, DISCONNECTION, POWER_SUPPLIER_RF, POWER_TRANSPORTATION, OPAQUE, CC_REMOTE_CONTROL, SNEAKY_COMBINATION -> true;
            case MODULE_UPGRADE -> pipe == PipeKind.CHASSIS;
            case PATTERN -> pipe == PipeKind.SUPPLIER;
            case FUZZY -> pipe == PipeKind.CRAFTING || pipe == PipeKind.BASIC;
            case CRAFTING_BYPRODUCT, CRAFTING_CLEANUP, ADVANCED_SATELLITE -> pipe == PipeKind.CRAFTING;
            default -> false;
        };
    }

    public boolean fitsModule(ModuleKind module) {
        return switch (this) {
            case ACTION_SPEED, ITEM_EXTRACTION, ITEM_STACK_EXTRACTION ->
                    module == ModuleKind.EXTRACTOR || module == ModuleKind.ADVANCED_EXTRACTOR;
            case CRAFTING_BYPRODUCT, CRAFTING_CLEANUP, ADVANCED_SATELLITE -> module == ModuleKind.CRAFTER;
            case PATTERN -> module == ModuleKind.ACTIVE_SUPPLIER;
            case FUZZY -> module == ModuleKind.CRAFTER || module == ModuleKind.ITEM_SINK;
            case SNEAKY -> module == ModuleKind.ITEM_SINK || module == ModuleKind.POLYMORPHIC_ITEM_SINK
                    || module == ModuleKind.MOD_ITEM_SINK || module == ModuleKind.TAG_ITEM_SINK
                    || module == ModuleKind.CREATIVE_TAB_ITEM_SINK;
            default -> false;
        };
    }
}
