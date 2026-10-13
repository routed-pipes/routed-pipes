package routedpipes.adapter.minecraft;

import java.util.Locale;

import routedpipes.domain.logistics.PipeKind;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

// the inventory system connector shows on its sides whether it has found the others on its channel
public class ConnectorPipeBlock extends LogisticsPipeBlock {

    // same order as ConnectorLinks.State
    public enum LinkState implements StringRepresentable {
        DISCONNECTED, MISMATCHED, CONNECTED;

        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final EnumProperty<LinkState> LINK = EnumProperty.create("link", LinkState.class);

    public ConnectorPipeBlock(Properties properties, PipeServices services) {
        super(properties, PipeKind.INVENTORY_SYSTEM_CONNECTOR, 0, services);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LINK);
    }
}
