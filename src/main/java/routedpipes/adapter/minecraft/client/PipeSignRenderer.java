package routedpipes.adapter.minecraft.client;

import java.util.ArrayList;
import java.util.List;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import routedpipes.adapter.minecraft.LogisticsPipeBlock;
import routedpipes.adapter.minecraft.PipeBlockEntity;
import routedpipes.adapter.minecraft.PipeSigns;
import routedpipes.adapter.minecraft.SignView;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.Material;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.level.block.state.properties.WoodType;
import org.joml.Quaternionf;

// draws the signs on the faces of a pipe: the board of a vanilla sign with an item and a few lines on it
final class PipeSignRenderer {

    // the board is a block wide and half a block high
    private static final float BOARD_SCALE = 2f / 3f;
    private static final float BOARD_HALF_DEPTH = BOARD_SCALE / 16f;
    // how far the board is from the middle of the pipe, and how much further when signs meet at a corner
    private static final float DISTANCE = 0.36f;
    private static final float PUSHED_OUT = 0.15f;
    // text is laid out in pixels, 96 across the board
    private static final float PIXEL = 1f / 96f;
    private static final int LINE_HEIGHT = 10;
    private static final int MAX_LINES = 4;
    private static final int ICON_CENTRE = -31;
    private static final int TEXT_CENTRE = 17;
    private static final int TEXT_WIDTH = 58;
    private static final int FULL_WIDTH = 90;
    private static final float ICON_SIZE = 0.3f;
    private static final int INK = 0xFF000000;

    private final ModelPart board;
    private final Material wood = Sheets.getSignMaterial(WoodType.OAK);
    private final Font font;
    private final ItemRenderer itemRenderer;

    PipeSignRenderer(BlockEntityRendererProvider.Context context) {
        // the sign without its stick
        board = context.bakeLayer(ModelLayers.createSignModelName(WoodType.OAK)).getChild("sign");
        font = context.getFont();
        itemRenderer = context.getItemRenderer();
    }

    void render(PipeBlockEntity pipe, PoseStack pose, MultiBufferSource buffers, int light) {
        PipeSigns signs = pipe.signs();
        if (signs == null) {
            return;
        }
        signs.views().forEach((face, view) -> {
            // a sign on a face something is connected to stays out of sight
            if (LogisticsPipeBlock.connected(pipe.getBlockState(), face)) {
                return;
            }
            pose.pushPose();
            pose.translate(0.5f, 0.5f, 0.5f);
            pose.mulPose(outOf(face));
            pose.translate(0, 0, DISTANCE + (signs.pushedOut(face) ? PUSHED_OUT : 0));
            drawBoard(pose, buffers, light);
            drawContent(pipe, view, pose, buffers, light);
            pose.popPose();
        });
    }

    // turns the scene so that south points out of the face
    private static Quaternionf outOf(Direction face) {
        return switch (face) {
            case UP -> Axis.XP.rotationDegrees(-90);
            case DOWN -> Axis.XP.rotationDegrees(90);
            default -> Axis.YP.rotationDegrees(-face.toYRot());
        };
    }

    private void drawBoard(PoseStack pose, MultiBufferSource buffers, int light) {
        pose.pushPose();
        // the model has the board half a block above its origin and upside down
        pose.translate(0, -0.5f * BOARD_SCALE, 0);
        pose.scale(BOARD_SCALE, -BOARD_SCALE, -BOARD_SCALE);
        board.render(pose, wood.buffer(buffers, RenderType::entityCutoutNoCull), light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    private void drawContent(PipeBlockEntity pipe, SignView view, PoseStack pose, MultiBufferSource buffers, int light) {
        boolean icon = !view.icon().isEmpty();
        if (icon) {
            pose.pushPose();
            pose.translate(ICON_CENTRE * PIXEL, 0, BOARD_HALF_DEPTH + 0.01f);
            // nearly flat, like a picture on the board
            pose.scale(ICON_SIZE, ICON_SIZE, 0.01f);
            itemRenderer.renderStatic(view.icon(), ItemDisplayContext.GUI, light, OverlayTexture.NO_OVERLAY, pose, buffers,
                    pipe.getLevel(), 0);
            pose.popPose();
        }
        // long lines go on in the next one as long as the board has room
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (Component line : view.lines()) {
            lines.addAll(font.split(line, icon ? TEXT_WIDTH : FULL_WIDTH));
        }
        if (lines.size() > MAX_LINES) {
            lines = lines.subList(0, MAX_LINES);
        }
        pose.pushPose();
        pose.translate(0, 0, BOARD_HALF_DEPTH + 0.005f);
        pose.scale(PIXEL, -PIXEL, PIXEL);
        int centre = icon ? TEXT_CENTRE : 0;
        int top = -lines.size() * LINE_HEIGHT / 2 + 1;
        for (int index = 0; index < lines.size(); index++) {
            FormattedCharSequence line = lines.get(index);
            font.drawInBatch(line, centre - font.width(line) / 2f, top + index * LINE_HEIGHT, INK, false, pose.last().pose(),
                    buffers, Font.DisplayMode.POLYGON_OFFSET, 0, light);
        }
        pose.popPose();
    }
}
