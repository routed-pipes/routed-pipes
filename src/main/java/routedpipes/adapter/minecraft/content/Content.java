package routedpipes.adapter.minecraft.content;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

import routedpipes.adapter.minecraft.CompilerDiskContents;
import routedpipes.adapter.minecraft.ConnectorPipeBlock;
import routedpipes.adapter.minecraft.CraftingTableBlock;
import routedpipes.adapter.minecraft.CraftingTableBlockEntity;
import routedpipes.adapter.minecraft.CraftingTableServices;
import routedpipes.adapter.minecraft.DiskContents;
import routedpipes.adapter.minecraft.DiskItem;
import routedpipes.adapter.minecraft.DiskMenus;
import routedpipes.adapter.minecraft.FluidContainerItem;
import routedpipes.adapter.minecraft.FluidContents;
import routedpipes.adapter.minecraft.FluidServices;
import routedpipes.adapter.minecraft.FrequencyCardItem;
import routedpipes.adapter.minecraft.GuideBookItem;
import routedpipes.adapter.minecraft.HudGlassesItem;
import routedpipes.adapter.minecraft.HudWatch;
import routedpipes.adapter.minecraft.LevelNetworks;
import routedpipes.adapter.minecraft.ModuleItem;
import routedpipes.adapter.minecraft.PipeBlock;
import routedpipes.adapter.minecraft.PipeBlockEntity;
import routedpipes.adapter.minecraft.PipeControllerItem;
import routedpipes.adapter.minecraft.PipeServices;
import routedpipes.adapter.minecraft.Platform;
import routedpipes.adapter.minecraft.PowerJunctionBlock;
import routedpipes.adapter.minecraft.PowerJunctionBlockEntity;
import routedpipes.adapter.minecraft.PowerProviderBlock;
import routedpipes.adapter.minecraft.PowerProviderBlockEntity;
import routedpipes.adapter.minecraft.ProgramCompilerBlock;
import routedpipes.adapter.minecraft.ProgramCompilerBlockEntity;
import routedpipes.adapter.minecraft.ProgramCompilerServices;
import routedpipes.adapter.minecraft.ProgrammedRecipe;
import routedpipes.adapter.minecraft.ProgrammerItem;
import routedpipes.adapter.minecraft.RemoteOrdererItem;
import routedpipes.adapter.minecraft.RequestTableBlock;
import routedpipes.adapter.minecraft.SecurityCardItem;
import routedpipes.adapter.minecraft.SecurityStationBlock;
import routedpipes.adapter.minecraft.SecurityStationBlockEntity;
import routedpipes.adapter.minecraft.SecurityStationServices;
import routedpipes.adapter.minecraft.SettingsCopierItem;
import routedpipes.adapter.minecraft.SignCreatorItem;
import routedpipes.adapter.minecraft.StatisticsTableBlock;
import routedpipes.adapter.minecraft.StatisticsTableBlockEntity;
import routedpipes.adapter.minecraft.StatisticsTableServices;
import routedpipes.adapter.minecraft.TubeBlock;
import routedpipes.adapter.minecraft.TubeBlockEntity;
import routedpipes.adapter.minecraft.TubeBlockItem;
import routedpipes.adapter.minecraft.TubePartBlock;
import routedpipes.adapter.minecraft.TubePartBlockEntity;
import routedpipes.adapter.minecraft.TubeServices;
import routedpipes.adapter.minecraft.UpgradeItem;
import routedpipes.adapter.minecraft.menu.ChannelMenu;
import routedpipes.adapter.minecraft.menu.ChassisMenu;
import routedpipes.adapter.minecraft.menu.CraftingTableMenu;
import routedpipes.adapter.minecraft.menu.DiskMenu;
import routedpipes.adapter.minecraft.menu.FirewallMenu;
import routedpipes.adapter.minecraft.menu.FluidConfigMenu;
import routedpipes.adapter.minecraft.menu.FluidRequestMenu;
import routedpipes.adapter.minecraft.menu.FrequencyCardMenu;
import routedpipes.adapter.minecraft.menu.HudSettingsMenu;
import routedpipes.adapter.minecraft.menu.MacroEditMenu;
import routedpipes.adapter.minecraft.menu.NamesMenu;
import routedpipes.adapter.minecraft.menu.PipeConfigMenu;
import routedpipes.adapter.minecraft.menu.ProgramCompilerMenu;
import routedpipes.adapter.minecraft.menu.RequestMenu;
import routedpipes.adapter.minecraft.menu.RequestTableMenu;
import routedpipes.adapter.minecraft.menu.SecurityStationMenu;
import routedpipes.adapter.minecraft.menu.SignItemMenu;
import routedpipes.adapter.minecraft.menu.StatisticsTableMenu;
import routedpipes.adapter.minecraft.menu.UpgradeMenu;
import routedpipes.adapter.minecraft.network.HudPanelsPayload;
import routedpipes.domain.logistics.ModuleKind;
import routedpipes.domain.logistics.PipeKind;
import routedpipes.domain.logistics.UpgradeKind;
import routedpipes.domain.tube.TubeKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;

// every block, item, menu and the rest of the mod, the loader puts them into the game registries
public final class Content {

    public static final String MODID = "routedpipes";

    public static final Registrations REGISTRATIONS = new Registrations(MODID);

    private static final String COMPUTERCRAFT = "computercraft";

    public static final Registered<MenuType<RequestMenu>> REQUEST_MENU = menu("request",
            id -> new RequestMenu(Content.REQUEST_MENU.get(), id));
    public static final Map<PipeKind, Supplier<MenuType<PipeConfigMenu>>> CONFIG_MENUS = new EnumMap<>(PipeKind.class);

    static {
        CONFIG_MENUS.put(PipeKind.BASIC, configMenu("basic", PipeKind.BASIC));
        CONFIG_MENUS.put(PipeKind.PROVIDER, configMenu("provider", PipeKind.PROVIDER));
        CONFIG_MENUS.put(PipeKind.SUPPLIER, configMenu("supplier", PipeKind.SUPPLIER));
        CONFIG_MENUS.put(PipeKind.CRAFTING, configMenu("crafting", PipeKind.CRAFTING));
        CONFIG_MENUS.put(PipeKind.SATELLITE, configMenu("satellite", PipeKind.SATELLITE));
        CONFIG_MENUS.put(PipeKind.VOID, configMenu("void", PipeKind.VOID));
    }

    public static final Registered<MenuType<CraftingTableMenu>> CRAFTING_TABLE_MENU = playerMenu("crafting_table",
            (id, inventory) -> new CraftingTableMenu(Content.CRAFTING_TABLE_MENU.get(), id, inventory));

    public static final Registered<MenuType<NamesMenu>> NAMES_MENU = playerMenu("names",
            (id, inventory) -> new NamesMenu(Content.NAMES_MENU.get(), id, inventory));

    public static final Registered<MenuType<UpgradeMenu>> UPGRADE_MENU = playerMenu("upgrades",
            (id, inventory) -> new UpgradeMenu(Content.UPGRADE_MENU.get(), id, inventory));

    public static final Registered<MenuType<FirewallMenu>> FIREWALL_MENU = playerMenu("firewall",
            (id, inventory) -> new FirewallMenu(Content.FIREWALL_MENU.get(), id, inventory));

    public static final Registered<MenuType<RequestTableMenu>> REQUEST_TABLE_MENU = playerMenu("request_table",
            (id, inventory) -> new RequestTableMenu(Content.REQUEST_TABLE_MENU.get(), id, inventory));

    public static final Registered<MenuType<ChassisMenu>> CHASSIS_MENU = playerMenu("chassis",
            (id, inventory) -> new ChassisMenu(Content.CHASSIS_MENU.get(), id, inventory));

    public static final Map<PipeKind, Supplier<MenuType<FluidConfigMenu>>> FLUID_CONFIG_MENUS = new EnumMap<>(PipeKind.class);

    static {
        FLUID_CONFIG_MENUS.put(PipeKind.FLUID_BASIC, fluidConfigMenu("fluid_basic", PipeKind.FLUID_BASIC));
        FLUID_CONFIG_MENUS.put(PipeKind.FLUID_TERMINUS, fluidConfigMenu("fluid_terminus", PipeKind.FLUID_TERMINUS));
        FLUID_CONFIG_MENUS.put(PipeKind.FLUID_SATELLITE, fluidConfigMenu("fluid_satellite", PipeKind.FLUID_SATELLITE));
        FLUID_CONFIG_MENUS.put(PipeKind.FLUID_SUPPLIER, fluidConfigMenu("fluid_supplier", PipeKind.FLUID_SUPPLIER));
        FLUID_CONFIG_MENUS.put(PipeKind.FLUID_CONTAINER_SUPPLIER,
                fluidConfigMenu("fluid_container_supplier", PipeKind.FLUID_CONTAINER_SUPPLIER));
    }

    public static final Registered<MenuType<FluidRequestMenu>> FLUID_REQUEST_MENU = menu("fluid_request",
            id -> new FluidRequestMenu(Content.FLUID_REQUEST_MENU.get(), id));

    public static final FluidServices FLUIDS = new FluidServices(Platform.get().fluids(), FLUID_CONFIG_MENUS,
            FLUID_REQUEST_MENU);

    public static final Registered<MenuType<DiskMenu>> DISK_MENU = menu("disk",
            id -> new DiskMenu(Content.DISK_MENU.get(), id));
    public static final Registered<MenuType<MacroEditMenu>> MACRO_EDIT_MENU = menu("macro_edit",
            id -> new MacroEditMenu(Content.MACRO_EDIT_MENU.get(), id));
    private static final DiskMenus DISK_MENUS = new DiskMenus(DISK_MENU, MACRO_EDIT_MENU);

    // the channels of inventory system connectors, as a connector shows them and as a security station does
    public static final Registered<MenuType<ChannelMenu>> CONNECTOR_MENU = playerMenu("connector",
            (id, inventory) -> new ChannelMenu(Content.CONNECTOR_MENU.get(), id, inventory, true));
    public static final Registered<MenuType<ChannelMenu>> CHANNEL_MENU = playerMenu("channels",
            (id, inventory) -> new ChannelMenu(Content.CHANNEL_MENU.get(), id, inventory, false));

    public static final Registered<MenuType<FrequencyCardMenu>> FREQUENCY_CARD_MENU = playerMenu("frequency_card",
            (id, inventory) -> new FrequencyCardMenu(Content.FREQUENCY_CARD_MENU.get(), id, inventory));

    public static final PipeServices SERVICES = new PipeServices(() -> Content.PIPE_BLOCK_ENTITY.get(),
            Platform.get().items(), new LevelNetworks(), REQUEST_MENU, CHASSIS_MENU, UPGRADE_MENU, FIREWALL_MENU,
            REQUEST_TABLE_MENU, CONFIG_MENUS, FLUIDS, DISK_MENUS,
            Platform.get().energy(), CONNECTOR_MENU, FREQUENCY_CARD_MENU);

    public static final Registered<PipeBlock> TRANSPORT_PIPE = pipe("transport_pipe", null, 0);
    public static final Map<PipeKind, Registered<PipeBlock>> PIPES = new EnumMap<>(PipeKind.class);

    static {
        PIPES.put(PipeKind.BASIC, pipe("basic_logistics_pipe", PipeKind.BASIC, 0));
        PIPES.put(PipeKind.PROVIDER, pipe("provider_pipe", PipeKind.PROVIDER, 0));
        PIPES.put(PipeKind.REQUEST, pipe("request_pipe", PipeKind.REQUEST, 0));
        PIPES.put(PipeKind.REQUEST_MK2, pipe("request_pipe_mk2", PipeKind.REQUEST_MK2, 0));
        PIPES.put(PipeKind.SUPPLIER, pipe("supplier_pipe", PipeKind.SUPPLIER, 0));
        PIPES.put(PipeKind.CRAFTING, pipe("crafting_pipe", PipeKind.CRAFTING, 0));
        PIPES.put(PipeKind.SATELLITE, pipe("satellite_pipe", PipeKind.SATELLITE, 0));
        PIPES.put(PipeKind.FIREWALL, pipe("firewall_pipe", PipeKind.FIREWALL, 0));
        Registered<PipeBlock> table = blockWithItem("request_table", () -> new RequestTableBlock(
                BlockBehaviour.Properties.of().strength(2.5f).sound(SoundType.METAL), SERVICES));
        PIPES.put(PipeKind.REQUEST_TABLE, table);
        PIPES.put(PipeKind.FLUID_BASIC, pipe("fluid_basic_pipe", PipeKind.FLUID_BASIC, 0));
        PIPES.put(PipeKind.FLUID_INSERTION, pipe("fluid_insertion_pipe", PipeKind.FLUID_INSERTION, 0));
        PIPES.put(PipeKind.FLUID_EXTRACTOR, pipe("fluid_extractor_pipe", PipeKind.FLUID_EXTRACTOR, 0));
        PIPES.put(PipeKind.FLUID_PROVIDER, pipe("fluid_provider_pipe", PipeKind.FLUID_PROVIDER, 0));
        PIPES.put(PipeKind.FLUID_TERMINUS, pipe("fluid_terminus_pipe", PipeKind.FLUID_TERMINUS, 0));
        PIPES.put(PipeKind.FLUID_REQUEST, pipe("fluid_request_pipe", PipeKind.FLUID_REQUEST, 0));
        PIPES.put(PipeKind.FLUID_SATELLITE, pipe("fluid_satellite_pipe", PipeKind.FLUID_SATELLITE, 0));
        PIPES.put(PipeKind.FLUID_SUPPLIER, pipe("fluid_supplier_pipe", PipeKind.FLUID_SUPPLIER, 0));
        PIPES.put(PipeKind.FLUID_CONTAINER_SUPPLIER,
                pipe("fluid_container_supplier_pipe", PipeKind.FLUID_CONTAINER_SUPPLIER, 0));
        PIPES.put(PipeKind.INVENTORY_SYSTEM_CONNECTOR, blockWithItem("inventory_system_connector_pipe",
                () -> new ConnectorPipeBlock(pipeProperties(), PipeKind.INVENTORY_SYSTEM_CONNECTOR, SERVICES)));
        PIPES.put(PipeKind.SYSTEM_ENTRANCE, pipe("system_entrance_pipe", PipeKind.SYSTEM_ENTRANCE, 0));
        PIPES.put(PipeKind.SYSTEM_DESTINATION, pipe("system_destination_pipe", PipeKind.SYSTEM_DESTINATION, 0));
        PIPES.put(PipeKind.VOID, pipe("void_pipe", PipeKind.VOID, 0));
        PIPES.put(PipeKind.TELEPORT, blockWithItem("teleport_pipe",
                () -> new ConnectorPipeBlock(pipeProperties(), PipeKind.TELEPORT, SERVICES)));
    }

    // chassis mk1 to mk5 by number of module slots
    public static final Map<Integer, Registered<PipeBlock>> CHASSIS_PIPES = new LinkedHashMap<>();
    private static final int[] CHASSIS_SLOTS = {1, 2, 3, 4, 8};

    static {
        for (int tier = 0; tier < CHASSIS_SLOTS.length; tier++) {
            CHASSIS_PIPES.put(CHASSIS_SLOTS[tier], pipe("chassis_pipe_mk" + (tier + 1), PipeKind.CHASSIS, CHASSIS_SLOTS[tier]));
        }
    }

    public static final Map<ModuleKind, Registered<ModuleItem>> MODULES = new EnumMap<>(ModuleKind.class);

    static {
        for (ModuleKind kind : ModuleKind.values()) {
            MODULES.put(kind, item("module_" + kind.name().toLowerCase(Locale.ROOT),
                    () -> new ModuleItem(new Item.Properties(), kind, ModuleItem.settingsLayoutFor(kind).orElse(null),
                            ModuleItem.settingsLayoutFor(kind).map(CONFIG_MENUS::get).orElse(null),
                            ModuleItem.listsNames(kind) ? NAMES_MENU : null)));
        }
    }

    public static final Map<UpgradeKind, Registered<UpgradeItem>> UPGRADES = new EnumMap<>(UpgradeKind.class);

    static {
        for (UpgradeKind kind : UpgradeKind.values()) {
            UPGRADES.put(kind, item("upgrade_" + kind.name().toLowerCase(Locale.ROOT),
                    () -> new UpgradeItem(new Item.Properties(), kind)));
        }
    }

    public static final Registered<PipeControllerItem> PIPE_CONTROLLER = item("pipe_controller",
            () -> new PipeControllerItem(new Item.Properties()));

    public static final Registered<FrequencyCardItem> FREQUENCY_CARD = item("frequency_card",
            () -> new FrequencyCardItem(new Item.Properties()));

    public static final Registered<RemoteOrdererItem> REMOTE_ORDERER = item("remote_orderer",
            () -> new RemoteOrdererItem(new Item.Properties()));

    public static final Registered<SettingsCopierItem> SETTINGS_COPIER = item("settings_copier",
            () -> new SettingsCopierItem(new Item.Properties()));

    public static final Registered<MenuType<SignItemMenu>> SIGN_ITEM_MENU = playerMenu("sign_item",
            (id, inventory) -> new SignItemMenu(Content.SIGN_ITEM_MENU.get(), id, inventory));
    public static final Registered<SignCreatorItem> SIGN_CREATOR = item("sign_creator",
            () -> new SignCreatorItem(new Item.Properties(), SIGN_ITEM_MENU));

    static {
        REGISTRATIONS.register(Registries.DATA_COMPONENT_TYPE, "sign_mode", () -> SignCreatorItem.MODE);
    }

    public static final Registered<GuideBookItem> GUIDE_BOOK = item("guide_book",
            () -> new GuideBookItem(new Item.Properties()));
    public static final Registered<MenuType<HudSettingsMenu>> HUD_SETTINGS_MENU = playerMenu("hud_settings",
            (id, inventory) -> new HudSettingsMenu(Content.HUD_SETTINGS_MENU.get(), id, inventory));
    private static final Registered<ArmorMaterial> HUD_GLASSES_MATERIAL = REGISTRATIONS.register(Registries.ARMOR_MATERIAL,
            "hud_glasses", () -> HudGlassesItem.material(ResourceLocation.fromNamespaceAndPath(MODID, "hud_glasses")));
    public static final Registered<HudGlassesItem> HUD_GLASSES = item("hud_glasses",
            () -> new HudGlassesItem(BuiltInRegistries.ARMOR_MATERIAL.wrapAsHolder(HUD_GLASSES_MATERIAL.get()),
                    new Item.Properties(), Content.HUD_SETTINGS_MENU));

    // the loader ticks it for every player on the server
    public static final HudWatch HUD_WATCH = new HudWatch((player, reset, changed, gone) -> {
        if (player instanceof ServerPlayer receiver) {
            Platform.get().sendToPlayer(receiver, new HudPanelsPayload(reset, changed, gone));
        }
    });

    // never in a player's hands, so it is in no creative tab and has no recipe
    public static final Registered<FluidContainerItem> FLUID_CONTAINER = item("fluid_container",
            () -> new FluidContainerItem(new Item.Properties(), FLUIDS.storage()));

    public static final Registered<DiskItem> DISK = item("disk",
            () -> new DiskItem(new Item.Properties().stacksTo(1)));

    static {
        REGISTRATIONS.register(Registries.DATA_COMPONENT_TYPE, "disk", () -> DiskContents.TYPE);
        REGISTRATIONS.register(Registries.DATA_COMPONENT_TYPE, "fluid", () -> FluidContents.TYPE);
    }

    public static final Registered<BlockEntityType<PipeBlockEntity>> PIPE_BLOCK_ENTITY = blockEntity("pipe",
            Content::newPipe, () -> Stream.of(Stream.of(TRANSPORT_PIPE), PIPES.values().stream(), CHASSIS_PIPES.values().stream())
                    .flatMap(pipes -> pipes));

    public static final Registered<CraftingTableBlock> CRAFTING_TABLE = block("logistics_crafting_table",
            () -> new CraftingTableBlock(BlockBehaviour.Properties.of().strength(2.5f).sound(SoundType.METAL),
                    new CraftingTableServices(() -> Content.CRAFTING_TABLE_BLOCK_ENTITY.get(), CRAFTING_TABLE_MENU)));
    public static final Registered<BlockEntityType<CraftingTableBlockEntity>> CRAFTING_TABLE_BLOCK_ENTITY =
            blockEntity("crafting_table", (pos, state) -> new CraftingTableBlockEntity(Content.CRAFTING_TABLE_BLOCK_ENTITY.get(), pos, state),
                    () -> Stream.of(CRAFTING_TABLE));

    public static final Registered<PowerJunctionBlock> POWER_JUNCTION = block("power_junction",
            () -> new PowerJunctionBlock(BlockBehaviour.Properties.of().strength(2.5f).sound(SoundType.METAL),
                    () -> Content.POWER_JUNCTION_BLOCK_ENTITY.get()));
    public static final Registered<BlockEntityType<PowerJunctionBlockEntity>> POWER_JUNCTION_BLOCK_ENTITY =
            blockEntity("power_junction", (pos, state) -> new PowerJunctionBlockEntity(Content.POWER_JUNCTION_BLOCK_ENTITY.get(), pos, state),
                    () -> Stream.of(POWER_JUNCTION));

    public static final Registered<PowerProviderBlock> POWER_PROVIDER_RF = block("power_provider_rf",
            () -> new PowerProviderBlock(BlockBehaviour.Properties.of().strength(2.5f).sound(SoundType.METAL),
                    () -> Content.POWER_PROVIDER_BLOCK_ENTITY.get(), SERVICES.networks()));
    public static final Registered<BlockEntityType<PowerProviderBlockEntity>> POWER_PROVIDER_BLOCK_ENTITY =
            blockEntity("power_provider", (pos, state) -> new PowerProviderBlockEntity(Content.POWER_PROVIDER_BLOCK_ENTITY.get(), pos, state),
                    () -> Stream.of(POWER_PROVIDER_RF));

    private static final TubeServices TUBE_SERVICES = new TubeServices(() -> Content.TUBE_BLOCK_ENTITY.get(),
            () -> Content.TUBE_PART_BLOCK_ENTITY.get(), () -> Content.TUBE_PART.get(), SERVICES.networks());

    public static final Map<TubeKind, Registered<TubeBlock>> TUBES = new EnumMap<>(TubeKind.class);

    static {
        for (TubeKind kind : TubeKind.values()) {
            String name = kind.name().toLowerCase(Locale.ROOT) + "_tube";
            Registered<TubeBlock> tube = block(name, () -> new TubeBlock(tubeProperties(), kind, TUBE_SERVICES));
            item(name, () -> new TubeBlockItem(tube.get(), new Item.Properties()));
            TUBES.put(kind, tube);
        }
    }

    public static final Registered<TubePartBlock> TUBE_PART = block("tube_part",
            () -> new TubePartBlock(tubeProperties().noLootTable(), TUBE_SERVICES));
    public static final Registered<BlockEntityType<TubeBlockEntity>> TUBE_BLOCK_ENTITY = blockEntity("tube",
            (pos, state) -> new TubeBlockEntity(Content.TUBE_BLOCK_ENTITY.get(), pos, state), () -> TUBES.values().stream());
    public static final Registered<BlockEntityType<TubePartBlockEntity>> TUBE_PART_BLOCK_ENTITY = blockEntity("tube_part",
            (pos, state) -> new TubePartBlockEntity(Content.TUBE_PART_BLOCK_ENTITY.get(), pos, state), () -> Stream.of(TUBE_PART));

    public static final Registered<MenuType<SecurityStationMenu>> SECURITY_STATION_MENU = playerMenu("security_station",
            (id, inventory) -> new SecurityStationMenu(Content.SECURITY_STATION_MENU.get(), id, inventory));
    public static final Registered<SecurityCardItem> SECURITY_CARD = item("security_card",
            () -> new SecurityCardItem(new Item.Properties()));
    public static final Registered<SecurityStationBlock> SECURITY_STATION = block("security_station",
            () -> new SecurityStationBlock(BlockBehaviour.Properties.of().strength(2.5f).sound(SoundType.METAL),
                    new SecurityStationServices(() -> Content.SECURITY_STATION_BLOCK_ENTITY.get(), SECURITY_STATION_MENU,
                            SECURITY_CARD, CHANNEL_MENU, () -> Platform.get().isModLoaded(COMPUTERCRAFT))));
    public static final Registered<BlockEntityType<SecurityStationBlockEntity>> SECURITY_STATION_BLOCK_ENTITY =
            blockEntity("security_station", (pos, state) -> new SecurityStationBlockEntity(Content.SECURITY_STATION_BLOCK_ENTITY.get(), pos, state),
                    () -> Stream.of(SECURITY_STATION));

    public static final Registered<MenuType<StatisticsTableMenu>> STATISTICS_TABLE_MENU = menu("statistics_table",
            id -> new StatisticsTableMenu(Content.STATISTICS_TABLE_MENU.get(), id));
    public static final Registered<StatisticsTableBlock> STATISTICS_TABLE = block("statistics_table",
            () -> new StatisticsTableBlock(BlockBehaviour.Properties.of().strength(2.5f).sound(SoundType.METAL),
                    new StatisticsTableServices(() -> Content.STATISTICS_TABLE_BLOCK_ENTITY.get(), STATISTICS_TABLE_MENU)));
    public static final Registered<BlockEntityType<StatisticsTableBlockEntity>> STATISTICS_TABLE_BLOCK_ENTITY =
            blockEntity("statistics_table", (pos, state) -> new StatisticsTableBlockEntity(Content.STATISTICS_TABLE_BLOCK_ENTITY.get(), pos, state),
                    () -> Stream.of(STATISTICS_TABLE));

    public static final Registered<MenuType<ProgramCompilerMenu>> PROGRAM_COMPILER_MENU = playerMenu("program_compiler",
            (id, inventory) -> new ProgramCompilerMenu(Content.PROGRAM_COMPILER_MENU.get(), id, inventory));
    public static final Registered<ProgrammerItem> LOGISTICS_PROGRAMMER = item("logistics_programmer",
            () -> new ProgrammerItem(new Item.Properties().stacksTo(1)));
    public static final Registered<ProgramCompilerBlock> PROGRAM_COMPILER = block("program_compiler",
            () -> new ProgramCompilerBlock(BlockBehaviour.Properties.of().strength(2.5f).sound(SoundType.METAL),
                    new ProgramCompilerServices(() -> Content.PROGRAM_COMPILER_BLOCK_ENTITY.get(), PROGRAM_COMPILER_MENU)));
    public static final Registered<BlockEntityType<ProgramCompilerBlockEntity>> PROGRAM_COMPILER_BLOCK_ENTITY =
            blockEntity("program_compiler", (pos, state) -> new ProgramCompilerBlockEntity(Content.PROGRAM_COMPILER_BLOCK_ENTITY.get(), pos, state),
                    () -> Stream.of(PROGRAM_COMPILER));

    static {
        blockItem(PROGRAM_COMPILER);
        REGISTRATIONS.register(Registries.DATA_COMPONENT_TYPE, "program", () -> ProgrammerItem.PROGRAM);
        REGISTRATIONS.register(Registries.DATA_COMPONENT_TYPE, "compiler_disk", () -> CompilerDiskContents.TYPE);
        REGISTRATIONS.register(Registries.RECIPE_SERIALIZER, "programmed", () -> ProgrammedRecipe.SERIALIZER);
    }

    // what the rest is crafted from, raw chips are smelted into chips
    static {
        for (String part : new String[] {"chip_basic_raw", "chip_basic", "chip_advanced_raw", "chip_advanced",
                "chip_fpga_raw", "chip_fpga", "module_blank"}) {
            item(part, () -> new Item(new Item.Properties()));
        }
        blockWithItem("frame", () -> new Block(BlockBehaviour.Properties.of().strength(2.5f).sound(SoundType.METAL)));
    }

    static {
        blockItem(CRAFTING_TABLE);
        blockItem(POWER_JUNCTION);
        blockItem(POWER_PROVIDER_RF);
        blockItem(SECURITY_STATION);
        blockItem(STATISTICS_TABLE);
    }

    private Content() {
    }

    public static PipeBlock pipe(PipeKind kind) {
        return PIPES.get(kind).get();
    }

    // what the creative tab of the mod shows
    public static List<Item> creativeTabItems() {
        return REGISTRATIONS.entries(Registries.ITEM).stream()
                .filter(item -> item != FLUID_CONTAINER).map(item -> (Item) item.get()).toList();
    }

    private static <T extends Block> Registered<T> block(String name, Supplier<T> factory) {
        return REGISTRATIONS.register(Registries.BLOCK, name, factory);
    }

    private static <T extends Block> Registered<T> blockWithItem(String name, Supplier<T> factory) {
        Registered<T> block = block(name, factory);
        blockItem(block);
        return block;
    }

    private static void blockItem(Registered<? extends Block> block) {
        item(block.id().getPath(), () -> new BlockItem(block.get(), new Item.Properties()));
    }

    private static <T extends Item> Registered<T> item(String name, Supplier<T> factory) {
        return REGISTRATIONS.register(Registries.ITEM, name, factory);
    }

    private interface BlockEntityFactory<T> {
        T create(BlockPos pos, BlockState state);
    }

    private static <T extends net.minecraft.world.level.block.entity.BlockEntity> Registered<BlockEntityType<T>> blockEntity(
            String name, BlockEntityFactory<T> factory, Supplier<Stream<? extends Supplier<? extends Block>>> blocks) {
        return REGISTRATIONS.register(Registries.BLOCK_ENTITY_TYPE, name, () -> Platform.get().blockEntityType(factory::create,
                blocks.get().map(block -> (Block) block.get()).toArray(Block[]::new)));
    }

    private interface MenuFactory<T> {
        T create(int id);
    }

    private interface PlayerMenuFactory<T> {
        T create(int id, net.minecraft.world.entity.player.Inventory inventory);
    }

    private static <T extends AbstractContainerMenu> Registered<MenuType<T>> menu(String name, MenuFactory<T> factory) {
        return playerMenu(name, (id, inventory) -> factory.create(id));
    }

    private static <T extends AbstractContainerMenu> Registered<MenuType<T>> playerMenu(String name, PlayerMenuFactory<T> factory) {
        return REGISTRATIONS.register(Registries.MENU, name, () -> Platform.get().menuType(factory::create));
    }

    private static Registered<PipeBlock> pipe(String name, PipeKind kind, int moduleSlots) {
        return blockWithItem(name, () -> new PipeBlock(pipeProperties(), kind, moduleSlots, SERVICES));
    }

    private static BlockBehaviour.Properties pipeProperties() {
        return BlockBehaviour.Properties.of().strength(0.5f).sound(SoundType.GLASS).noOcclusion();
    }

    private static BlockBehaviour.Properties tubeProperties() {
        return BlockBehaviour.Properties.of().strength(0.5f).sound(SoundType.GLASS).noOcclusion().pushReaction(PushReaction.BLOCK);
    }

    private static Registered<MenuType<PipeConfigMenu>> configMenu(String name, PipeKind kind) {
        return playerMenu(name, (id, inventory) -> new PipeConfigMenu(SERVICES.configMenu(kind), id, inventory, kind));
    }

    private static Registered<MenuType<FluidConfigMenu>> fluidConfigMenu(String name, PipeKind kind) {
        return playerMenu(name, (id, inventory) -> new FluidConfigMenu(FLUIDS.configMenu(kind), id, inventory, kind));
    }

    private static PipeBlockEntity newPipe(BlockPos pos, BlockState state) {
        return new PipeBlockEntity(PIPE_BLOCK_ENTITY.get(), pos, state);
    }
}
