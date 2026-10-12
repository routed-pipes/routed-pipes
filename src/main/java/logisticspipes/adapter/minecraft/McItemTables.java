package logisticspipes.adapter.minecraft;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import logisticspipes.application.port.ItemTables;
import logisticspipes.domain.item.ItemType;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

// items for scripts: the registry name, the name players see, and a hash of the data if the item has any
public final class McItemTables implements ItemTables {

    public static final String DISPLAY_NAME = "displayName";

    private final HolderLookup.Provider registries;

    public McItemTables(HolderLookup.Provider registries) {
        this.registries = registries;
    }

    @Override
    public Map<String, Object> describe(ItemType type) {
        McItemType item = (McItemType) type;
        Map<String, Object> table = new LinkedHashMap<>();
        table.put(NAME, BuiltInRegistries.ITEM.getKey(item.item()).toString());
        table.put(DISPLAY_NAME, new ItemStack(item.item().builtInRegistryHolder(), 1, item.components()).getHoverName().getString());
        if (!item.components().isEmpty()) {
            table.put(DATA, hash(item.components()));
        }
        return table;
    }

    // the same data gives the same text in every session, like the nbt hash computers show for items
    private String hash(DataComponentPatch components) {
        String written = DataComponentPatch.CODEC.encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), components)
                .getOrThrow().toString();
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(written.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public Optional<ItemType> named(String name) {
        ResourceLocation key = ResourceLocation.tryParse(name);
        if (key == null || !BuiltInRegistries.ITEM.containsKey(key)) {
            return Optional.empty();
        }
        return Optional.of(new McItemType(BuiltInRegistries.ITEM.get(key), DataComponentPatch.EMPTY));
    }
}
