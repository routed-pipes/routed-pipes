package logisticspipes.application.port;

import java.util.Map;
import java.util.Optional;

import logisticspipes.domain.item.ItemType;

// items as the tables a script works with
public interface ItemTables {

    String NAME = "name";
    // only there for items that carry data of their own, like damage or enchantments
    String DATA = "nbt";

    // the name, and under DATA something that tells two items of the same name apart
    Map<String, Object> describe(ItemType type);

    // the item of that name without any data, empty if there is none
    Optional<ItemType> named(String name);
}
