package logisticspipes.adapter.minecraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import logisticspipes.adapter.minecraft.Directions;
import logisticspipes.adapter.minecraft.McItemType;
import logisticspipes.adapter.minecraft.PipeBlockEntity;
import logisticspipes.domain.routing.Side;
import logisticspipes.domain.transport.TravellingItem;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;

// draws the items moving through a pipe
public class PipeItemRenderer implements BlockEntityRenderer<PipeBlockEntity> {

    private static final float ITEM_SCALE = 0.4f;

    private final ItemRenderer itemRenderer;

    public PipeItemRenderer(BlockEntityRendererProvider.Context context) {
        itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(PipeBlockEntity pipe, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        for (TravellingItem item : pipe.travellingItems()) {
            float progress = Math.min(item.progress() + partialTick, item.ticksPerPipe()) / item.ticksPerPipe();
            // first half comes in from the origin side, second half goes out through the exit
            Side side = item.origin();
            float offset = Math.max(0.5f - progress, 0);
            if (progress > 0.5f && item.exit().isPresent()) {
                side = item.exit().get();
                offset = progress - 0.5f;
            }
            Direction direction = Directions.toMinecraft(side);
            pose.pushPose();
            pose.translate(0.5 + direction.getStepX() * offset, 0.5 + direction.getStepY() * offset,
                    0.5 + direction.getStepZ() * offset);
            pose.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);
            itemRenderer.renderStatic(McItemType.stackOf(item.batch()), ItemDisplayContext.FIXED, light,
                    OverlayTexture.NO_OVERLAY, pose, buffers, pipe.getLevel(), 0);
            pose.popPose();
        }
    }
}
