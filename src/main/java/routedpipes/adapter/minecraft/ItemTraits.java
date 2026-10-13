package routedpipes.adapter.minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

// what the sinks for mods, tags and creative tabs go by, as names a player can read
public final class ItemTraits {

    private static final Map<String, TagKey<Item>> TAGS = new ConcurrentHashMap<>();

    private ItemTraits() {
    }

    public static String namespace(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).getNamespace();
    }

    public static List<String> tags(ItemStack stack) {
        return stack.getTags().map(tag -> tag.location().toString()).sorted().toList();
    }

    public static boolean inTag(Item item, String name) {
        ResourceLocation location = ResourceLocation.tryParse(name);
        if (location == null) {
            return false;
        }
        return item.builtInRegistryHolder().is(TAGS.computeIfAbsent(name, key -> TagKey.create(Registries.ITEM, location)));
    }

    // tabs only know their items once somebody has built them, see the callers
    public static List<String> groups(ItemStack stack) {
        List<String> groups = new ArrayList<>();
        BuiltInRegistries.CREATIVE_MODE_TAB.entrySet().forEach(entry -> {
            if (lists(entry.getValue(), stack)) {
                groups.add(entry.getKey().location().toString());
            }
        });
        return groups;
    }

    public static boolean inGroup(ItemStack stack, String name) {
        ResourceLocation location = ResourceLocation.tryParse(name);
        CreativeModeTab tab = location == null ? null : BuiltInRegistries.CREATIVE_MODE_TAB.get(location);
        return tab != null && lists(tab, stack);
    }

    // the search tab and the like list everything
    private static boolean lists(CreativeModeTab tab, ItemStack stack) {
        return tab.getType() == CreativeModeTab.Type.CATEGORY && tab.contains(stack);
    }
}
