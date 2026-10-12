package logisticspipes.adapter.neoforge;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Stream;

import logisticspipes.adapter.minecraft.CraftingTableBlock;
import logisticspipes.adapter.minecraft.CraftingTableBlockEntity;
import logisticspipes.adapter.minecraft.CraftingTableServices;
import logisticspipes.adapter.minecraft.LevelNetworks;
import logisticspipes.adapter.minecraft.LogisticsPipeBlock;
import logisticspipes.adapter.minecraft.PipeBlockEntity;
import logisticspipes.adapter.minecraft.PipeServices;
import logisticspipes.adapter.minecraft.menu.CraftingTableMenu;
import logisticspipes.adapter.minecraft.menu.PipeConfigMenu;
import logisticspipes.adapter.minecraft.menu.RequestMenu;
import logisticspipes.domain.logistics.PipeKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

// composition root, the only place that knows every layer
@Mod(LogisticsPipes.MODID)
public class LogisticsPipes {

    public static final String MODID = "logisticspipes";

    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(MODID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(MODID);
    private static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, MODID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, MODID);
    private static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final Supplier<MenuType<RequestMenu>> REQUEST_MENU = MENUS.register("request",
            () -> new MenuType<>((id, inventory) -> new RequestMenu(LogisticsPipes.REQUEST_MENU.get(), id),
                    FeatureFlags.DEFAULT_FLAGS));
    public static final Map<PipeKind, Supplier<MenuType<PipeConfigMenu>>> CONFIG_MENUS = new EnumMap<>(PipeKind.class);

    static {
        CONFIG_MENUS.put(PipeKind.BASIC, configMenu("basic", PipeKind.BASIC));
        CONFIG_MENUS.put(PipeKind.SUPPLIER, configMenu("supplier", PipeKind.SUPPLIER));
        CONFIG_MENUS.put(PipeKind.CRAFTING, configMenu("crafting", PipeKind.CRAFTING));
        CONFIG_MENUS.put(PipeKind.SATELLITE, configMenu("satellite", PipeKind.SATELLITE));
    }

    public static final Supplier<MenuType<CraftingTableMenu>> CRAFTING_TABLE_MENU = MENUS.register("crafting_table",
            () -> new MenuType<>((id, inventory) -> new CraftingTableMenu(LogisticsPipes.CRAFTING_TABLE_MENU.get(), id, inventory),
                    FeatureFlags.DEFAULT_FLAGS));

    private static final PipeServices SERVICES = new PipeServices(() -> LogisticsPipes.PIPE_BLOCK_ENTITY.get(),
            new CapabilityItemStorage(), new LevelNetworks(), REQUEST_MENU, CONFIG_MENUS);

    public static final DeferredBlock<LogisticsPipeBlock> TRANSPORT_PIPE = pipe("transport_pipe", null);
    public static final Map<PipeKind, DeferredBlock<LogisticsPipeBlock>> LOGISTICS_PIPES = new EnumMap<>(PipeKind.class);

    static {
        LOGISTICS_PIPES.put(PipeKind.BASIC, pipe("basic_logistics_pipe", PipeKind.BASIC));
        LOGISTICS_PIPES.put(PipeKind.PROVIDER, pipe("provider_pipe", PipeKind.PROVIDER));
        LOGISTICS_PIPES.put(PipeKind.REQUEST, pipe("request_pipe", PipeKind.REQUEST));
        LOGISTICS_PIPES.put(PipeKind.SUPPLIER, pipe("supplier_pipe", PipeKind.SUPPLIER));
        LOGISTICS_PIPES.put(PipeKind.CRAFTING, pipe("crafting_pipe", PipeKind.CRAFTING));
        LOGISTICS_PIPES.put(PipeKind.SATELLITE, pipe("satellite_pipe", PipeKind.SATELLITE));
    }

    public static final Supplier<BlockEntityType<PipeBlockEntity>> PIPE_BLOCK_ENTITY = BLOCK_ENTITIES.register("pipe",
            () -> BlockEntityType.Builder
                    .of(LogisticsPipes::newPipe, Stream.concat(Stream.of(TRANSPORT_PIPE), LOGISTICS_PIPES.values().stream())
                            .map(Supplier::get).toArray(Block[]::new))
                    .build(null));

    public static final DeferredBlock<CraftingTableBlock> CRAFTING_TABLE = BLOCKS.register("logistics_crafting_table",
            () -> new CraftingTableBlock(BlockBehaviour.Properties.of().strength(2.5f).sound(SoundType.METAL),
                    new CraftingTableServices(() -> LogisticsPipes.CRAFTING_TABLE_BLOCK_ENTITY.get(), CRAFTING_TABLE_MENU)));
    public static final Supplier<BlockEntityType<CraftingTableBlockEntity>> CRAFTING_TABLE_BLOCK_ENTITY =
            BLOCK_ENTITIES.register("crafting_table", () -> BlockEntityType.Builder
                    .of((pos, state) -> new CraftingTableBlockEntity(LogisticsPipes.CRAFTING_TABLE_BLOCK_ENTITY.get(), pos, state),
                            CRAFTING_TABLE.get())
                    .build(null));

    static {
        ITEMS.registerSimpleBlockItem(CRAFTING_TABLE);
    }

    static {
        TABS.register("main", () -> CreativeModeTab.builder()
                .title(Component.translatable("itemGroup.logisticspipes"))
                .icon(() -> new ItemStack(pipe(PipeKind.BASIC)))
                .displayItems((parameters, output) -> ITEMS.getEntries().forEach(item -> output.accept(item.get())))
                .build());
    }

    public LogisticsPipes(IEventBus modEventBus) {
        BLOCKS.register(modEventBus);
        ITEMS.register(modEventBus);
        BLOCK_ENTITIES.register(modEventBus);
        MENUS.register(modEventBus);
        TABS.register(modEventBus);
        modEventBus.addListener(LogisticsPipes::registerCapabilities);
    }

    public static LogisticsPipeBlock pipe(PipeKind kind) {
        return LOGISTICS_PIPES.get(kind).get();
    }

    private static DeferredBlock<LogisticsPipeBlock> pipe(String name, PipeKind kind) {
        DeferredBlock<LogisticsPipeBlock> block = BLOCKS.register(name, () -> new LogisticsPipeBlock(
                BlockBehaviour.Properties.of().strength(0.5f).sound(SoundType.GLASS).noOcclusion(), kind, SERVICES));
        ITEMS.registerSimpleBlockItem(block);
        return block;
    }

    private static Supplier<MenuType<PipeConfigMenu>> configMenu(String name, PipeKind kind) {
        return MENUS.register(name, () -> new MenuType<>(
                (id, inventory) -> new PipeConfigMenu(SERVICES.configMenu(kind), id, inventory, kind),
                FeatureFlags.DEFAULT_FLAGS));
    }

    private static PipeBlockEntity newPipe(BlockPos pos, BlockState state) {
        return new PipeBlockEntity(PIPE_BLOCK_ENTITY.get(), pos, state);
    }

    private static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, PIPE_BLOCK_ENTITY.get(),
                (pipe, side) -> side == null ? null : new PipeItemHandler(pipe, side));
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK, CRAFTING_TABLE_BLOCK_ENTITY.get(),
                (table, side) -> new CraftingTableItemHandler(table));
    }
}
