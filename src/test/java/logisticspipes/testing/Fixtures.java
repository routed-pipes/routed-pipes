package logisticspipes.testing;

import java.util.Set;
import java.util.UUID;

import logisticspipes.domain.fluid.FluidBatch;
import logisticspipes.domain.fluid.FluidType;
import logisticspipes.domain.item.ItemBatch;
import logisticspipes.domain.item.ItemType;
import logisticspipes.domain.routing.RouterId;

public final class Fixtures {

    public record TestItem(String name) implements ItemType {
    }

    // an item with the things sinks for mods, tags and enchantments look at
    public record TraitItem(String name, String namespace, Set<String> tags, Set<String> groups, boolean enchanted) implements ItemType {

        @Override
        public ItemType plain() {
            return new TestItem(name);
        }

        @Override
        public boolean inTag(String tag) {
            return tags.contains(tag);
        }

        @Override
        public boolean inGroup(String group) {
            return groups.contains(group);
        }
    }

    public record TestFluid(String name) implements FluidType {
    }

    private Fixtures() {
    }

    public static RouterId router(int number) {
        return new RouterId(new UUID(0, number));
    }

    public static ItemBatch batch(String name, int count) {
        return new ItemBatch(new TestItem(name), count);
    }

    public static FluidBatch fluid(String name, int amount) {
        return new FluidBatch(new TestFluid(name), amount);
    }
}
