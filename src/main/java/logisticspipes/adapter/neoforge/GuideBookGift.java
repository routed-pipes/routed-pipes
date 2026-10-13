package logisticspipes.adapter.neoforge;

import java.util.function.Supplier;

import com.mojang.serialization.Codec;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

// the first time a player crafts something of the mod they get the guide book
public final class GuideBookGift {

    private static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, LogisticsPipes.MODID);
    // kept when the player dies
    public static final Supplier<AttachmentType<Boolean>> GIVEN = ATTACHMENTS.register("guide_book_given",
            () -> AttachmentType.builder(() -> false).serialize(Codec.BOOL).copyOnDeath().build());

    private GuideBookGift() {
    }

    static void register(IEventBus modEventBus) {
        LogisticsPipes.keepOldIds(ATTACHMENTS);
        ATTACHMENTS.register(modEventBus);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.ItemCraftedEvent.class, GuideBookGift::crafted);
    }

    private static void crafted(PlayerEvent.ItemCraftedEvent event) {
        crafted(event.getEntity(), event.getCrafting());
    }

    public static void crafted(Player player, ItemStack crafted) {
        if (player.level().isClientSide || player.getData(GIVEN) || crafted.isEmpty()
                || !BuiltInRegistries.ITEM.getKey(crafted.getItem()).getNamespace().equals(LogisticsPipes.MODID)) {
            return;
        }
        player.setData(GIVEN, true);
        // whoever crafts the book first has no need for a second one
        if (!crafted.is(LogisticsPipes.GUIDE_BOOK.get())) {
            player.getInventory().placeItemBackInInventory(new ItemStack(LogisticsPipes.GUIDE_BOOK.get()));
        }
    }
}
