package logisticspipes.domain.item;

// identity of a stackable item, implementations must have value equality
public interface ItemType {

    // the same item without damage, enchantments and other data
    default ItemType plain() {
        return this;
    }

    // the mod the item comes from
    default String namespace() {
        return "";
    }

    default boolean inTag(String tag) {
        return false;
    }

    // creative tabs
    default boolean inGroup(String group) {
        return false;
    }

    default boolean enchanted() {
        return false;
    }

    // fluid packed for its way through the pipes, no inventory and no item sink takes it
    default boolean packedFluid() {
        return false;
    }
}
