package routedpipes.testing;

import java.util.Set;
import java.util.UUID;

import routedpipes.domain.fluid.FluidBatch;
import routedpipes.domain.fluid.FluidType;
import routedpipes.domain.item.ItemBatch;
import routedpipes.domain.item.ItemType;
import routedpipes.domain.routing.RouterId;

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

    // an item with what fuzzy flags look at: how worn it is, what else it carries and the tags it is in
    public record WornItem(String name, int damage, String data, Set<String> tags) implements ItemType {

        public static WornItem fresh(String name, String... tags) {
            return new WornItem(name, 0, "", Set.of(tags));
        }

        public WornItem worn(int by) {
            return new WornItem(name, by, data, tags);
        }

        public WornItem carrying(String more) {
            return new WornItem(name, damage, more, tags);
        }

        @Override
        public ItemType plain() {
            return new WornItem(name, 0, "", tags);
        }

        @Override
        public ItemType undamaged() {
            return new WornItem(name, 0, data, tags);
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
