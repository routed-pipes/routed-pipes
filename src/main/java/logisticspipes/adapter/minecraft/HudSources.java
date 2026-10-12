package logisticspipes.adapter.minecraft;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import logisticspipes.domain.hud.HudCategory;
import logisticspipes.domain.hud.HudSource;
import logisticspipes.domain.item.ItemBatch;
import logisticspipes.domain.item.ItemType;
import logisticspipes.domain.logistics.ItemSink;
import logisticspipes.domain.logistics.PipeKind;
import logisticspipes.domain.logistics.SinkRule;
import logisticspipes.domain.power.ProviderStore;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

// reads off a pipe what the glasses show about it
public final class HudSources {

    private HudSources() {
    }

    // cheap, asked for every block entity around a player
    public static Optional<HudCategory> categoryOf(BlockEntity entity) {
        if (entity instanceof PipeBlockEntity pipe && pipe.kind() != null) {
            return HudCategory.of(pipe.kind());
        }
        if (entity instanceof PowerJunctionBlockEntity || entity instanceof PowerProviderBlockEntity) {
            return Optional.of(HudCategory.POWER);
        }
        return Optional.empty();
    }

    public static Optional<HudSource> of(BlockEntity entity) {
        if (entity instanceof PowerJunctionBlockEntity junction) {
            return Optional.of(new HudSource.Power(junction.stored(), PowerJunctionBlockEntity.CAPACITY));
        }
        if (entity instanceof PowerProviderBlockEntity provider) {
            return Optional.of(new HudSource.Power(provider.stored(), ProviderStore.CAPACITY));
        }
        if (!(entity instanceof PipeBlockEntity pipe) || pipe.kind() == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(switch (pipe.kind()) {
            case CHASSIS -> chassis(pipe);
            case BASIC -> new HudSource.Basic(takes(pipe), defaultRoute(pipe));
            case PROVIDER -> new HudSource.Provider(batches(pipe.provided()));
            case SUPPLIER -> new HudSource.Supplier(batches(pipe.supplyTargets()));
            case INVENTORY_SYSTEM_CONNECTOR -> new HudSource.Connector(pipe.connector().expected().stream()
                    .map(McItemType::batchOf).toList(), pipe.connector().ticksEmpty());
            case CRAFTING -> new HudSource.Crafting(pipe.recipe().map(recipe -> recipe.output()),
                    batches(pipe.outputOrders().owedByType()));
            case SATELLITE, FLUID_SATELLITE -> new HudSource.Satellite(pipe.satelliteName(), pipe.kind() == PipeKind.SATELLITE
                    ? batches(pipe.network().onTheWayTo(pipe.routerId(), pipe.getLevel().getGameTime())) : List.of());
            default -> null;
        });
    }

    private static List<ItemBatch> batches(Map<ItemType, Integer> counts) {
        List<ItemBatch> batches = new ArrayList<>();
        counts.forEach((type, count) -> {
            if (count > 0) {
                batches.add(new ItemBatch(type, count));
            }
        });
        return batches;
    }

    private static HudSource chassis(PipeBlockEntity pipe) {
        List<HudSource.Module> modules = new ArrayList<>();
        for (int slot = 0; slot < pipe.modules().getContainerSize(); slot++) {
            ItemStack module = pipe.modules().getItem(slot);
            if (module.getItem() instanceof ModuleItem) {
                List<ItemBatch> items = ModuleItem.filterOf(module).stream().filter(stack -> !stack.isEmpty())
                        .map(McItemType::batchOf).toList();
                modules.add(new HudSource.Module(McItemType.batchOf(module.copyWithCount(1)), items, ModuleItem.namesOf(module)));
            }
        }
        return new HudSource.Chassis(modules, takes(pipe), defaultRoute(pipe));
    }

    private static boolean defaultRoute(PipeBlockEntity pipe) {
        return pipe.sinkRules().stream().anyMatch(rule -> rule instanceof ItemSink sink && sink.defaultRoute());
    }

    // filters are sets, sorted so the panel does not shuffle
    private static List<ItemBatch> takes(PipeBlockEntity pipe) {
        Set<ItemType> wanted = new LinkedHashSet<>();
        for (SinkRule rule : pipe.sinkRules()) {
            wanted.addAll(rule.wants());
        }
        return wanted.stream().map(type -> new ItemBatch(type, 1))
                .sorted(Comparator.comparing(batch -> BuiltInRegistries.ITEM.getKey(McItemType.stackOf(batch).getItem()).toString()))
                .toList();
    }
}
