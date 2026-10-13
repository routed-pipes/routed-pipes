package logisticspipes.adapter.minecraft;

import logisticspipes.adapter.minecraft.menu.ChassisMenu;
import logisticspipes.adapter.minecraft.menu.FirewallMenu;
import logisticspipes.adapter.minecraft.menu.FrequencyCardMenu;
import logisticspipes.adapter.minecraft.menu.PipeConfigMenu;
import logisticspipes.adapter.minecraft.menu.RequestMenu;
import logisticspipes.adapter.minecraft.menu.RequestTableMenu;
import logisticspipes.adapter.minecraft.menu.UpgradeMenu;
import logisticspipes.application.RequestOutcome;
import logisticspipes.domain.item.ItemBatch;
import logisticspipes.domain.logistics.PipeKind;
import logisticspipes.domain.logistics.UpgradeKind;
import logisticspipes.domain.security.Permission;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;

// the screens a pipe opens
final class PipeMenus {

    private PipeMenus() {
    }

    // what the pipe controller opens
    static MenuProvider upgrades(PipeBlockEntity pipe) {
        return new SimpleMenuProvider((id, inventory, player) -> new UpgradeMenu(pipe.services().upgradeMenu().get(), id,
                inventory, pipe, pipe.security().access(player, Permission.UPGRADE_PIPES).allowed(),
                user -> Container.stillValidBlockEntity(pipe, user)), pipe.getBlockState().getBlock().getName());
    }

    static MenuProvider request(PipeBlockEntity pipe, boolean remote) {
        return new SimpleMenuProvider((id, inventory, player) -> new RequestMenu(pipe.services().requestMenu().get(), id,
                pipe, user -> !pipe.isRemoved() && (remote || Container.stillValidBlockEntity(pipe, user)),
                // the disk is only at hand when standing at the pipe
                remote || pipe.kind() != PipeKind.REQUEST_MK2 ? null : pipe.diskDrive()),
                pipe.getBlockState().getBlock().getName());
    }

    static MenuProvider of(PipeBlockEntity pipe) {
        Component title = pipe.getBlockState().getBlock().getName();
        PipeServices services = pipe.services();
        PipeKind kind = pipe.kind();
        if (pipe.fluidPipe() != null) {
            return pipe.fluidPipe().menuProvider();
        }
        if (kind.requestPipe()) {
            return request(pipe, false);
        }
        if (kind == PipeKind.REQUEST_TABLE) {
            return requestTable(pipe, services, title);
        }
        if (pipe.connector() != null) {
            return pipe.connector().menuProvider();
        }
        if (pipe.frequencySlot() != null) {
            FrequencySlot frequency = pipe.frequencySlot();
            return new SimpleMenuProvider((id, inventory, player) -> new FrequencyCardMenu(services.frequencyCardMenu().get(), id,
                    inventory, frequency.card(), user -> Container.stillValidBlockEntity(pipe, user)), title);
        }
        if (kind == PipeKind.FIREWALL) {
            FirewallSettings firewall = pipe.firewallSettings();
            return new SimpleMenuProvider((id, inventory, player) -> new FirewallMenu(services.firewallMenu().get(), id, inventory,
                    firewall.list(), firewall.data(), user -> Container.stillValidBlockEntity(pipe, user)), title);
        }
        if (kind == PipeKind.CHASSIS) {
            return chassis(pipe, services, title);
        }
        PipeSettings settings = pipe.settings();
        return new SimpleMenuProvider((id, inventory, player) -> new PipeConfigMenu(services.configMenu(kind), id,
                inventory, kind, settings.config(), settings.data(), settings.slotSettings().data(
                        () -> pipe.upgradeCount(UpgradeKind.PATTERN), () -> pipe.upgradeCount(UpgradeKind.FUZZY)),
                user -> Container.stillValidBlockEntity(pipe, user), settings::importRecipe, settings.satelliteSettings(),
                settings.fluidCrafting().data(() -> pipe.upgradeCount(UpgradeKind.FLUID_CRAFTING))), title);
    }

    private static MenuProvider requestTable(PipeBlockEntity pipe, PipeServices services, Component title) {
        RequestTable table = pipe.requestTable();
        DiskDrive drive = pipe.diskDrive();
        PipeSecurity security = pipe.security();
        RequestTableMenu.Actions actions = new RequestTableMenu.Actions() {
            @Override
            public ItemStack result() {
                return table.result();
            }

            @Override
            public void craft() {
                table.craft();
            }

            @Override
            public void order(Player player, int crafts, boolean missingOnly) {
                // opening the table needs the settings permission, ordering from it the one for requests
                if (security.allows(player, Permission.ACTIVE_REQUESTING)) {
                    tellWhatIsMissing(player, table.orderIngredients(crafts, missingOnly));
                }
            }

            @Override
            public void openList(Player player) {
                if (security.allows(player, Permission.ACTIVE_REQUESTING)) {
                    pipe.openRequestScreen(player, false);
                }
            }

            @Override
            public void openDisk(Player player) {
                if (drive.holdsDisk() && security.allows(player, Permission.ACTIVE_REQUESTING)) {
                    drive.open(player);
                }
            }
        };
        return new SimpleMenuProvider((id, inventory, player) -> new RequestTableMenu(services.requestTableMenu().get(), id,
                inventory, table.grid(), table.buffer(), table.sort(), drive.slot(),
                user -> Container.stillValidBlockEntity(pipe, user),
                actions), title);
    }

    private static MenuProvider chassis(PipeBlockEntity pipe, PipeServices services, Component title) {
        PipeModules modules = pipe.pipeModules();
        ContainerData chassis = new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case ChassisMenu.DATA_SLOTS -> pipe.block().moduleSlots();
                    case ChassisMenu.DATA_MODULE_UPGRADES -> pipe.upgradeCount(UpgradeKind.MODULE_UPGRADE);
                    default -> pipe.pointed().map(Direction::get3DDataValue).orElse(-1);
                };
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int getCount() {
                return ChassisMenu.DATA_COUNT;
            }
        };
        return new SimpleMenuProvider((id, inventory, player) -> new ChassisMenu(services.chassisMenu().get(), id,
                inventory, modules.slots(), modules.upgrades(), chassis, user -> Container.stillValidBlockEntity(pipe, user),
                pipe::pointAtNext, modules::openSettings), title);
    }

    static void tellWhatIsMissing(Player player, RequestOutcome outcome) {
        if (outcome.started()) {
            return;
        }
        if (!outcome.powered()) {
            player.displayClientMessage(Component.translatable("logisticspipes.request.no_power"), false);
            return;
        }
        MutableComponent missing = Component.translatable("logisticspipes.request.missing");
        outcome.missing().forEach((type, count) -> missing.append("\n").append(Component.translatable(
                "logisticspipes.request.missing_entry", count, McItemType.stackOf(new ItemBatch(type, 1)).getHoverName())));
        player.displayClientMessage(missing, false);
    }
}
