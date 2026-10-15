package routedpipes.adapter.minecraft;

import routedpipes.adapter.minecraft.content.Content;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

// the first time a player crafts something of the mod they get the guide book
public final class GuideBookGift {

    private GuideBookGift() {
    }

    // the loader calls this whenever a player crafts something
    public static void crafted(Player player, ItemStack crafted) {
        if (player.level().isClientSide || Platform.get().guideBookGiven(player) || crafted.isEmpty()
                || !BuiltInRegistries.ITEM.getKey(crafted.getItem()).getNamespace().equals(Content.MODID)) {
            return;
        }
        Platform.get().markGuideBookGiven(player);
        // whoever crafts the book first has no need for a second one
        if (!crafted.is(Content.GUIDE_BOOK.get())) {
            player.getInventory().placeItemBackInInventory(new ItemStack(Content.GUIDE_BOOK.get()));
        }
    }
}
