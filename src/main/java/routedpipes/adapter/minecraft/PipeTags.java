package routedpipes.adapter.minecraft;

import routedpipes.domain.crafting.OutputOrder;
import routedpipes.domain.crafting.OutputOrders;
import routedpipes.domain.crafting.SatelliteName;
import routedpipes.domain.item.FuzzyFlags;
import routedpipes.domain.item.FuzzyType;
import routedpipes.domain.item.ItemBatch;
import routedpipes.domain.item.ItemType;
import routedpipes.domain.routing.RouterId;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

// a pipe as nbt: everything it saves but its router and the items inside it, those the pipe writes itself
final class PipeTags {

    private PipeTags() {
    }

    static void save(PipeBlockEntity pipe, CompoundTag tag, HolderLookup.Provider registries) {
        PipeSettings settings = pipe.settings();
        PipeUpgrades upgrades = pipe.pipeUpgrades();
        PipeModules modules = pipe.pipeModules();
        tag.putString("Satellite", settings.satelliteName);
        tag.putBoolean("DefaultRoute", settings.defaultRoute);
        tag.putInt("Mode", settings.mode);
        tag.putInt("Priority", settings.priority);
        ListTag slotNames = new ListTag();
        for (String name : settings.slotSatellites) {
            slotNames.add(StringTag.valueOf(name));
        }
        tag.put("SlotSatellites", slotNames);
        tag.putInt("Disconnected", upgrades.disconnected());
        if (pipe.firewallSettings() != null) {
            tag.put("Firewall", pipe.firewallSettings().save(registries));
        }
        if (pipe.requestTable() != null) {
            tag.put("Table", pipe.requestTable().save(registries));
        }
        if (pipe.diskDrive() != null) {
            tag.put("Disk", ContainerTags.save(pipe.diskDrive().slot(), registries));
        }
        if (pipe.connector() != null) {
            tag.put("Connector", pipe.connector().save(registries));
        }
        if (pipe.frequencySlot() != null) {
            tag.put("Frequency", pipe.frequencySlot().save(registries));
        }
        tag.putBoolean("Exclude", settings.exclude);
        Direction pointed = pipe.surroundings().savedPointed();
        if (pointed != null) {
            tag.putInt("Pointed", pointed.get3DDataValue());
        }
        tag.put("Config", ContainerTags.save(settings.config(), registries));
        tag.put("Modules", ContainerTags.save(modules.slots(), registries));
        tag.put("Upgrades", ContainerTags.save(upgrades.slots(), registries));
        tag.put("ModuleUpgrades", ContainerTags.save(modules.upgrades(), registries));
        tag.put("SneakyUpgrades", ContainerTags.save(upgrades.sneakyRow(), registries));
        tag.put("SlotSettings", settings.slotSettings().save());
        tag.put("FluidCrafting", settings.fluidCrafting().save());
        tag.put("Orders", saveOrders(pipe.outputOrders(), registries));
        if (pipe.fluidPipe() != null) {
            tag.put("Fluids", pipe.fluidPipe().save(registries));
        }
        if (pipe.powerPipe().buffered() > 0) {
            tag.putInt("Power", pipe.powerPipe().buffered());
        }
        tag.putLongArray("Statistics", StatisticsTags.save(pipe.statistics()));
        tag.put("Security", pipe.security().save(registries));
        if (pipe.signs() != null) {
            tag.put("Signs", pipe.signs().save(registries, false));
        }
    }

    private static ListTag saveOrders(OutputOrders outputOrders, HolderLookup.Provider registries) {
        ListTag orders = new ListTag();
        for (OutputOrder order : outputOrders.all()) {
            CompoundTag entry = new CompoundTag();
            if (!order.isExtra()) {
                entry.putUUID("Destination", order.destination().value());
            }
            entry.put("Stack", McItemType.stackOf(new ItemBatch(order.batch().type(), 1)).save(registries));
            if (order.batch().type() instanceof FuzzyType fuzzy) {
                entry.putInt("Fuzzy", fuzzy.flags().bits());
            }
            entry.putInt("Count", order.batch().count());
            entry.putLong("Since", order.since());
            entry.putLong("Placed", order.placed());
            entry.putLong("Request", order.request());
            orders.add(entry);
        }
        return orders;
    }

    static void load(PipeBlockEntity pipe, CompoundTag tag, HolderLookup.Provider registries) {
        PipeSettings settings = pipe.settings();
        PipeUpgrades upgrades = pipe.pipeUpgrades();
        PipeModules modules = pipe.pipeModules();
        // updates for clients carry only the items, and commands may set any part alone, so every part is optional
        if (tag.contains("Satellite")) {
            // worlds from before satellites had names kept a number here
            settings.satelliteName = tag.contains("Satellite", Tag.TAG_STRING) ? tag.getString("Satellite")
                    : SatelliteName.ofNumber(tag.getInt("Satellite"));
        }
        if (tag.contains("DefaultRoute")) {
            settings.defaultRoute = tag.getBoolean("DefaultRoute");
        }
        if (tag.contains("Mode")) {
            settings.mode = tag.getInt("Mode");
        }
        if (pipe.requestTable() != null && tag.contains("Table")) {
            pipe.requestTable().load(tag.getCompound("Table"), registries);
        }
        if (pipe.diskDrive() != null && tag.contains("Disk")) {
            ContainerTags.load(pipe.diskDrive().slot(), tag.getCompound("Disk"), registries);
        }
        if (pipe.frequencySlot() != null && tag.contains("Frequency")) {
            pipe.frequencySlot().load(tag.getCompound("Frequency"), registries);
        }
        if (pipe.connector() != null && tag.contains("Connector")) {
            pipe.connector().load(tag.getCompound("Connector"), registries);
        }
        if (pipe.firewallSettings() != null && tag.contains("Firewall")) {
            pipe.firewallSettings().load(tag.getCompound("Firewall"), registries);
        }
        ListTag slotNames = tag.getList("SlotSatellites", Tag.TAG_STRING);
        int[] slotNumbers = tag.getIntArray("SlotSatellites");
        for (int slot = 0; slot < settings.slotSatellites.length; slot++) {
            if (slot < slotNames.size()) {
                settings.slotSatellites[slot] = slotNames.getString(slot);
            } else if (slot < slotNumbers.length) {
                settings.slotSatellites[slot] = SatelliteName.ofNumber(slotNumbers[slot]);
            }
        }
        if (tag.contains("Priority")) {
            settings.priority = tag.getInt("Priority");
        }
        if (tag.contains("Exclude")) {
            settings.exclude = tag.getBoolean("Exclude");
        }
        if (tag.contains("Pointed")) {
            pipe.surroundings().loadPointed(Direction.from3DDataValue(tag.getInt("Pointed")));
        }
        if (tag.contains("Config")) {
            ContainerTags.load(settings.config(), tag.getCompound("Config"), registries);
        }
        if (tag.contains("Modules")) {
            ContainerTags.load(modules.slots(), tag.getCompound("Modules"), registries);
        }
        if (tag.contains("ModuleUpgrades")) {
            ContainerTags.load(modules.upgrades(), tag.getCompound("ModuleUpgrades"), registries);
        }
        if (tag.contains("SlotSettings")) {
            settings.slotSettings().load(tag.getCompound("SlotSettings"));
        }
        if (tag.contains("FluidCrafting")) {
            settings.fluidCrafting().load(tag.getCompound("FluidCrafting"));
        }
        if (tag.contains("SneakyUpgrades")) {
            ContainerTags.load(upgrades.sneakyRow(), tag.getCompound("SneakyUpgrades"), registries);
        }
        if (tag.contains("Upgrades")) {
            ContainerTags.load(upgrades.slots(), tag.getCompound("Upgrades"), registries);
        }
        // after the upgrades, filling their slots one by one looks like the disconnection upgrade was taken out
        if (tag.contains("Disconnected")) {
            upgrades.loadDisconnected(tag.getInt("Disconnected"));
        }
        if (tag.contains("Power")) {
            pipe.powerPipe().load(tag.getInt("Power"));
        }
        StatisticsTags.load(pipe.statistics(), tag.getLongArray("Statistics"));
        if (tag.contains("Security")) {
            pipe.security().load(tag.getCompound("Security"), registries);
        }
        if (pipe.fluidPipe() != null && tag.contains("Fluids")) {
            pipe.fluidPipe().load(tag.getCompound("Fluids"), registries);
        }
        if (pipe.signs() != null && tag.contains("Signs")) {
            pipe.signs().load(tag.getCompound("Signs"), registries);
        }
        loadOrders(pipe.outputOrders(), tag, registries);
    }

    private static void loadOrders(OutputOrders outputOrders, CompoundTag tag, HolderLookup.Provider registries) {
        if (tag.contains("Orders")) {
            // loading into a pipe that is there, like a command does, replaces its orders
            outputOrders.clear();
        }
        ListTag orders = tag.getList("Orders", Tag.TAG_COMPOUND);
        for (int index = 0; index < orders.size(); index++) {
            CompoundTag entry = orders.getCompound(index);
            ItemStack.parse(registries, entry.getCompound("Stack")).filter(stack -> !stack.isEmpty())
                    .ifPresent(stack -> outputOrders.add(new OutputOrder(
                            entry.hasUUID("Destination") ? new RouterId(entry.getUUID("Destination")) : null,
                            new ItemBatch(orderedType(stack, entry.getInt("Fuzzy")), entry.getInt("Count")), entry.getLong("Since"),
                            // worlds from before orders stood in line go by when they last made progress
                            entry.contains("Placed") ? entry.getLong("Placed") : entry.getLong("Since") * OutputOrder.PLACED_PER_TICK,
                            entry.getLong("Request"))));
        }
    }

    // the item of a saved order, with the fuzzy flags of the result slot it was placed for
    private static ItemType orderedType(ItemStack stack, int fuzzy) {
        ItemType type = McItemType.batchOf(stack).type();
        return fuzzy == 0 ? type : new FuzzyType(type, FuzzyFlags.of(fuzzy));
    }
}
