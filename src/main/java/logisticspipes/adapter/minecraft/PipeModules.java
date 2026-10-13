package logisticspipes.adapter.minecraft;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import logisticspipes.domain.crafting.Cleanup;
import logisticspipes.domain.crafting.CraftingSetup;
import logisticspipes.domain.item.ItemType;
import logisticspipes.domain.logistics.Extraction;
import logisticspipes.domain.logistics.ModuleKind;
import logisticspipes.domain.logistics.PatternSupply;
import logisticspipes.domain.logistics.ProviderSettings;
import logisticspipes.domain.logistics.SinkRule;
import logisticspipes.domain.logistics.SneakyFaces;
import logisticspipes.domain.logistics.Supply;
import logisticspipes.domain.logistics.UpgradeKind;
import logisticspipes.domain.routing.Side;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

// the modules of a chassis with the upgrades each of them has, and what they make the pipe do
final class PipeModules {

    private final PipeBlockEntity pipe;
    private final SimpleContainer slots = new SimpleContainer(PipeBlockEntity.MODULE_SLOTS);
    // two slots for every module slot of a chassis, in the order of the modules
    private final SimpleContainer upgrades = new SimpleContainer(PipeBlockEntity.MODULE_SLOTS * PipeBlockEntity.UPGRADES_PER_MODULE);

    PipeModules(PipeBlockEntity pipe) {
        this.pipe = pipe;
    }

    SimpleContainer slots() {
        return slots;
    }

    SimpleContainer upgrades() {
        return upgrades;
    }

    private static int count(Container container, int from, int to, UpgradeKind kind) {
        int count = 0;
        for (int slot = from; slot < to; slot++) {
            if (container.getItem(slot).getItem() instanceof UpgradeItem upgrade && upgrade.kind() == kind) {
                count += container.getItem(slot).getCount();
            }
        }
        return Math.min(count, kind.most());
    }

    // upgrades in the two slots of a module
    private int upgradeCount(int moduleSlot, UpgradeKind kind) {
        return count(upgrades, moduleSlot * PipeBlockEntity.UPGRADES_PER_MODULE,
                (moduleSlot + 1) * PipeBlockEntity.UPGRADES_PER_MODULE, kind);
    }

    // the sneaky face of the sink module that takes the item, empty if none does or it has no sneaky upgrade
    Optional<Side> face(ItemType type) {
        List<SneakyFaces.ModuleFace> sinks = new ArrayList<>();
        boolean any = false;
        boolean looksInside = false;
        for (int slot = 0; slot < pipe.block().moduleSlots(); slot++) {
            int first = slot * PipeBlockEntity.UPGRADES_PER_MODULE;
            Optional<Side> own = PipeUpgrades.sneakyFace(upgrades.getItem(first))
                    .or(() -> PipeUpgrades.sneakyFace(upgrades.getItem(first + 1)));
            for (SinkRule rule : rules(slot)) {
                any |= own.isPresent();
                looksInside |= rule.needsContents();
                sinks.add(new SneakyFaces.ModuleFace(rule, own));
            }
        }
        return any ? SneakyFaces.ofModuleTaking(type, looksInside ? pipe.contents() : Map.of(), sinks) : Optional.empty();
    }

    // how the module in a slot takes items nobody asked for, with a fuzzy upgrade of its own its flags count too
    private List<SinkRule> rules(int slot) {
        List<SinkRule> rules = new ArrayList<>();
        ModuleItem.ruleOf(slots.getItem(slot)).ifPresent(rules::add);
        if (upgradeCount(slot, UpgradeKind.FUZZY) > 0) {
            ModuleItem.fuzzySinkOf(slots.getItem(slot)).ifPresent(rules::add);
        }
        return rules;
    }

    // the modules in the slots this chassis really has
    private List<ItemStack> plugged() {
        List<ItemStack> plugged = new ArrayList<>();
        for (int slot = 0; slot < pipe.block().moduleSlots(); slot++) {
            if (slots.getItem(slot).getItem() instanceof ModuleItem) {
                plugged.add(slots.getItem(slot));
            }
        }
        return plugged;
    }

    boolean has(ModuleKind wanted) {
        return plugged().stream().anyMatch(stack -> ((ModuleItem) stack.getItem()).kind() == wanted);
    }

    ProviderSettings providerSettings() {
        return plugged().stream().filter(stack -> ((ModuleItem) stack.getItem()).kind() == ModuleKind.PROVIDER)
                .findFirst().map(ModuleItem::providerOf).orElse(ProviderSettings.EVERYTHING);
    }

    List<CraftingSetup> crafts() {
        if (pipe.block().moduleSlots() == 0) {
            return List.of();
        }
        List<CraftingSetup> crafts = new ArrayList<>();
        for (int slot = 0; slot < pipe.block().moduleSlots(); slot++) {
            if (!(slots.getItem(slot).getItem() instanceof ModuleItem module) || module.kind() != ModuleKind.CRAFTER) {
                continue;
            }
            ModuleItem.craftingOf(slots.getItem(slot), upgradeCount(slot, UpgradeKind.CRAFTING_BYPRODUCT) > 0,
                    upgradeCount(slot, UpgradeKind.ADVANCED_SATELLITE) > 0, upgradeCount(slot, UpgradeKind.FUZZY) > 0,
                    upgradeCount(slot, UpgradeKind.FLUID_CRAFTING)).ifPresent(crafts::add);
        }
        return crafts;
    }

    Optional<Cleanup> cleanup() {
        for (int slot = 0; slot < pipe.block().moduleSlots(); slot++) {
            Optional<Cleanup> cleanup = ModuleItem.cleanupOf(slots.getItem(slot), upgradeCount(slot, UpgradeKind.CRAFTING_CLEANUP));
            if (cleanup.isPresent()) {
                return cleanup;
            }
        }
        return Optional.empty();
    }

    List<Supply> supplies() {
        // a module with a pattern upgrade goes by slots, see patternSupplies
        List<Supply> supplies = new ArrayList<>();
        for (int slot = 0; slot < pipe.block().moduleSlots(); slot++) {
            if (upgradeCount(slot, UpgradeKind.PATTERN) == 0) {
                ModuleItem.supplyOf(slots.getItem(slot)).ifPresent(supplies::add);
            }
        }
        return supplies;
    }

    List<PatternSupply> patternSupplies() {
        List<PatternSupply> patterns = new ArrayList<>();
        for (int slot = 0; slot < pipe.block().moduleSlots(); slot++) {
            if (upgradeCount(slot, UpgradeKind.PATTERN) > 0) {
                ModuleItem.patternOf(slots.getItem(slot)).ifPresent(patterns::add);
            }
        }
        return patterns;
    }

    List<Extraction> extractions() {
        List<Extraction> extractions = new ArrayList<>();
        for (int slot = 0; slot < pipe.block().moduleSlots(); slot++) {
            int module = slot;
            ModuleItem.extractionOf(slots.getItem(slot)).ifPresent(extraction -> extractions.add(extraction.with(
                    upgradeCount(module, UpgradeKind.ACTION_SPEED), upgradeCount(module, UpgradeKind.ITEM_EXTRACTION),
                    upgradeCount(module, UpgradeKind.ITEM_STACK_EXTRACTION))));
        }
        return extractions.stream().distinct().toList();
    }

    List<SinkRule> sinkRules() {
        List<SinkRule> rules = new ArrayList<>();
        for (int slot = 0; slot < pipe.block().moduleSlots(); slot++) {
            rules.addAll(rules(slot));
        }
        return rules;
    }

    // the settings of a module while it sits in the chassis
    void openSettings(Player player, int slot) {
        ItemStack module = slots.getItem(slot);
        if (slot < pipe.block().moduleSlots() && module.getItem() instanceof ModuleItem item) {
            item.settingsProvider(module, user -> Container.stillValidBlockEntity(pipe, user) && slots.getItem(slot) == module,
                    pipe::settingsChanged, pipe.settings()::importRecipe, upgrade -> upgradeCount(slot, upgrade),
                    kind -> pipe.network().satelliteNames(pipe.routerId(), kind)).ifPresent(player::openMenu);
        }
    }
}
