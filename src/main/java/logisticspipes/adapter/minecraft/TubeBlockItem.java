package logisticspipes.adapter.minecraft;

import logisticspipes.domain.tube.TubePlacement;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.phys.Vec3;

// puts down a whole tube, the clicked block becomes the end nearest to the player
public class TubeBlockItem extends BlockItem {

    private final TubeBlock tube;

    public TubeBlockItem(TubeBlock tube, Properties properties) {
        super(tube, properties);
        this.tube = tube;
    }

    @Override
    public InteractionResult place(BlockPlaceContext context) {
        Player player = context.getPlayer();
        if (player == null || !context.canPlace()) {
            return InteractionResult.FAIL;
        }
        Level level = context.getLevel();
        BlockPos clicked = context.getClickedPos();
        Vec3 look = player.getLookAngle();
        TubePlacement placement = TubePlacement.of(tube.kind(), clicked.getX() + 0.5 - player.getX(),
                clicked.getZ() + 0.5 - player.getZ(), look.x, look.z);
        BlockPos main = Tubes.at(clicked, placement.main());
        if (level.isClientSide) {
            // the server builds it, here it is only about swinging the arm or not
            return tube.fits(level, main, placement.shape(), pos -> true) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
        }
        if (!tube.build(level, main, placement.shape(),
                pos -> player.mayUseItemAt(pos, context.getClickedFace(), context.getItemInHand()))) {
            return InteractionResult.FAIL;
        }
        SoundType sound = tube.defaultBlockState().getSoundType();
        level.playSound(null, clicked, sound.getPlaceSound(), SoundSource.BLOCKS, (sound.getVolume() + 1) / 2, sound.getPitch() * 0.8f);
        context.getItemInHand().consume(1, player);
        return InteractionResult.CONSUME;
    }
}
