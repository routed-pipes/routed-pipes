package logisticspipes.adapter.minecraft.client;

import java.util.Optional;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import logisticspipes.adapter.minecraft.PlacementPreview;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

// outlines the pipe in the player's hand where it would be placed, with a green square on every side it would connect on
public final class PlacementGhost {

    private static final double MARK_HALF_WIDTH = 0.25;
    private static final double MARK_HALF_DEPTH = 1.0 / 32;

    // looked up once a tick at most, not for every frame
    private static long lookedUpAt = Long.MIN_VALUE;
    private static BlockHitResult lookedUpFor;
    private static Item lookedUpMain;
    private static Item lookedUpOff;
    private static Optional<PlacementPreview.Preview> preview = Optional.empty();

    private PlacementGhost() {
    }

    // hit is the block the player looks at
    public static void draw(BlockHitResult hit, PoseStack pose, MultiBufferSource buffers, Vec3 camera) {
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        Optional<PlacementPreview.Preview> found = previewFor(player, hit);
        if (found.isEmpty()) {
            return;
        }
        BlockPos pos = found.get().pos();
        double x = pos.getX() - camera.x;
        double y = pos.getY() - camera.y;
        double z = pos.getZ() - camera.z;
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        VoxelShape shape = found.get().state().getShape(player.level(), pos, CollisionContext.of(player));
        LevelRenderer.renderVoxelShape(pose, lines, shape, x, y, z, 1, 1, 1, 0.6f, true);
        for (Direction side : found.get().sides()) {
            LevelRenderer.renderLineBox(pose, lines, mark(side).move(x, y, z), 0.2f, 1, 0.3f, 1);
        }
    }

    private static Optional<PlacementPreview.Preview> previewFor(Player player, BlockHitResult hit) {
        long now = player.level().getGameTime();
        Item main = player.getMainHandItem().getItem();
        Item off = player.getOffhandItem().getItem();
        if (now != lookedUpAt || main != lookedUpMain || off != lookedUpOff || lookedUpFor == null
                || !lookedUpFor.getBlockPos().equals(hit.getBlockPos()) || lookedUpFor.getDirection() != hit.getDirection()) {
            lookedUpAt = now;
            lookedUpFor = hit;
            lookedUpMain = main;
            lookedUpOff = off;
            preview = PlacementPreview.of(player, hit);
        }
        return preview;
    }

    // a flat square in the middle of that face of the block
    private static AABB mark(Direction side) {
        double x = 0.5 + side.getStepX() * 0.5;
        double y = 0.5 + side.getStepY() * 0.5;
        double z = 0.5 + side.getStepZ() * 0.5;
        double dx = side.getAxis() == Direction.Axis.X ? MARK_HALF_DEPTH : MARK_HALF_WIDTH;
        double dy = side.getAxis() == Direction.Axis.Y ? MARK_HALF_DEPTH : MARK_HALF_WIDTH;
        double dz = side.getAxis() == Direction.Axis.Z ? MARK_HALF_DEPTH : MARK_HALF_WIDTH;
        return new AABB(x - dx, y - dy, z - dz, x + dx, y + dy, z + dz);
    }
}
