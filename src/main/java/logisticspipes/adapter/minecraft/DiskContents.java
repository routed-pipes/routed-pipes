package logisticspipes.adapter.minecraft;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import logisticspipes.domain.item.ItemBatch;
import logisticspipes.domain.macro.Macro;
import logisticspipes.domain.macro.MacroDisk;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

// what is written on a disk: its name and its macros
public record DiskContents(String name, List<Entries> macros) {

    // amounts go past a stack, so this is not an item stack
    public record Entry(Holder<Item> item, DataComponentPatch components, int amount) {

        static final Codec<Entry> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ItemStack.ITEM_NON_AIR_CODEC.fieldOf("id").forGetter(Entry::item),
                DataComponentPatch.CODEC.optionalFieldOf("components", DataComponentPatch.EMPTY).forGetter(Entry::components),
                ExtraCodecs.POSITIVE_INT.fieldOf("amount").forGetter(Entry::amount)).apply(instance, Entry::new));

        static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.holderRegistry(Registries.ITEM), Entry::item,
                DataComponentPatch.STREAM_CODEC, Entry::components,
                ByteBufCodecs.VAR_INT, Entry::amount,
                Entry::new);
    }

    public record Entries(String name, List<Entry> items) {

        static final Codec<Entries> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("name").forGetter(Entries::name),
                Entry.CODEC.listOf().fieldOf("items").forGetter(Entries::items)).apply(instance, Entries::new));

        static final StreamCodec<RegistryFriendlyByteBuf, Entries> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Entries::name,
                Entry.STREAM_CODEC.apply(ByteBufCodecs.list()), Entries::items,
                Entries::new);
    }

    public static final Codec<DiskContents> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("name", "").forGetter(DiskContents::name),
            Entries.CODEC.listOf().optionalFieldOf("macros", List.of()).forGetter(DiskContents::macros))
            .apply(instance, DiskContents::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, DiskContents> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, DiskContents::name,
            Entries.STREAM_CODEC.apply(ByteBufCodecs.list()), DiskContents::macros,
            DiskContents::new);

    // the loader has to register this as a data component
    public static final DataComponentType<DiskContents> TYPE = DataComponentType.<DiskContents>builder()
            .persistent(CODEC).networkSynchronized(STREAM_CODEC).build();

    public static DiskContents of(MacroDisk disk) {
        return new DiskContents(disk.name(), disk.macros().stream().map(DiskContents::entries).toList());
    }

    // one macro that may still lack a name or items
    static DiskContents ofDraft(Macro macro) {
        return new DiskContents("", List.of(entries(macro)));
    }

    private static Entries entries(Macro macro) {
        return new Entries(macro.name(), macro.entries().stream().map(DiskContents::entry).toList());
    }

    private static Entry entry(ItemBatch batch) {
        McItemType type = (McItemType) batch.type();
        return new Entry(type.item().builtInRegistryHolder(), type.components(), batch.count());
    }

    public MacroDisk disk() {
        return new MacroDisk(name, macros.stream().map(DiskContents::macro).toList());
    }

    Macro draft() {
        return macros.isEmpty() ? Macro.EMPTY : macro(macros.get(0));
    }

    private static Macro macro(Entries macro) {
        return new Macro(macro.name(), macro.items().stream()
                // a client could send anything
                .filter(entry -> entry.amount() > 0)
                .map(entry -> new ItemBatch(new McItemType(entry.item().value(), entry.components()), entry.amount()))
                .toList());
    }
}
