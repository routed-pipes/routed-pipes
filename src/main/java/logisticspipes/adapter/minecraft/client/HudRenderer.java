package logisticspipes.adapter.minecraft.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import logisticspipes.adapter.minecraft.HudGlassesItem;
import logisticspipes.adapter.minecraft.McItemType;
import logisticspipes.adapter.minecraft.ShownHudPanels;
import logisticspipes.domain.hud.HudAction;
import logisticspipes.domain.hud.HudFacing;
import logisticspipes.domain.hud.HudGaze;
import logisticspipes.domain.hud.HudLayout;
import logisticspipes.domain.hud.HudPanel;
import logisticspipes.domain.hud.HudRange;
import logisticspipes.domain.hud.HudShape;
import logisticspipes.domain.hud.HudText;
import logisticspipes.domain.hud.HudView;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11;

// draws the panels of the glasses into the world, what is on them is worked out elsewhere
public final class HudRenderer {

    private static final int BACKGROUND = 0xB0C6C6C6;
    private static final int BUTTON = 0xC08B8B8B;
    private static final int BUTTON_CHOSEN = 0xC0555555;
    private static final int BAR_EMPTY = 0xC0373737;
    private static final int BAR_FILLED = 0xFF4CAF50;
    private static final int TEXT = 0xFF404040;
    private static final int AMOUNT = 0xFFFFFFFF;
    // how far the layers of a panel lie in front of each other, in panel units
    private static final float LAYER = 0.5f;

    private static final int BUTTON_FOCUSED = 0xC0A0A0FF;
    private static final int CROSSHAIR = 0xFF000000;

    // how the player has every panel turned, forgotten with the panel
    private static final Map<BlockPos, HudView> VIEWS = new HashMap<>();
    private static final HudGaze GAZE = new HudGaze();
    // the panel the crosshair is on drawn over everything, while left shift is held
    private static boolean shifted;

    private HudRenderer() {
    }

    // pose has to be the one the level is drawn with, so that 0 0 0 is the camera
    public static void render(PoseStack pose, Camera camera) {
        Minecraft minecraft = Minecraft.getInstance();
        Map<BlockPos, HudPanel> panels = ShownHudPanels.all();
        VIEWS.keySet().retainAll(panels.keySet());
        shifted = false;
        if (panels.isEmpty() || minecraft.player == null || minecraft.screen != null || minecraft.options.hideGui
                || !minecraft.options.getCameraType().isFirstPerson() || !HudGlassesItem.worn(minecraft.player)) {
            GAZE.look(Optional.empty(), Util.getMillis());
            return;
        }
        Vec3 eye = camera.getPosition();
        HudFacing.Vec eyeAt = new HudFacing.Vec(eye.x, eye.y, eye.z);
        List<BlockPos> nearest = HudRange.nearest(panels.keySet(), pos -> Math.sqrt(pos.distToCenterSqr(eye)));
        BlockPos looked = look(nearest, panels, eyeAt, camera);
        shifted = looked != null && InputConstants.isKeyDown(minecraft.getWindow().getWindow(), GLFW.GLFW_KEY_LEFT_SHIFT);
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        // far ones first, they shine through the near ones
        for (int index = nearest.size() - 1; index >= 0; index--) {
            if (!shifted || !nearest.get(index).equals(looked)) {
                drawAt(minecraft, pose, buffers, eyeAt, nearest.get(index), panels.get(nearest.get(index)));
            }
        }
        if (shifted) {
            RenderSystem.clear(GL11.GL_DEPTH_BUFFER_BIT, Minecraft.ON_OSX);
            drawAt(minecraft, pose, buffers, eyeAt, looked, panels.get(looked));
        }
    }

    // finds the nearest panel the crosshair is on and lets the button under it be pressed by looking at it
    private static BlockPos look(List<BlockPos> nearest, Map<BlockPos, HudPanel> panels, HudFacing.Vec eye, Camera camera) {
        HudFacing.Vec look = new HudFacing.Vec(camera.getLookVector().x(), camera.getLookVector().y(), camera.getLookVector().z());
        for (BlockPos pos : nearest) {
            HudPanel panel = panels.get(pos);
            Optional<HudFacing.Spot> spot = facing(eye, pos).cursor(eye, look)
                    .filter(on -> Math.abs(on.x()) <= panel.width() / 2.0 && Math.abs(on.y()) <= panel.height() / 2.0);
            if (spot.isEmpty()) {
                continue;
            }
            HudView view = VIEWS.computeIfAbsent(pos, key -> new HudView());
            Optional<HudGaze.Target> target = HudLayout.place(panel, view).stream()
                    .filter(shape -> shape instanceof HudShape.Button button && button.contains(spot.get().x(), spot.get().y()))
                    .findFirst().map(button -> new HudGaze.Target(pos, ((HudShape.Button) button).action()));
            GAZE.look(target, Util.getMillis()).ifPresent(pressed -> view.apply(pressed.action()));
            return pos;
        }
        GAZE.look(Optional.empty(), Util.getMillis());
        return null;
    }

    private static HudFacing facing(HudFacing.Vec eye, BlockPos pos) {
        Vec3 centre = pos.getCenter();
        return HudFacing.of(eye, new HudFacing.Vec(centre.x, centre.y, centre.z));
    }

    // a black cross over the normal one while a panel is drawn over everything
    public static void drawCrosshair(GuiGraphics graphics) {
        if (shifted) {
            int x = graphics.guiWidth() / 2;
            int y = graphics.guiHeight() / 2;
            graphics.fill(x - 4, y, x + 5, y + 1, CROSSHAIR);
            graphics.fill(x, y - 4, x + 1, y + 5, CROSSHAIR);
        }
    }

    private static void drawAt(Minecraft minecraft, PoseStack pose, MultiBufferSource.BufferSource buffers, HudFacing.Vec eye,
            BlockPos pos, HudPanel panel) {
        HudFacing facing = facing(eye, pos);
        pose.pushPose();
        pose.translate(facing.origin().x() - eye.x(), facing.origin().y() - eye.y(), facing.origin().z() - eye.z());
        // x along the panel to the right, y down it, z out of it towards the player
        pose.mulPose(new Matrix4f(
                (float) facing.right().x(), (float) facing.right().y(), (float) facing.right().z(), 0,
                (float) facing.down().x(), (float) facing.down().y(), (float) facing.down().z(), 0,
                (float) facing.normal().x(), (float) facing.normal().y(), (float) facing.normal().z(), 0,
                0, 0, 0, 1));
        pose.scale((float) HudFacing.UNIT, (float) HudFacing.UNIT, (float) HudFacing.UNIT);
        HudAction focused = GAZE.focused().filter(target -> target.panel().equals(pos)).map(HudGaze.Target::action).orElse(null);
        draw(minecraft, pose, buffers, panel, HudLayout.place(panel, VIEWS.computeIfAbsent(pos, key -> new HudView())), focused);
        pose.popPose();
    }

    private static void draw(Minecraft minecraft, PoseStack pose, MultiBufferSource.BufferSource buffers, HudPanel panel,
            List<HudShape> shapes, HudAction focused) {
        VertexConsumer quads = buffers.getBuffer(RenderType.gui());
        fill(pose, quads, -panel.width() / 2, -panel.height() / 2, panel.width(), panel.height(), 0, BACKGROUND);
        for (HudShape shape : shapes) {
            if (shape instanceof HudShape.Button button) {
                fill(pose, quads, button.x(), button.y(), button.width(), button.height(), LAYER, button.action().equals(focused) ? BUTTON_FOCUSED : button.chosen() ? BUTTON_CHOSEN : BUTTON);
            } else if (shape instanceof HudShape.Bar bar) {
                fill(pose, quads, bar.x(), bar.y(), bar.width(), bar.height(), LAYER, BAR_EMPTY);
                fill(pose, quads, bar.x() + 1, bar.y() + 1, (int) Math.round((bar.width() - 2) * bar.filled()), bar.height() - 2,
                        2 * LAYER, BAR_FILLED);
            }
        }
        buffers.endBatch();
        for (HudShape shape : shapes) {
            if (shape instanceof HudShape.Stack stack) {
                item(minecraft, pose, buffers, stack.x(), stack.y(), stack);
            } else if (shape instanceof HudShape.Button button && button.icon().isPresent()) {
                item(minecraft, pose, buffers, button.x() + (button.width() - 16) / 2, button.y() + (button.height() - 16) / 2,
                        new HudShape.Stack(0, 0, button.icon().get()));
            }
        }
        buffers.endBatch();
        Font font = minecraft.font;
        pose.pushPose();
        pose.translate(0, 0, 4 * LAYER);
        Matrix4f matrix = pose.last().pose();
        for (HudShape shape : shapes) {
            switch (shape) {
                case HudShape.Text text -> text(font, matrix, buffers, text.x(), text.y(), text.width(), component(text.text()),
                        text.centred(), TEXT, false);
                case HudShape.Stack stack -> text(font, matrix, buffers, stack.x() + 17, stack.y() + 9, 32,
                        Component.literal(stack.amount()), false, AMOUNT, true);
                case HudShape.Button button -> text(font, matrix, buffers, button.x() + button.width() / 2,
                        button.y() + (button.height() - 7) / 2, button.width(), Component.literal(button.label()), true, TEXT, false);
                case HudShape.Bar bar -> {
                }
            }
        }
        pose.popPose();
        buffers.endBatch();
    }

    private static Component component(HudText text) {
        return Component.translatable(text.key(), text.args().toArray());
    }

    // the same corners in the same order as a rectangle in a screen
    private static void fill(PoseStack pose, VertexConsumer quads, int x, int y, int width, int height, float z, int colour) {
        Matrix4f matrix = pose.last().pose();
        quads.addVertex(matrix, x, y, z).setColor(colour);
        quads.addVertex(matrix, x, y + height, z).setColor(colour);
        quads.addVertex(matrix, x + width, y + height, z).setColor(colour);
        quads.addVertex(matrix, x + width, y, z).setColor(colour);
    }

    // flat like in a screen, a block that sticks out would poke through the panel
    private static void item(Minecraft minecraft, PoseStack pose, MultiBufferSource buffers, int x, int y, HudShape.Stack stack) {
        pose.pushPose();
        pose.translate(x + 8, y + 8, 3 * LAYER);
        pose.scale(16, -16, 0.5f);
        minecraft.getItemRenderer().renderStatic(McItemType.stackOf(stack.batch()).copyWithCount(1), ItemDisplayContext.GUI,
                LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, pose, buffers, minecraft.level, 0);
        pose.popPose();
    }

    // right is true for text that ends at x, long text is cut to the room it has
    private static void text(Font font, Matrix4f matrix, MultiBufferSource buffers, int x, int y, int width, Component text,
            boolean centred, int colour, boolean right) {
        String shown = font.plainSubstrByWidth(text.getString(), width);
        if (shown.isEmpty()) {
            return;
        }
        float left = right ? x - font.width(shown) : centred ? x - font.width(shown) / 2f : x;
        font.drawInBatch(shown, left, y, colour, right, matrix, buffers, Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
    }
}
