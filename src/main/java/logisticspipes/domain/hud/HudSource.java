package logisticspipes.domain.hud;

import java.util.List;
import java.util.Optional;

import logisticspipes.domain.item.ItemBatch;

// what was read off a pipe or block for its panel
public sealed interface HudSource {

    // every plugged module with the items and names set on it
    record Chassis(List<Module> modules, List<ItemBatch> takes, boolean defaultRoute) implements HudSource {
    }

    record Module(ItemBatch icon, List<ItemBatch> items, List<String> names) {
    }

    record Basic(List<ItemBatch> takes, boolean defaultRoute) implements HudSource {
    }

    record Provider(List<ItemBatch> provided) implements HudSource {
    }

    record Crafting(Optional<ItemBatch> result, List<ItemBatch> orders) implements HudSource {
    }

    record Supplier(List<ItemBatch> targets) implements HudSource {
    }

    // ticksEmpty counts since the connector last waited for something
    record Connector(List<ItemBatch> expected, long ticksEmpty) implements HudSource {
    }

    // in FE
    record Power(long stored, long capacity) implements HudSource {
    }

    // waiting is what is on its way to the satellite
    record Satellite(String name, List<ItemBatch> waiting) implements HudSource {
    }
}
