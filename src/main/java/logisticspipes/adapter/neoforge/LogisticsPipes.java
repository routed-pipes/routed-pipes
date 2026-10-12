package logisticspipes.adapter.neoforge;

import logisticspipes.domain.logistics.UpgradeKind;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

import logisticspipes.adapter.minecraft.CompilerDiskContents;
import logisticspipes.adapter.minecraft.ConnectorPipeBlock;
import logisticspipes.adapter.minecraft.CraftingTableBlock;
import logisticspipes.adapter.minecraft.CraftingTableBlockEntity;
import logisticspipes.adapter.minecraft.CraftingTableServices;
import logisticspipes.adapter.minecraft.DiskContents;
import logisticspipes.adapter.minecraft.DiskItem;
import logisticspipes.adapter.minecraft.DiskMenus;
import logisticspipes.adapter.minecraft.FluidContainerItem;
import logisticspipes.adapter.minecraft.FrequencyCardItem;
import logisticspipes.adapter.minecraft.GuideBookItem;
import logisticspipes.adapter.minecraft.FluidContents;
import logisticspipes.adapter.minecraft.FluidServices;
import logisticspipes.adapter.minecraft.LevelNetworks;
import logisticspipes.adapter.minecraft.LogisticsPipeBlock;
import logisticspipes.adapter.minecraft.ModuleItem;
import logisticspipes.adapter.minecraft.PipeBlockEntity;
import logisticspipes.adapter.minecraft.PipeControllerItem;
import logisticspipes.adapter.minecraft.PipeServices;
import logisticspipes.adapter.minecraft.PowerJunctionBlock;
import logisticspipes.adapter.minecraft.PowerJunctionBlockEntity;
import logisticspipes.adapter.minecraft.PowerProviderBlock;
import logisticspipes.adapter.minecraft.PowerProviderBlockEntity;
import logisticspipes.adapter.minecraft.ProgramCompilerBlock;
import logisticspipes.adapter.minecraft.ProgramCompilerBlockEntity;
import logisticspipes.adapter.minecraft.ProgramCompilerServices;
import logisticspipes.adapter.minecraft.ProgrammedRecipe;
import logisticspipes.adapter.minecraft.ProgrammerItem;
import logisticspipes.adapter.minecraft.RemoteOrdererItem;
import logisticspipes.adapter.minecraft.RequestTableBlock;
import logisticspipes.adapter.minecraft.SettingsCopierItem;
import logisticspipes.adapter.minecraft.SignCreatorItem;
import logisticspipes.adapter.minecraft.TubeBlock;
import logisticspipes.adapter.minecraft.TubeBlockEntity;
import logisticspipes.adapter.minecraft.TubeBlockItem;
import logisticspipes.adapter.minecraft.TubePartBlock;
import logisticspipes.adapter.minecraft.TubePartBlockEntity;
import logisticspipes.adapter.minecraft.TubeServices;
import logisticspipes.adapter.minecraft.SecurityCardItem;
import logisticspipes.adapter.minecraft.SecurityStationBlock;
import logisticspipes.adapter.minecraft.SecurityStationBlockEntity;
import logisticspipes.adapter.minecraft.SecurityStationServices;
import logisticspipes.adapter.minecraft.StatisticsTableBlock;
import logisticspipes.adapter.minecraft.StatisticsTableBlockEntity;
import logisticspipes.adapter.minecraft.StatisticsTableServices;
import logisticspipes.adapter.minecraft.UpgradeItem;
import logisticspipes.adapter.minecraft.menu.ChannelMenu;
import logisticspipes.adapter.minecraft.menu.ChassisMenu;
import logisticspipes.adapter.minecraft.menu.DiskMenu;
import logisticspipes.adapter.minecraft.menu.FirewallMenu;
import logisticspipes.adapter.minecraft.menu.MacroEditMenu;
import logisticspipes.adapter.minecraft.menu.FrequencyCardMenu;
import logisticspipes.adapter.minecraft.menu.CraftingTableMenu;
import logisticspipes.adapter.minecraft.menu.FluidConfigMenu;
import logisticspipes.adapter.minecraft.menu.FluidRequestMenu;
import logisticspipes.adapter.minecraft.menu.MenuRequests;
import logisticspipes.adapter.minecraft.menu.NamesMenu;
import logisticspipes.adapter.minecraft.menu.PipeConfigMenu;
import logisticspipes.adapter.minecraft.menu.ProgramCompilerMenu;
import logisticspipes.adapter.minecraft.menu.RequestMenu;
import logisticspipes.adapter.minecraft.menu.RequestTableMenu;
import logisticspipes.adapter.minecraft.menu.SecurityStationMenu;
import logisticspipes.adapter.minecraft.menu.SignItemMenu;
import logisticspipes.adapter.minecraft.menu.StatisticsTableMenu;
import logisticspipes.adapter.minecraft.menu.UpgradeMenu;
import logisticspipes.adapter.neoforge.benchmark.Benchmark;
import logisticspipes.adapter.neoforge.gametest.TestBattery;
import logisticspipes.adapter.neoforge.gametest.TestTank;
import logisticspipes.domain.logistics.ModuleKind;
import logisticspipes.domain.logistics.PipeKind;
import logisticspipes.domain.tube.TubeKind;
import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.slf4j.Logger;

// composition root, the only place that knows every layer
@Mod(LogisticsPipes.MODID)
public class LogisticsPipes {

    public static final String MODID = "logisticspipes";
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MODID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);
    private static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, MODID);
    private static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, MODID);

    public static final Supplier<MenuType<RequestMenu>> REQUEST_MENU = MENUS.register("request",
            () -> new MenuType<>((id, inventory) -> new RequestMenu(LogisticsPipes.REQUEST_MENU.get(), id),
                    FeatureFlags.DEFAULT_FLAGS));
    public static final Map<PipeKind, Supplier<MenuType<PipeConfigMenu>>> CONFIG_MENUS = new EnumMap<>(PipeKind.class);

    static {
        CONFIG_MENUS.put(PipeKind.BASIC, configMenu("basic", PipeKind.BASIC));
        CONFIG_MENUS.put(PipeKind.PROVIDER, configMenu("provider", PipeKind.PROVIDER));
        CONFIG_MENUS.put(PipeKind.SUPPLIER, configMenu("supplier", PipeKind.SUPPLIER));
        CONFIG_MENUS.put(PipeKind.CRAFTING, configMenu("crafting", PipeKind.CRAFTING));
        CONFIG_MENUS.put(PipeKind.SATELLITE, configMenu("satellite", PipeKind.SATELLITE));
    }

    public static final Supplier<MenuType<CraftingTableMenu>> CRAFTING_TABLE_MENU = MENUS.register("crafting_table",
            () -> new MenuType<>((id, inventory) -> new CraftingTableMenu(LogisticsPipes.CRAFTING_TABLE_MENU.get(), id, inventory),
                    FeatureFlags.DEFAULT_FLAGS));

    public static final Supplier<MenuType<NamesMenu>> NAMES_MENU = MENUS.register("names",
            () -> new MenuType<>((id, inventory) -> new NamesMenu(LogisticsPipes.NAMES_MENU.get(), id, inventory),
                    FeatureFlags.DEFAULT_FLAGS));

    public static final Supplier<MenuType<UpgradeMenu>> UPGRADE_MENU = MENUS.register("upgrades",
            () -> new MenuType<>((id, inventory) -> new UpgradeMenu(LogisticsPipes.UPGRADE_MENU.get(), id, inventory),
                    FeatureFlags.DEFAULT_FLAGS));

    public static final Supplier<MenuType<FirewallMenu>> FIREWALL_MENU = MENUS.register("firewall",
            () -> new MenuType<>((id, inventory) -> new FirewallMenu(LogisticsPipes.FIREWALL_MENU.get(), id, inventory),
                    FeatureFlags.DEFAULT_FLAGS));

    public static final Supplier<MenuType<RequestTableMenu>> REQUEST_TABLE_MENU = MENUS.register("request_table",
            () -> new MenuType<>((id, inventory) -> new RequestTableMenu(LogisticsPipes.REQUEST_TABLE_MENU.get(), id, inventory),
                    FeatureFlags.DEFAULT_FLAGS));

    public static final Supplier<MenuType<ChassisMenu>> CHASSIS_MENU = MENUS.register("chassis",
            () -> new MenuType<>((id, inventory) -> new ChassisMenu(LogisticsPipes.CHASSIS_MENU.get(), id, inventory),
                    FeatureFlags.DEFAULT_FLAGS));

    public static final Map<PipeKind, Supplier<MenuType<FluidConfigMenu>>> FLUID_CONFIG_MENUS = new EnumMap<>(PipeKind.class);

    static {
        FLUID_CONFIG_MENUS.put(PipeKind.FLUID_BASIC, fluidConfigMenu("fluid_basic", PipeKind.FLUID_BASIC));
        FLUID_CONFIG_MENUS.put(PipeKind.FLUID_TERMINUS, fluidConfigMenu("fluid_terminus", PipeKind.FLUID_TERMINUS));
        FLUID_CONFIG_MENUS.put(PipeKind.FLUID_SATELLITE, fluidConfigMenu("fluid_satellite", PipeKind.FLUID_SATELLITE));
        FLUID_CONFIG_MENUS.put(PipeKind.FLUID_SUPPLIER, fluidConfigMenu("fluid_supplier", PipeKind.FLUID_SUPPLIER));
        FLUID_CONFIG_MENUS.put(PipeKind.FLUID_CONTAINER_SUPPLIER,
                fluidConfigMenu("fluid_container_supplier", PipeKind.FLUID_CONTAINER_SUPPLIER));
    }

    public static final Supplier<MenuType<FluidRequestMenu>> FLUID_REQUEST_MENU = MENUS.register("fluid_request",
            () -> new MenuType<>((id, inventory) -> new FluidRequestMenu(LogisticsPipes.FLUID_REQUEST_MENU.get(), id),
                    FeatureFlags.DEFAULT_FLAGS));

    private static final FluidServices FLUIDS = new FluidServices(new CapabilityFluidStorage(), FLUID_CONFIG_MENUS,
            FLUID_REQUEST_MENU);

    public static final Supplier<MenuType<DiskMenu>> DISK_MENU = MENUS.register("disk",
            () -> new MenuType<>((id, inventory) -> new DiskMenu(LogisticsPipes.DISK_MENU.get(), id),
                    FeatureFlags.DEFAULT_FLAGS));
    public static final Supplier<MenuType<MacroEditMenu>> MACRO_EDIT_MENU = MENUS.register("macro_edit",
            () -> new MenuType<>((id, inventory) -> new MacroEditMenu(LogisticsPipes.MACRO_EDIT_MENU.get(), id),
                    FeatureFlags.DEFAULT_FLAGS));
    private static final DiskMenus DISK_MENUS = new DiskMenus(DISK_MENU, MACRO_EDIT_MENU);

    // the channels of inventory system connectors, as a connector shows them and as a security station does
    public static final Supplier<MenuType<ChannelMenu>> CONNECTOR_MENU = MENUS.register("connector",
            () -> new MenuType<>((id, inventory) -> new ChannelMenu(LogisticsPipes.CONNECTOR_MENU.get(), id, inventory, true),
                    FeatureFlags.DEFAULT_FLAGS));
    public static final Supplier<MenuType<ChannelMenu>> CHANNEL_MENU = MENUS.register("channels",
            () -> new MenuType<>((id, inventory) -> new ChannelMenu(LogisticsPipes.CHANNEL_MENU.get(), id, inventory, false),
                    FeatureFlags.DEFAULT_FLAGS));

    public static final Supplier<MenuType<FrequencyCardMenu>> FREQUENCY_CARD_MENU = MENUS.register("frequency_card",
            () -> new MenuType<>((id, inventory) -> new FrequencyCardMenu(LogisticsPipes.FREQUENCY_CARD_MENU.get(), id, inventory),
                    FeatureFlags.DEFAULT_FLAGS));

    private static final PipeServices SERVICES = new PipeServices(() -> LogisticsPipes.PIPE_BLOCK_ENTITY.get(),
            new CapabilityItemStorage(), new LevelNetworks(), REQUEST_MENU, CHASSIS_MENU, UPGRADE_MENU, FIREWALL_MENU,
            REQUEST_TABLE_MENU, CONFIG_MENUS, FLUIDS, DISK_MENUS,
            new CapabilityEnergyAccess(), CONNECTOR_MENU, FREQUENCY_CARD_MENU);

    public static final DeferredBlock<LogisticsPipeBlock> TRANSPORT_PIPE = pipe("transport_pipe", null, 0);
    public static final Map<PipeKind, DeferredBlock<LogisticsPipeBlock>> LOGISTICS_PIPES = new EnumMap<>(PipeKind.class);

    static {
        LOGISTICS_PIPES.put(PipeKind.BASIC, pipe("basic_logistics_pipe", PipeKind.BASIC, 0));
        LOGISTICS_PIPES.put(PipeKind.PROVIDER, pipe("provider_pipe", PipeKind.PROVIDER, 0));
        LOGISTICS_PIPES.put(PipeKind.REQUEST, pipe("request_pipe", PipeKind.REQUEST, 0));
        LOGISTICS_PIPES.put(PipeKind.REQUEST_MK2, pipe("request_pipe_mk2", PipeKind.REQUEST_MK2, 0));
        LOGISTICS_PIPES.put(PipeKind.SUPPLIER, pipe("supplier_pipe", PipeKind.SUPPLIER, 0));
        LOGISTICS_PIPES.put(PipeKind.CRAFTING, pipe("crafting_pipe", PipeKind.CRAFTING, 0));
        LOGISTICS_PIPES.put(PipeKind.SATELLITE, pipe("satellite_pipe", PipeKind.SATELLITE, 0));
        LOGISTICS_PIPES.put(PipeKind.FIREWALL, pipe("firewall_pipe", PipeKind.FIREWALL, 0));
        DeferredBlock<LogisticsPipeBlock> table = BLOCKS.register("request_table", () -> new RequestTableBlock(
                BlockBehaviour.Properties.of().strength(2.5f).sound(SoundType.METAL), SERVICES));
        ITEMS.registerSimpleBlockItem(table);
        LOGISTICS_PIPES.put(PipeKind.REQUEST_TABLE, table);
        LOGISTICS_PIPES.put(PipeKind.FLUID_BASIC, pipe("fluid_basic_pipe", PipeKind.FLUID_BASIC, 0));
        LOGISTICS_PIPES.put(PipeKind.FLUID_INSERTION, pipe("fluid_insertion_pipe", PipeKind.FLUID_INSERTION, 0));
        LOGISTICS_PIPES.put(PipeKind.FLUID_EXTRACTOR, pipe("fluid_extractor_pipe", PipeKind.FLUID_EXTRACTOR, 0));
        LOGISTICS_PIPES.put(PipeKind.FLUID_PROVIDER, pipe("fluid_provider_pipe", PipeKind.FLUID_PROVIDER, 0));
        LOGISTICS_PIPES.put(PipeKind.FLUID_TERMINUS, pipe("fluid_terminus_pipe", PipeKind.FLUID_TERMINUS, 0));
        LOGISTICS_PIPES.put(PipeKind.FLUID_REQUEST, pipe("fluid_request_pipe", PipeKind.FLUID_REQUEST, 0));
        LOGISTICS_PIPES.put(PipeKind.FLUID_SATELLITE, pipe("fluid_satellite_pipe", PipeKind.FLUID_SATELLITE, 0));
        LOGISTICS_PIPES.put(PipeKind.FLUID_SUPPLIER, pipe("fluid_supplier_pipe", PipeKind.FLUID_SUPPLIER, 0));
        LOGISTICS_PIPES.put(PipeKind.FLUID_CONTAINER_SUPPLIER,
                pipe("fluid_container_supplier_pipe", PipeKind.FLUID_CONTAINER_SUPPLIER, 0));
        DeferredBlock<LogisticsPipeBlock> connector = BLOCKS.register("inventory_system_connector_pipe",
                () -> new ConnectorPipeBlock(pipeProperties(), SERVICES));
        ITEMS.registerSimpleBlockItem(connector);
        LOGISTICS_PIPES.put(PipeKind.INVENTORY_SYSTEM_CONNECTOR, connector);
        LOGISTICS_PIPES.put(PipeKind.SYSTEM_ENTRANCE, pipe("system_entrance_pipe", PipeKind.SYSTEM_ENTRANCE, 0));
        LOGISTICS_PIPES.put(PipeKind.SYSTEM_DESTINATION, pipe("system_destination_pipe", PipeKind.SYSTEM_DESTINATION, 0));
    }

    // chassis mk1 to mk5 by number of module slots
    public static final Map<Integer, DeferredBlock<LogisticsPipeBlock>> CHASSIS_PIPES = new LinkedHashMap<>();
    private static final int[] CHASSIS_SLOTS = {1, 2, 3, 4, 8};

    static {
        for (int tier = 0; tier < CHASSIS_SLOTS.length; tier++) {
            CHASSIS_PIPES.put(CHASSIS_SLOTS[tier], pipe("chassis_pipe_mk" + (tier + 1), PipeKind.CHASSIS, CHASSIS_SLOTS[tier]));
        }
    }

    public static final Map<ModuleKind, DeferredItem<ModuleItem>> MODULES = new EnumMap<>(ModuleKind.class);

    static {
        for (ModuleKind kind : ModuleKind.values()) {
            MODULES.put(kind, ITEMS.register("module_" + kind.name().toLowerCase(Locale.ROOT),
                    () -> new ModuleItem(new Item.Properties(), kind, ModuleItem.settingsLayoutFor(kind).orElse(null),
                            ModuleItem.settingsLayoutFor(kind).map(CONFIG_MENUS::get).orElse(null),
                            ModuleItem.listsNames(kind) ? NAMES_MENU : null)));
        }
    }

    public static final Map<UpgradeKind, DeferredItem<UpgradeItem>> UPGRADES = new EnumMap<>(UpgradeKind.class);

    static {
        for (UpgradeKind kind : UpgradeKind.values()) {
            UPGRADES.put(kind, ITEMS.register("upgrade_" + kind.name().toLowerCase(Locale.ROOT),
                    () -> new UpgradeItem(new Item.Properties(), kind)));
        }
    }

    public static final DeferredItem<PipeControllerItem> PIPE_CONTROLLER = ITEMS.register("pipe_controller",
            () -> new PipeControllerItem(new Item.Properties()));

    public static final DeferredItem<FrequencyCardItem> FREQUENCY_CARD = ITEMS.register("frequency_card",
            () -> new FrequencyCardItem(new Item.Properties()));

    public static final DeferredItem<RemoteOrdererItem> REMOTE_ORDERER = ITEMS.register("remote_orderer",
            () -> new RemoteOrdererItem(new Item.Properties()));

    public static final DeferredItem<SettingsCopierItem> SETTINGS_COPIER = ITEMS.register("settings_copier",
            () -> new SettingsCopierItem(new Item.Properties()));

    public static final Supplier<MenuType<SignItemMenu>> SIGN_ITEM_MENU = MENUS.register("sign_item",
            () -> new MenuType<>((id, inventory) -> new SignItemMenu(LogisticsPipes.SIGN_ITEM_MENU.get(), id, inventory),
                    FeatureFlags.DEFAULT_FLAGS));
    public static final DeferredItem<SignCreatorItem> SIGN_CREATOR = ITEMS.register("sign_creator",
            () -> new SignCreatorItem(new Item.Properties(), SIGN_ITEM_MENU));

    static {
        DATA_COMPONENTS.register("sign_mode", () -> SignCreatorItem.MODE);
    }

    public static final DeferredItem<GuideBookItem> GUIDE_BOOK = ITEMS.register("guide_book",
            () -> new GuideBookItem(new Item.Properties()));

    // never in a player's hands, so it is in no creative tab and has no recipe
    public static final DeferredItem<FluidContainerItem> FLUID_CONTAINER = ITEMS.register("fluid_container",
            () -> new FluidContainerItem(new Item.Properties(), FLUIDS.storage()));

    public static final DeferredItem<DiskItem> DISK = ITEMS.register("disk",
            () -> new DiskItem(new Item.Properties().stacksTo(1)));

    static {
        DATA_COMPONENTS.register("disk", () -> DiskContents.TYPE);
    }

    static {
        DATA_COMPONENTS.register("fluid", () -> FluidContents.TYPE);
    }

    public static final Supplier<BlockEntityType<PipeBlockEntity>> PIPE_BLOCK_ENTITY = BLOCK_ENTITIES.register("pipe",
            () -> BlockEntityType.Builder
                    .of(LogisticsPipes::newPipe, Stream.of(Stream.of(TRANSPORT_PIPE), LOGISTICS_PIPES.values().stream(),
                            CHASSIS_PIPES.values().stream()).flatMap(pipes -> pipes).map(Supplier::get).toArray(Block[]::new))
                    .build(null));

    public static final DeferredBlock<CraftingTableBlock> CRAFTING_TABLE = BLOCKS.register("logistics_crafting_table",
            () -> new CraftingTableBlock(BlockBehaviour.Properties.of().strength(2.5f).sound(SoundType.METAL),
                    new CraftingTableServices(() -> LogisticsPipes.CRAFTING_TABLE_BLOCK_ENTITY.get(), CRAFTING_TABLE_MENU)));
    public static final Supplier<BlockEntityType<CraftingTableBlockEntity>> CRAFTING_TABLE_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("crafting_table", () -> BlockEntityType.Builder
                    .of((pos, state) -> new CraftingTableBlockEntity(LogisticsPipes.CRAFTING_TABLE_BLOCK_ENTITY.get(), pos, state),
                            CRAFTING_TABLE.get())
                    .build(null));

    public static final DeferredBlock<PowerJunctionBlock> POWER_JUNCTION = BLOCKS.register("power_junction",
            () -> new PowerJunctionBlock(BlockBehaviour.Properties.of().strength(2.5f).sound(SoundType.METAL),
                    () -> LogisticsPipes.POWER_JUNCTION_BLOCK_ENTITY.get()));
    public static final Supplier<BlockEntityType<PowerJunctionBlockEntity>> POWER_JUNCTION_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("power_junction", () -> BlockEntityType.Builder
                    .of((pos, state) -> new PowerJunctionBlockEntity(LogisticsPipes.POWER_JUNCTION_BLOCK_ENTITY.get(), pos, state),
                            POWER_JUNCTION.get())
                    .build(null));

    public static final DeferredBlock<PowerProviderBlock> POWER_PROVIDER_RF = BLOCKS.register("power_provider_rf",
            () -> new PowerProviderBlock(BlockBehaviour.Properties.of().strength(2.5f).sound(SoundType.METAL),
                    () -> LogisticsPipes.POWER_PROVIDER_BLOCK_ENTITY.get(), SERVICES.networks()));
    public static final Supplier<BlockEntityType<PowerProviderBlockEntity>> POWER_PROVIDER_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("power_provider", () -> BlockEntityType.Builder
                    .of((pos, state) -> new PowerProviderBlockEntity(LogisticsPipes.POWER_PROVIDER_BLOCK_ENTITY.get(), pos, state),
                            POWER_PROVIDER_RF.get())
                    .build(null));

    private static final TubeServices TUBE_SERVICES = new TubeServices(() -> LogisticsPipes.TUBE_BLOCK_ENTITY.get(),
            () -> LogisticsPipes.TUBE_PART_BLOCK_ENTITY.get(), () -> LogisticsPipes.TUBE_PART.get(), SERVICES.networks());

    public static final Map<TubeKind, DeferredBlock<TubeBlock>> TUBES = new EnumMap<>(TubeKind.class);

    static {
        for (TubeKind kind : TubeKind.values()) {
            String name = kind.name().toLowerCase(Locale.ROOT) + "_tube";
            DeferredBlock<TubeBlock> tube = BLOCKS.register(name, () -> new TubeBlock(tubeProperties(), kind, TUBE_SERVICES));
            ITEMS.register(name, () -> new TubeBlockItem(tube.get(), new Item.Properties()));
            TUBES.put(kind, tube);
        }
    }

    public static final DeferredBlock<TubePartBlock> TUBE_PART = BLOCKS.register("tube_part",
            () -> new TubePartBlock(tubeProperties().noLootTable(), TUBE_SERVICES));
    public static final Supplier<BlockEntityType<TubeBlockEntity>> TUBE_BLOCK_ENTITY = BLOCK_ENTITIES.register("tube",
            () -> BlockEntityType.Builder
                    .of((pos, state) -> new TubeBlockEntity(LogisticsPipes.TUBE_BLOCK_ENTITY.get(), pos, state),
                            TUBES.values().stream().map(Supplier::get).toArray(Block[]::new))
                    .build(null));
    public static final Supplier<BlockEntityType<TubePartBlockEntity>> TUBE_PART_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("tube_part", () -> BlockEntityType.Builder
                    .of((pos, state) -> new TubePartBlockEntity(LogisticsPipes.TUBE_PART_BLOCK_ENTITY.get(), pos, state),
                            TUBE_PART.get())
                    .build(null));

    public static final Supplier<MenuType<SecurityStationMenu>> SECURITY_STATION_MENU = MENUS.register("security_station",
            () -> new MenuType<>((id, inventory) -> new SecurityStationMenu(LogisticsPipes.SECURITY_STATION_MENU.get(), id, inventory),
                    FeatureFlags.DEFAULT_FLAGS));
    public static final DeferredItem<SecurityCardItem> SECURITY_CARD = ITEMS.register("security_card",
            () -> new SecurityCardItem(new Item.Properties()));
    public static final DeferredBlock<SecurityStationBlock> SECURITY_STATION = BLOCKS.register("security_station",
            () -> new SecurityStationBlock(BlockBehaviour.Properties.of().strength(2.5f).sound(SoundType.METAL),
                    new SecurityStationServices(() -> LogisticsPipes.SECURITY_STATION_BLOCK_ENTITY.get(), SECURITY_STATION_MENU,
                            SECURITY_CARD, CHANNEL_MENU, ComputerCraftSupport::installed)));
    public static final Supplier<BlockEntityType<SecurityStationBlockEntity>> SECURITY_STATION_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("security_station", () -> BlockEntityType.Builder
                    .of((pos, state) -> new SecurityStationBlockEntity(LogisticsPipes.SECURITY_STATION_BLOCK_ENTITY.get(), pos, state),
                            SECURITY_STATION.get())
                    .build(null));

    public static final Supplier<MenuType<StatisticsTableMenu>> STATISTICS_TABLE_MENU = MENUS.register("statistics_table",
            () -> new MenuType<>((id, inventory) -> new StatisticsTableMenu(LogisticsPipes.STATISTICS_TABLE_MENU.get(), id),
                    FeatureFlags.DEFAULT_FLAGS));
    public static final DeferredBlock<StatisticsTableBlock> STATISTICS_TABLE = BLOCKS.register("statistics_table",
            () -> new StatisticsTableBlock(BlockBehaviour.Properties.of().strength(2.5f).sound(SoundType.METAL),
                    new StatisticsTableServices(() -> LogisticsPipes.STATISTICS_TABLE_BLOCK_ENTITY.get(), STATISTICS_TABLE_MENU)));
    public static final Supplier<BlockEntityType<StatisticsTableBlockEntity>> STATISTICS_TABLE_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("statistics_table", () -> BlockEntityType.Builder
                    .of((pos, state) -> new StatisticsTableBlockEntity(LogisticsPipes.STATISTICS_TABLE_BLOCK_ENTITY.get(), pos, state),
                            STATISTICS_TABLE.get())
                    .build(null));

    public static final Supplier<MenuType<ProgramCompilerMenu>> PROGRAM_COMPILER_MENU = MENUS.register("program_compiler",
            () -> new MenuType<>((id, inventory) -> new ProgramCompilerMenu(LogisticsPipes.PROGRAM_COMPILER_MENU.get(), id, inventory),
                    FeatureFlags.DEFAULT_FLAGS));
    public static final DeferredItem<ProgrammerItem> LOGISTICS_PROGRAMMER = ITEMS.register("logistics_programmer",
            () -> new ProgrammerItem(new Item.Properties().stacksTo(1)));
    public static final DeferredBlock<ProgramCompilerBlock> PROGRAM_COMPILER = BLOCKS.register("program_compiler",
            () -> new ProgramCompilerBlock(BlockBehaviour.Properties.of().strength(2.5f).sound(SoundType.METAL),
                    new ProgramCompilerServices(() -> LogisticsPipes.PROGRAM_COMPILER_BLOCK_ENTITY.get(), PROGRAM_COMPILER_MENU)));
    public static final Supplier<BlockEntityType<ProgramCompilerBlockEntity>> PROGRAM_COMPILER_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("program_compiler", () -> BlockEntityType.Builder
                    .of((pos, state) -> new ProgramCompilerBlockEntity(LogisticsPipes.PROGRAM_COMPILER_BLOCK_ENTITY.get(), pos, state),
                            PROGRAM_COMPILER.get())
                    .build(null));

    static {
        ITEMS.registerSimpleBlockItem(PROGRAM_COMPILER);
        DATA_COMPONENTS.register("program", () -> ProgrammerItem.PROGRAM);
        DATA_COMPONENTS.register("compiler_disk", () -> CompilerDiskContents.TYPE);
        RECIPE_SERIALIZERS.register("programmed", () -> ProgrammedRecipe.SERIALIZER);
    }

    // what the rest is crafted from, raw chips are smelted into chips
    static {
        for (String part : new String[] {"chip_basic_raw", "chip_basic", "chip_advanced_raw", "chip_advanced",
                "chip_fpga_raw", "chip_fpga", "module_blank"}) {
            ITEMS.registerSimpleItem(part);
        }
        ITEMS.registerSimpleBlockItem(BLOCKS.registerSimpleBlock("frame",
                BlockBehaviour.Properties.of().strength(2.5f).sound(SoundType.METAL)));
    }

    static {
        ITEMS.registerSimpleBlockItem(CRAFTING_TABLE);
        ITEMS.registerSimpleBlockItem(POWER_JUNCTION);
        ITEMS.registerSimpleBlockItem(POWER_PROVIDER_RF);
        ITEMS.registerSimpleBlockItem(SECURITY_STATION);
        ITEMS.registerSimpleBlockItem(STATISTICS_TABLE);
    }

    static {
        TABS.register("main", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.logisticspipes"))
                .icon(() -> new ItemStack(pipe(PipeKind.BASIC)))
                .displayItems((parameters, output) -> ITEMS.getEntries().stream()
                        .filter(item -> item != FLUID_CONTAINER).forEach(item -> output.accept(item.get())))
                .build());
    }

    public LogisticsPipes(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        MENUS.register(modEventBus);
        TABS.register(modEventBus);
        DATA_COMPONENTS.register(modEventBus);
        RECIPE_SERIALIZERS.register(modEventBus);
        modEventBus.addListener(LogisticsPipes::registerCapabilities);
        modEventBus.addListener(LogisticsPipes::registerPayloads);
        NeoForge.EVENT_BUS.addListener(LogisticsPipes::buildCreativeTabs);
        SecurityEvents.register();
        GuideBookGift.register(modEventBus);
        ComputerCraftSupport.registerIfInstalled(modEventBus);
        Benchmark.registerIfRequested();
    }

    public static LogisticsPipeBlock pipe(PipeKind kind) {
        return LOGISTICS_PIPES.get(kind).get();
    }

    private static DeferredBlock<LogisticsPipeBlock> pipe(String name, PipeKind kind, int moduleSlots) {
        DeferredBlock<LogisticsPipeBlock> block = BLOCKS.register(name, () -> new LogisticsPipeBlock(
                BlockBehaviour.Properties.of().strength(0.5f).sound(SoundType.GLASS).noOcclusion(), kind, moduleSlots, SERVICES));
        ITEMS.registerSimpleBlockItem(block);
        return block;
    }

    private static BlockBehaviour.Properties pipeProperties() {
        return BlockBehaviour.Properties.of().strength(0.5f).sound(SoundType.GLASS).noOcclusion();
    }

    private static BlockBehaviour.Properties tubeProperties() {
        return BlockBehaviour.Properties.of().strength(0.5f).sound(SoundType.GLASS).noOcclusion().pushReaction(PushReaction.BLOCK);
    }

    private static Supplier<MenuType<PipeConfigMenu>> configMenu(String name, PipeKind kind) {
        return MENUS.register(name, () -> new MenuType<>(
                (id, inventory) -> new PipeConfigMenu(SERVICES.configMenu(kind), id, inventory, kind),
                FeatureFlags.DEFAULT_FLAGS));
    }

    private static Supplier<MenuType<FluidConfigMenu>> fluidConfigMenu(String name, PipeKind kind) {
        return MENUS.register(name, () -> new MenuType<>(
                (id, inventory) -> new FluidConfigMenu(FLUIDS.configMenu(kind), id, inventory, kind),
                FeatureFlags.DEFAULT_FLAGS));
    }

    private static PipeBlockEntity newPipe(BlockPos pos, BlockState state) {
        return new PipeBlockEntity(PIPE_BLOCK_ENTITY.get(), pos, state);
    }

    // the sink for creative tabs asks the tabs what they hold, and on a server nobody else fills them
    private static void buildCreativeTabs(ServerStartedEvent event) {
        try {
            CreativeModeTabs.tryRebuildTabContents(event.getServer().getWorldData().enabledFeatures(), false,
                    event.getServer().registryAccess());
        } catch (RuntimeException e) {
            LOGGER.warn("Could not fill the creative tabs, the creative tab sink will take nothing", e);
        }
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(SetMarkerPayload.TYPE, SetMarkerPayload.CODEC, (payload, context) ->
                MenuRequests.apply(context.player(), payload.containerId(), payload.slot(), payload.stack()));
        registrar.playToServer(SetMenuTextPayload.TYPE, SetMenuTextPayload.CODEC, (payload, context) ->
                MenuRequests.applyText(context.player(), payload.containerId(), payload.field(), payload.text()));
        registrar.playToServer(AskIngredientsPayload.TYPE, AskIngredientsPayload.CODEC, (payload, context) ->
                MenuRequests.askIngredients(context.player(), payload.containerId(), payload.question(), payload.ingredients(),
                        payload.order()));
        registrar.playToServer(OpenRemoteOrdererPayload.TYPE, OpenRemoteOrdererPayload.CODEC, (payload, context) ->
                RemoteOrdererItem.openFromInventory(context.player()));
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, PIPE_BLOCK_ENTITY.get(),
                (pipe, side) -> side == null ? null : new PipeItemHandler(pipe, side));
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK, PIPE_BLOCK_ENTITY.get(),
                (pipe, side) -> side == null || pipe.fluidPipe() == null || !pipe.fluidPipe().receives()
                        ? null : new PipeFluidHandler(pipe.fluidPipe(), side));
        TestTank.registerIfTesting(event);
        TestBattery.registerIfTesting(event);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, CRAFTING_TABLE_BLOCK_ENTITY.get(),
                (table, side) -> new CraftingTableItemHandler(table));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, POWER_JUNCTION_BLOCK_ENTITY.get(),
                (junction, side) -> new PowerJunctionEnergy(junction));
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK, POWER_PROVIDER_BLOCK_ENTITY.get(),
                (provider, side) -> new PowerProviderEnergy(provider));
    }
}
