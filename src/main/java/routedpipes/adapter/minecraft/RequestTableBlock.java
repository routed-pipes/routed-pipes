package routedpipes.adapter.minecraft;

import routedpipes.domain.logistics.PipeKind;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

// a routed pipe in the shape of a whole block
public class RequestTableBlock extends LogisticsPipeBlock {

    public RequestTableBlock(Properties properties, PipeServices services) {
        super(properties, PipeKind.REQUEST_TABLE, 0, services);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.block();
    }
}
