package routedpipes.domain.item;

import java.util.Set;

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

    // the names of all item tags the item is in
    default Set<String> tags() {
        return Set.of();
    }

    // how worn the item is, 0 for a fresh one
    default int damage() {
        return 0;
    }

    // the same item as good as new, with everything else it carries
    default ItemType undamaged() {
        return this;
    }

    // whether the other item will do where this one is asked for, only a type with fuzzy flags takes more than itself
    default boolean accepts(ItemType other) {
        return equals(other);
    }

    // the item itself where a type with fuzzy flags stands for many
    default ItemType exact() {
        return this;
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
