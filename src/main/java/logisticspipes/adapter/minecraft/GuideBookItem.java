package logisticspipes.adapter.minecraft;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import logisticspipes.domain.guide.GuideChapter;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

// opens the book about the mod, the book itself only exists on the client
public class GuideBookItem extends Item {

    private static final String NAMESPACE = "routedpipes";

    // the client plugs in how the book is opened
    private static Runnable opener = () -> {
    };

    public GuideBookItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static void setOpener(Runnable newOpener) {
        opener = newOpener;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide) {
            opener.run();
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".description").withStyle(ChatFormatting.GRAY));
    }

    // every item of the mod that has a description, by chapter in the order they were registered
    // hasKey tells whether the language has a text for a key
    public static Map<GuideChapter, List<Item>> entries(Predicate<String> hasKey) {
        Map<GuideChapter, List<Item>> entries = new EnumMap<>(GuideChapter.class);
        for (Item item : BuiltInRegistries.ITEM) {
            String id = BuiltInRegistries.ITEM.getKey(item).getPath();
            if (BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(NAMESPACE) && hasKey.test(descriptionKey(item))) {
                entries.computeIfAbsent(GuideChapter.of(id, item instanceof BlockItem), chapter -> new ArrayList<>()).add(item);
            }
        }
        return entries;
    }

    public static String descriptionKey(Item item) {
        return item.getDescriptionId() + ".description";
    }
}
