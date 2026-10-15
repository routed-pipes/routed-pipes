package routedpipes.adapter.fabric.mixin;

import routedpipes.adapter.minecraft.PipeProtection;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Fabric has no event for the blocks an explosion is about to destroy
@Mixin(Explosion.class)
abstract class ExplosionMixin {

    @Shadow
    @Final
    private Level level;

    @Shadow
    @Final
    private ObjectArrayList<BlockPos> toBlow;

    @Inject(method = "explode", at = @At(value = "INVOKE",
            target = "Lit/unimi/dsi/fastutil/objects/ObjectArrayList;addAll(Ljava/util/Collection;)Z", shift = At.Shift.AFTER))
    private void routedpipes$spareLockedPipes(CallbackInfo info) {
        PipeProtection.spareFromExplosion(level, toBlow);
    }
}
