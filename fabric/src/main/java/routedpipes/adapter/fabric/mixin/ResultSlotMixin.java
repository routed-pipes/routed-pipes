package routedpipes.adapter.fabric.mixin;

import routedpipes.adapter.minecraft.GuideBookGift;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Fabric has no event for a player taking what they crafted
@Mixin(ResultSlot.class)
abstract class ResultSlotMixin {

    @Shadow
    @Final
    private Player player;

    @Shadow
    private int removeCount;

    @Inject(method = "checkTakeAchievements", at = @At("HEAD"))
    private void routedpipes$crafted(ItemStack crafted, CallbackInfo info) {
        if (removeCount > 0) {
            GuideBookGift.crafted(player, crafted);
        }
    }
}
