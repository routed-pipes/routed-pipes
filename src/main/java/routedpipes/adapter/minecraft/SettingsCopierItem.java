package routedpipes.adapter.minecraft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import routedpipes.domain.logistics.ModuleKind;
import routedpipes.domain.logistics.ModuleMatch;
import routedpipes.domain.logistics.PipeKind;
import routedpipes.domain.logistics.Shortened;
import routedpipes.domain.security.Permission;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

// carries the settings of a routed pipe to other pipes of its kind
// sneak and use on a pipe copies, a plain use pastes, the pipe block lets the plain use through
public class SettingsCopierItem extends Item {

    private static final String SETTINGS_TAG = "LogisticsPipesSettings";
    private static final int MAX_MODULES_SHOWN = 3;

    public SettingsCopierItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    private static CompoundTag settingsOf(ItemStack copier) {
        return copier.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag().getCompound(SETTINGS_TAG);
    }

    public static boolean hasSettings(ItemStack copier) {
        return !settingsOf(copier).isEmpty();
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (!(level.getBlockEntity(context.getClickedPos()) instanceof PipeBlockEntity pipe) || pipe.kind() == null) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && (context.getPlayer() == null
                || pipe.security().allows(context.getPlayer(), Permission.CONFIGURE_SETTINGS))) {
            Component done = context.isSecondaryUseActive() ? copy(context.getItemInHand(), pipe) : paste(context.getItemInHand(), pipe);
            if (context.getPlayer() != null) {
                context.getPlayer().displayClientMessage(done, true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // modules stay where they are, only what was set up on them is taken along
    private static Component copy(ItemStack copier, PipeBlockEntity pipe) {
        HolderLookup.Provider registries = pipe.getLevel().registryAccess();
        CompoundTag settings = new CompoundTag();
        settings.putString("Kind", pipe.kind().name());
        settings.putString("Name", pipe.getBlockState().getBlock().getDescriptionId());
        settings.put("Config", ContainerTags.save(pipe.config(), registries));
        settings.putBoolean("DefaultRoute", pipe.defaultRoute());
        settings.putString("Satellite", pipe.satelliteName());
        settings.putInt("Mode", pipe.mode());
        settings.putBoolean("Exclude", pipe.exclude());
        settings.put("PerSlot", pipe.copyPerSlotSettings());
        ListTag modules = new ListTag();
        for (int slot = 0; slot < pipe.modules().getContainerSize(); slot++) {
            ItemStack stack = pipe.modules().getItem(slot);
            if (stack.getItem() instanceof ModuleItem module) {
                CompoundTag entry = new CompoundTag();
                entry.putInt("Slot", slot);
                entry.putString("Kind", module.kind().name());
                entry.putString("Name", stack.getDescriptionId());
                entry.put("Settings", ModuleItem.saveSettings(stack, registries));
                modules.add(entry);
            }
        }
        settings.put("Modules", modules);
        CustomData.update(DataComponents.CUSTOM_DATA, copier, tag -> tag.put(SETTINGS_TAG, settings));
        return Component.translatable("routedpipes.settings_copier.copied", pipe.getBlockState().getBlock().getName());
    }

    // everything goes through the setters of the pipe, so it forgets what it worked out and tells the network
    private static Component paste(ItemStack copier, PipeBlockEntity pipe) {
        CompoundTag settings = settingsOf(copier);
        if (settings.isEmpty()) {
            return Component.translatable("routedpipes.settings_copier.empty");
        }
        if (!settings.getString("Kind").equals(pipe.kind().name())) {
            return Component.translatable("routedpipes.settings_copier.wrong_kind", Component.translatable(settings.getString("Name")));
        }
        HolderLookup.Provider registries = pipe.getLevel().registryAccess();
        ContainerTags.load(pipe.config(), settings.getCompound("Config"), registries);
        pipe.setDefaultRoute(settings.getBoolean("DefaultRoute"));
        // a satellite name another satellite in reach already has is left out
        pipe.pasteSatelliteName(settings.getString("Satellite"));
        pipe.setMode(settings.getInt("Mode"));
        pipe.setExclude(settings.getBoolean("Exclude"));
        // a copier filled by an older version does not have these
        if (settings.contains("PerSlot")) {
            pipe.pastePerSlotSettings(settings.getCompound("PerSlot"));
        }
        if (pipe.kind() != PipeKind.CHASSIS) {
            return Component.translatable("routedpipes.settings_copier.pasted");
        }
        ListTag modules = settings.getList("Modules", Tag.TAG_COMPOUND);
        List<ModuleKind> copied = new ArrayList<>(Collections.nCopies(pipe.modules().getContainerSize(), null));
        List<CompoundTag> copiedSettings = new ArrayList<>(Collections.nCopies(copied.size(), null));
        for (int index = 0; index < modules.size(); index++) {
            CompoundTag entry = modules.getCompound(index);
            int slot = entry.getInt("Slot");
            if (slot >= 0 && slot < copied.size()) {
                copied.set(slot, moduleKind(entry.getString("Kind")));
                copiedSettings.set(slot, entry.getCompound("Settings"));
            }
        }
        List<ModuleKind> present = new ArrayList<>();
        for (int slot = 0; slot < pipe.modules().getContainerSize(); slot++) {
            present.add(pipe.modules().getItem(slot).getItem() instanceof ModuleItem module ? module.kind() : null);
        }
        List<Integer> slots = ModuleMatch.slots(copied, present);
        for (int slot : slots) {
            ItemStack module = pipe.modules().getItem(slot).copy();
            ModuleItem.loadSettings(module, copiedSettings.get(slot), registries);
            pipe.modules().setItem(slot, module);
        }
        return Component.translatable("routedpipes.settings_copier.pasted_modules", slots.size());
    }

    // null for a kind this version does not know
    private static ModuleKind moduleKind(String name) {
        for (ModuleKind kind : ModuleKind.values()) {
            if (kind.name().equals(name)) {
                return kind;
            }
        }
        return null;
    }

    // what the copier holds, one line each, nothing for an empty one
    public static List<Component> stored(ItemStack copier) {
        CompoundTag settings = settingsOf(copier);
        List<Component> lines = new ArrayList<>();
        if (settings.isEmpty()) {
            return lines;
        }
        String kind = settings.getString("Kind");
        int mode = settings.getInt("Mode");
        lines.add(Component.translatable("routedpipes.settings_copier.stored", Component.translatable(settings.getString("Name"))));
        int markers = settings.getCompound("Config").getList("Items", Tag.TAG_COMPOUND).size();
        if (markers > 0) {
            lines.add(Component.translatable("routedpipes.settings_copier.markers", markers));
        }
        if (kind.equals(PipeKind.BASIC.name()) && settings.getBoolean("DefaultRoute")) {
            lines.add(Component.translatable("routedpipes.status.default_route"));
        }
        if (!settings.getString("Satellite").isEmpty()) {
            lines.add(Component.translatable("routedpipes.settings_copier.satellite", settings.getString("Satellite")));
        }
        if (kind.equals(PipeKind.PROVIDER.name())) {
            lines.add(Component.translatable("routedpipes.gui.provider_mode." + mode));
            lines.add(Component.translatable(settings.getBoolean("Exclude") ? "routedpipes.gui.exclude" : "routedpipes.gui.include"));
        }
        if (kind.equals(PipeKind.SUPPLIER.name())) {
            lines.add(Component.translatable("routedpipes.gui.supply_mode." + mode));
        }
        ListTag modules = settings.getList("Modules", Tag.TAG_COMPOUND);
        if (!modules.isEmpty()) {
            List<Component> names = new ArrayList<>();
            for (int index = 0; index < modules.size(); index++) {
                names.add(Component.translatable(modules.getCompound(index).getString("Name")));
            }
            Shortened<Component> shortened = Shortened.of(names, MAX_MODULES_SHOWN);
            List<Component> parts = new ArrayList<>(shortened.shown());
            if (shortened.more() > 0) {
                parts.add(Component.translatable("routedpipes.status.more", shortened.more()));
            }
            lines.add(Component.translatable("routedpipes.status.modules", ComponentUtils.formatList(parts, Component.literal(", "))));
        }
        return lines;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable(getDescriptionId() + ".description").withStyle(ChatFormatting.GRAY));
        stored(stack).forEach(line -> tooltip.add(line.copy().withStyle(ChatFormatting.DARK_GRAY)));
    }
}
