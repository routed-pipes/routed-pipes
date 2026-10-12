package logisticspipes.adapter.minecraft;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

// what is written on a sign: an item to draw, empty for none, and the lines next to it
// the server works it out and clients draw it as it is
public record SignView(ItemStack icon, List<Component> lines) {

    public static final SignView BLANK = new SignView(ItemStack.EMPTY, List.of());

    public SignView {
        lines = List.copyOf(lines);
    }

    boolean sameAs(SignView other) {
        return ItemStack.matches(icon, other.icon) && lines.equals(other.lines);
    }

    CompoundTag save(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        if (!icon.isEmpty()) {
            tag.put("Icon", icon.save(registries));
        }
        ListTag written = new ListTag();
        for (Component line : lines) {
            written.add(StringTag.valueOf(Component.Serializer.toJson(line, registries)));
        }
        tag.put("Lines", written);
        return tag;
    }

    static SignView load(CompoundTag tag, HolderLookup.Provider registries) {
        List<Component> lines = new ArrayList<>();
        ListTag written = tag.getList("Lines", Tag.TAG_STRING);
        for (int index = 0; index < written.size(); index++) {
            Component line = Component.Serializer.fromJson(written.getString(index), registries);
            if (line != null) {
                lines.add(line);
            }
        }
        return new SignView(ItemStack.parseOptional(registries, tag.getCompound("Icon")), lines);
    }
}
