package routedpipes.adapter.minecraft;

import java.util.List;

import routedpipes.domain.macro.Macro;
import routedpipes.domain.macro.MacroDisk;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

// keeps macros, lists of items to order in one go, goes into a Request Pipe Mk2 or a Request Table
public class DiskItem extends Item {

    public DiskItem(Properties properties) {
        super(properties);
    }

    // an empty disk for anything that has nothing written on it
    public static MacroDisk read(ItemStack stack) {
        DiskContents contents = stack.get(DiskContents.TYPE);
        return contents == null ? MacroDisk.EMPTY : contents.disk();
    }

    public static void write(ItemStack stack, MacroDisk disk) {
        stack.set(DiskContents.TYPE, DiskContents.of(disk));
    }

    // a macro that is still being put together travels to the screen on a stack too
    // it cannot be on a disk yet, a disk only takes finished ones
    public static void writeDraft(ItemStack stack, Macro macro) {
        stack.set(DiskContents.TYPE, DiskContents.ofDraft(macro));
    }

    public static Macro readDraft(ItemStack stack) {
        DiskContents contents = stack.get(DiskContents.TYPE);
        return contents == null ? Macro.EMPTY : contents.draft();
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".description").withStyle(ChatFormatting.GRAY));
        String name = read(stack).name();
        if (!name.isEmpty()) {
            tooltip.add(Component.literal(name).withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
