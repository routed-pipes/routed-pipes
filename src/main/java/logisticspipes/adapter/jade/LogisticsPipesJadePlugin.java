package logisticspipes.adapter.jade;

import logisticspipes.adapter.minecraft.LogisticsPipeBlock;
import logisticspipes.adapter.minecraft.PipeBlockEntity;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaCommonRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;
import snownee.jade.api.config.IPluginConfig;

// only loaded when Jade is installed: looking at a pipe shows what it is connected to and what is inside
@WailaPlugin
public class LogisticsPipesJadePlugin implements IWailaPlugin {

    private enum PipeStatus implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
        INSTANCE;

        private static final String ITEMS = "LogisticsPipesItems";
        private static final String ROUTERS = "LogisticsPipesRouters";
        private static final String ENERGY = "LogisticsPipesEnergy";

        @Override
        public ResourceLocation getUid() {
            return ResourceLocation.fromNamespaceAndPath("logisticspipes", "pipe_status");
        }

        @Override
        public void appendServerData(CompoundTag data, BlockAccessor accessor) {
            if (!(accessor.getBlockEntity() instanceof PipeBlockEntity pipe)) {
                return;
            }
            data.putInt(ITEMS, pipe.travellingItems().size());
            pipe.networkStatus().ifPresent(status -> {
                data.putInt(ROUTERS, status.routers());
                data.putInt(ENERGY, status.energy());
            });
        }

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            CompoundTag data = accessor.getServerData();
            if (data.contains(ROUTERS)) {
                tooltip.add(data.getInt(ROUTERS) == 0
                        ? Component.translatable("logisticspipes.status.alone")
                        : Component.translatable("logisticspipes.status.routers", data.getInt(ROUTERS)));
                tooltip.add(Component.translatable("logisticspipes.status.energy", data.getInt(ENERGY)));
            }
            if (data.getInt(ITEMS) > 0) {
                tooltip.add(Component.translatable("logisticspipes.status.items", data.getInt(ITEMS)));
            }
        }
    }

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(PipeStatus.INSTANCE, PipeBlockEntity.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(PipeStatus.INSTANCE, LogisticsPipeBlock.class);
    }
}
