package routedpipes.adapter.fabric.client;

import java.util.stream.Stream;

import routedpipes.adapter.minecraft.content.Content;
import routedpipes.domain.logistics.PipeKind;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.minecraft.client.renderer.RenderType;

// the models of pipes and tubes ask for the cutout layer, which Fabric only takes per block
final class CutoutBlocks {

    private CutoutBlocks() {
    }

    static void register() {
        Stream.of(Stream.of(Content.TRANSPORT_PIPE), Content.PIPES.entrySet().stream()
                                .filter(pipe -> pipe.getKey() != PipeKind.REQUEST_TABLE).map(java.util.Map.Entry::getValue),
                        Content.CHASSIS_PIPES.values().stream(), Content.TUBES.values().stream())
                .flatMap(blocks -> blocks)
                .forEach(block -> BlockRenderLayerMap.INSTANCE.putBlock(block.get(), RenderType.cutout()));
    }
}
