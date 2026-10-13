package routedpipes.adapter.minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import routedpipes.application.computer.ModuleInfo;
import routedpipes.application.computer.PipeCommands;
import routedpipes.application.port.ComputerEvents;
import routedpipes.application.port.ComputerPipe;
import routedpipes.application.port.RouterNode;
import routedpipes.domain.item.ItemBatch;
import routedpipes.domain.logistics.UpgradeKind;
import routedpipes.domain.routing.RouterId;
import routedpipes.domain.security.Access;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;

// a routed pipe for the commands of a computer
public final class PipeComputer implements ComputerPipe {

    private final PipeBlockEntity pipe;

    private PipeComputer(PipeBlockEntity pipe) {
        this.pipe = pipe;
    }

    // empty for plain pipes and on the client
    public static Optional<PipeComputer> of(PipeBlockEntity pipe) {
        boolean routed = pipe.getBlockState().getBlock() instanceof PipeBlock block && block.routed();
        return routed && pipe.getLevel() != null && !pipe.getLevel().isClientSide ? Optional.of(new PipeComputer(pipe)) : Optional.empty();
    }

    public PipeCommands commands(ComputerEvents events) {
        return new PipeCommands(pipe.network(), this, new McItemTables(pipe.getLevel().registryAccess()), events);
    }

    @Override
    public RouterId id() {
        return pipe.routerId();
    }

    @Override
    public RouterNode node() {
        return pipe;
    }

    @Override
    public long now() {
        return pipe.getLevel().getGameTime();
    }

    private static List<ItemBatch> batches(List<ItemStack> stacks) {
        return stacks.stream().filter(stack -> !stack.isEmpty()).map(McItemType::batchOf).toList();
    }

    // the items in the list a basic or a provider pipe keeps in its settings
    private static List<ItemBatch> listed(Container settings) {
        List<ItemStack> listed = new ArrayList<>();
        for (int slot = 0; slot < PipeBlockEntity.OUTPUT_SLOT; slot++) {
            listed.add(settings.getItem(slot));
        }
        return batches(listed);
    }

    @Override
    public Optional<ModuleInfo> module() {
        return ModuleInfo.builtInto(pipe.kind()).map(kind -> ModuleInfo.of(kind, true, listed(pipe.config()), pipe.defaultRoute()));
    }

    @Override
    public void importRecipe() {
        pipe.importRecipe();
    }

    @Override
    public List<Optional<ModuleInfo>> modules() {
        List<Optional<ModuleInfo>> modules = new ArrayList<>();
        int slots = ((PipeBlock) pipe.getBlockState().getBlock()).moduleSlots();
        for (int slot = 0; slot < slots; slot++) {
            ItemStack stack = pipe.modules().getItem(slot);
            modules.add(stack.getItem() instanceof ModuleItem module
                    ? Optional.of(ModuleInfo.of(module.kind(), module.hasSettings(), batches(ModuleItem.filterOf(stack)),
                            ModuleItem.defaultRouteOf(stack)))
                    : Optional.empty());
        }
        return modules;
    }

    @Override
    public Access computerAccess(int computer) {
        return pipe.security().computerAccess(computer);
    }

    @Override
    public boolean remoteControl() {
        return pipe.hasUpgrade(UpgradeKind.CC_REMOTE_CONTROL);
    }

    @Override
    public Optional<ComputerPipe> pipe(RouterId other) {
        return pipe.network().node(other) instanceof PipeBlockEntity found ? of(found).map(ComputerPipe.class::cast) : Optional.empty();
    }
}
