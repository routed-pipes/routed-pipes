package logisticspipes.adapter.minecraft.client;

import com.mojang.blaze3d.vertex.PoseStack;
import logisticspipes.adapter.minecraft.McItemType;
import logisticspipes.adapter.minecraft.TubeBlockEntity;
import logisticspipes.domain.transport.TubeItem;
import logisticspipes.domain.transport.TubeTransport;
import logisticspipes.domain.tube.Point;
import logisticspipes.domain.tube.TubeShape;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;

// draws the items moving through a tube
public class TubeItemRenderer implements BlockEntityRenderer<TubeBlockEntity> {

    private static final float ITEM_SCALE = 0.4f;

    private final ItemRenderer itemRenderer;

    public TubeItemRenderer(BlockEntityRendererProvider.Context context) {
        itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(TubeBlockEntity tube, float partialTick, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        TubeShape shape = tube.shape();
        float length = shape.kind().length();
        for (TubeItem item : tube.travellingItems()) {
            float progress = Math.min(item.progress() + partialTick * TubeTransport.SPEED, length) / length;
            // the path is counted from the first end
            Point point = shape.pointAt(item.entry() == 0 ? progress : 1 - progress);
            pose.pushPose();
            pose.translate(point.x(), point.y(), point.z());
            pose.scale(ITEM_SCALE, ITEM_SCALE, ITEM_SCALE);
            itemRenderer.renderStatic(McItemType.stackOf(item.batch()), ItemDisplayContext.FIXED, light,
                    OverlayTexture.NO_OVERLAY, pose, buffers, tube.getLevel(), 0);
            pose.popPose();
        }
    }

    // the items can be several blocks away from the main block that draws them
    @Override
    public boolean shouldRenderOffScreen(TubeBlockEntity tube) {
        return true;
    }
}
