package routedpipes.adapter.neoforge.gametest;

import java.util.List;
import java.util.Optional;

import routedpipes.adapter.minecraft.PipeBlockEntity;
import routedpipes.adapter.minecraft.SignCreatorItem;
import routedpipes.adapter.minecraft.SignView;
import routedpipes.adapter.minecraft.menu.SignItemMenu;
import routedpipes.adapter.neoforge.LogisticsPipes;
import routedpipes.domain.logistics.PipeKind;
import routedpipes.domain.sign.SignKind;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

// signs on the faces of routed pipes and the tool that puts them there
@GameTestHolder(LogisticsPipes.MODID)
@PrefixGameTestTemplate(false)
public class SignGameTest {

    private static final String EMPTY = "empty";
    private static final BlockPos PIPE = new BlockPos(1, 1, 1);
    // a pipe looks at what its signs show once a second
    static final int A_LOOK = 21;

    static PipeBlockEntity pipe(GameTestHelper helper, PipeKind kind, BlockPos pos) {
        helper.setBlock(pos, LogisticsPipes.pipe(kind));
        return helper.getBlockEntity(pos);
    }

    static TestPlayer playerWithCreator(GameTestHelper helper, SignKind mode) {
        TestPlayer player = TestPlayer.in(helper);
        ItemStack creator = new ItemStack(LogisticsPipes.SIGN_CREATOR.get());
        creator.set(SignCreatorItem.MODE, mode.ordinal());
        player.setItemInHand(InteractionHand.MAIN_HAND, creator);
        return player;
    }

    // a right click with the creator on a face of the pipe, in the order the game does it:
    // sneaking skips the block, otherwise the block is asked first and has to let the item through
    static InteractionResult use(GameTestHelper helper, TestPlayer player, BlockPos pos, Direction face, boolean sneaking) {
        ItemStack creator = player.getMainHandItem();
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(helper.absolutePos(pos)), face, helper.absolutePos(pos), false);
        player.setShiftKeyDown(sneaking);
        if (!sneaking && helper.<PipeBlockEntity>getBlockEntity(pos).kind() != null) {
            ItemInteractionResult result = helper.getBlockState(pos).useItemOn(creator, helper.getLevel(), player, InteractionHand.MAIN_HAND, hit);
            helper.assertTrue(result == ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION, "the pipe must leave the click to the creator");
        }
        return creator.useOn(new UseOnContext(helper.getLevel(), player, InteractionHand.MAIN_HAND, creator, hit));
    }

    static List<String> keys(SignView view) {
        return view.lines().stream().map(line -> line.getContents() instanceof TranslatableContents translated
                ? translated.getKey() : line.getString()).toList();
    }

    private static void setRecipe(PipeBlockEntity crafting) {
        crafting.config().setItem(0, new ItemStack(Items.OAK_PLANKS, 2));
        crafting.config().setItem(PipeBlockEntity.OUTPUT_SLOT, new ItemStack(Items.STICK, 4));
    }

    @GameTest(template = EMPTY)
    public static void aCraftingSignGoesOnACraftingPipeAndUsesUpTheCreator(GameTestHelper helper) {
        PipeBlockEntity crafting = pipe(helper, PipeKind.CRAFTING, PIPE);
        TestPlayer player = playerWithCreator(helper, SignKind.CRAFTING);

        use(helper, player, PIPE, Direction.NORTH, false);

        helper.assertTrue(crafting.signs().at(Direction.NORTH).equals(Optional.of(SignKind.CRAFTING)), "the sign is on the face that was clicked");
        helper.assertTrue(crafting.signs().at(Direction.SOUTH).isEmpty(), "and on no other");
        helper.assertTrue(player.getMainHandItem().getDamageValue() == 1, "a sign costs the creator one use");
        helper.assertTrue(player.getMainHandItem().getMaxDamage() == 250, "of 250");
        helper.assertTrue(player.getMainHandItem().getMaxStackSize() == 1, "creators do not stack");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void aFaceTakesOnlyOneSignAndAPipeOneOnEveryFace(GameTestHelper helper) {
        PipeBlockEntity crafting = pipe(helper, PipeKind.CRAFTING, PIPE);
        TestPlayer player = playerWithCreator(helper, SignKind.CRAFTING);

        use(helper, player, PIPE, Direction.NORTH, false);
        use(helper, player, PIPE, Direction.NORTH, false);
        helper.assertTrue(player.getMainHandItem().getDamageValue() == 1, "a click on a sign that is there costs nothing");

        for (Direction face : Direction.values()) {
            use(helper, player, PIPE, face, false);
        }
        helper.assertTrue(crafting.signs().views().size() == 6, "six signs");
        helper.assertTrue(player.getMainHandItem().getDamageValue() == 6, "for six uses");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void aCraftingSignIsRefusedOnOtherPipes(GameTestHelper helper) {
        PipeBlockEntity provider = pipe(helper, PipeKind.PROVIDER, PIPE);
        BlockPos chassisPos = PIPE.east(2);
        helper.setBlock(chassisPos, LogisticsPipes.CHASSIS_PIPES.get(1).get());
        PipeBlockEntity chassis = helper.getBlockEntity(chassisPos);
        TestPlayer player = playerWithCreator(helper, SignKind.CRAFTING);

        use(helper, player, PIPE, Direction.UP, false);
        use(helper, player, chassisPos, Direction.UP, false);

        helper.assertTrue(provider.signs().at(Direction.UP).isEmpty(), "no crafting sign on a provider pipe");
        helper.assertTrue(chassis.signs().at(Direction.UP).isEmpty(), "and none on a chassis");
        helper.assertTrue(player.getMainHandItem().getDamageValue() == 0, "a sign that was not put up costs nothing");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void plainPipesTakeNoSigns(GameTestHelper helper) {
        helper.setBlock(PIPE, LogisticsPipes.TRANSPORT_PIPE.get());
        TestPlayer player = playerWithCreator(helper, SignKind.ITEM_AMOUNT);

        InteractionResult result = use(helper, player, PIPE, Direction.UP, false);
        InteractionResult sneaking = use(helper, player, PIPE, Direction.UP, true);

        helper.assertTrue(helper.<PipeBlockEntity>getBlockEntity(PIPE).signs() == null, "a plain pipe has no signs");
        // a pass would go on to the use in the air, which picks another kind when sneaking
        helper.assertTrue(result == InteractionResult.FAIL && sneaking == InteractionResult.FAIL, "the click ends at the pipe");
        helper.assertTrue(player.getMainHandItem().getDamageValue() == 0, "and costs nothing");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void sneakingTakesTheSignOffAndPutsItBackIntoTheCreator(GameTestHelper helper) {
        PipeBlockEntity crafting = pipe(helper, PipeKind.CRAFTING, PIPE);
        TestPlayer player = playerWithCreator(helper, SignKind.CRAFTING);
        use(helper, player, PIPE, Direction.WEST, false);
        use(helper, player, PIPE, Direction.EAST, false);

        InteractionResult result = use(helper, player, PIPE, Direction.WEST, true);

        helper.assertTrue(result.consumesAction(), "the click is used up, so the creator keeps its kind of sign");
        helper.assertTrue(crafting.signs().at(Direction.WEST).isEmpty(), "the sign is gone");
        helper.assertTrue(crafting.signs().at(Direction.EAST).isPresent(), "the other one stays");
        helper.assertTrue(player.getMainHandItem().getDamageValue() == 1, "the creator got one use back");

        use(helper, player, PIPE, Direction.UP, true);
        helper.assertTrue(player.getMainHandItem().getDamageValue() == 1, "a face without a sign gives nothing back");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void sneakingInTheAirPicksTheOtherKindOfSign(GameTestHelper helper) {
        TestPlayer player = playerWithCreator(helper, SignKind.CRAFTING);
        ItemStack creator = player.getMainHandItem();
        String craftingName = creator.getHoverName().getString();

        creator.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(SignCreatorItem.modeOf(creator) == SignKind.CRAFTING, "without sneaking nothing changes");

        player.setShiftKeyDown(true);
        creator.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(SignCreatorItem.modeOf(creator) == SignKind.ITEM_AMOUNT, "the next kind");
        helper.assertTrue(!creator.getHoverName().getString().equals(craftingName), "the creator is named after its kind");

        creator.use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertTrue(SignCreatorItem.modeOf(creator) == SignKind.CRAFTING, "and round again");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void aCraftingSignShowsWhatThePipeMakes(GameTestHelper helper) {
        PipeBlockEntity crafting = pipe(helper, PipeKind.CRAFTING, PIPE);
        TestPlayer player = playerWithCreator(helper, SignKind.CRAFTING);

        use(helper, player, PIPE, Direction.NORTH, false);
        SignView blank = crafting.signs().views().get(Direction.NORTH);
        helper.assertTrue(blank.icon().isEmpty() && keys(blank).equals(List.of("routedpipes.sign.empty")),
                "without a recipe the sign says so, got " + keys(blank));

        setRecipe(crafting);
        helper.runAfterDelay(A_LOOK, () -> {
            SignView view = crafting.signs().views().get(Direction.NORTH);
            helper.assertTrue(view.icon().is(Items.STICK) && view.icon().getCount() == 1, "the result is the picture, got " + view.icon());
            helper.assertTrue(view.lines().equals(List.of(new ItemStack(Items.STICK).getHoverName())),
                    "with its name next to it, got " + view.lines());

            crafting.setSatelliteName("7");
        });
        helper.runAfterDelay(2 * A_LOOK, () -> {
            SignView view = crafting.signs().views().get(Direction.NORTH);
            helper.assertTrue(view.lines().equals(List.of(Component.translatable("routedpipes.sign.satellite", "7"),
                    new ItemStack(Items.STICK).getHoverName())), "the satellite is named above the item, got " + view.lines());
            helper.succeed();
        });
    }

    @GameTest(template = EMPTY)
    public static void signsAreSavedWithThePipe(GameTestHelper helper) {
        PipeBlockEntity original = pipe(helper, PipeKind.CRAFTING, PIPE);
        TestPlayer player = playerWithCreator(helper, SignKind.CRAFTING);
        use(helper, player, PIPE, Direction.NORTH, false);
        player.getMainHandItem().set(SignCreatorItem.MODE, SignKind.ITEM_AMOUNT.ordinal());
        use(helper, player, PIPE, Direction.UP, false);

        CompoundTag saved = original.saveWithoutMetadata(helper.getLevel().registryAccess());
        PipeBlockEntity loaded = new PipeBlockEntity(original.getType(), original.getBlockPos(), original.getBlockState());
        loaded.loadWithComponents(saved, helper.getLevel().registryAccess());

        helper.assertTrue(loaded.signs().at(Direction.NORTH).equals(Optional.of(SignKind.CRAFTING)), "the crafting sign is back");
        helper.assertTrue(loaded.signs().at(Direction.UP).equals(Optional.of(SignKind.ITEM_AMOUNT)), "and the item amount sign");
        helper.assertTrue(loaded.signs().at(Direction.DOWN).isEmpty(), "and no other");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void clientsAreToldTheSignsAndWhatIsOnThem(GameTestHelper helper) {
        PipeBlockEntity crafting = pipe(helper, PipeKind.CRAFTING, PIPE);
        setRecipe(crafting);
        crafting.setSatelliteName("3");
        TestPlayer player = playerWithCreator(helper, SignKind.CRAFTING);
        use(helper, player, PIPE, Direction.NORTH, false);

        PipeBlockEntity viewed = new PipeBlockEntity(crafting.getType(), crafting.getBlockPos(), crafting.getBlockState());
        viewed.loadWithComponents(crafting.getUpdateTag(helper.getLevel().registryAccess()), helper.getLevel().registryAccess());

        SignView view = viewed.signs().views().get(Direction.NORTH);
        helper.assertTrue(view != null && viewed.signs().views().size() == 1, "the client knows the one sign");
        helper.assertTrue(view.icon().is(Items.STICK), "its picture, got " + view.icon());
        helper.assertTrue(view.lines().equals(List.of(Component.translatable("routedpipes.sign.satellite", "3"),
                new ItemStack(Items.STICK).getHoverName())), "and its lines, got " + view.lines());

        // a sign that is taken off has to go on the client too
        use(helper, player, PIPE, Direction.NORTH, true);
        viewed.loadWithComponents(crafting.getUpdateTag(helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        helper.assertTrue(viewed.signs().views().isEmpty(), "the sign is gone for the client");
        helper.succeed();
    }

    // picks the item of the item amount sign whose screen the player has open
    private static void pick(TestPlayer player, ItemStack item) {
        player.containerMenu.setCarried(item);
        player.containerMenu.clicked(SignItemMenu.ITEM_SLOT, 0, ClickType.PICKUP, player);
        player.containerMenu.setCarried(ItemStack.EMPTY);
    }

    private static void fillChest(GameTestHelper helper, BlockPos pos, ItemStack stack, int stacks) {
        helper.setBlock(pos, Blocks.CHEST);
        ChestBlockEntity chest = helper.getBlockEntity(pos);
        for (int slot = 0; slot < stacks; slot++) {
            chest.setItem(slot, stack.copy());
        }
    }

    @GameTest(template = EMPTY)
    public static void anItemAmountSignAsksForItsItemAtOnce(GameTestHelper helper) {
        PipeBlockEntity basic = pipe(helper, PipeKind.BASIC, PIPE);
        TestPlayer player = playerWithCreator(helper, SignKind.ITEM_AMOUNT);

        use(helper, player, PIPE, Direction.NORTH, false);
        helper.assertTrue(basic.signs().at(Direction.NORTH).equals(Optional.of(SignKind.ITEM_AMOUNT)), "the sign is up");
        helper.assertTrue(player.containerMenu instanceof SignItemMenu, "and its screen is open");
        helper.assertTrue(keys(basic.signs().views().get(Direction.NORTH)).equals(List.of("routedpipes.sign.empty")),
                "without an item it says so");

        ItemStack held = new ItemStack(Items.COBBLESTONE, 32);
        player.containerMenu.setCarried(held);
        player.containerMenu.clicked(SignItemMenu.ITEM_SLOT, 0, ClickType.PICKUP, player);
        helper.assertTrue(basic.signs().counted(Direction.NORTH).is(Items.COBBLESTONE)
                && basic.signs().counted(Direction.NORTH).getCount() == 1, "the sign counts cobblestone");
        helper.assertTrue(player.containerMenu.getCarried().getCount() == 32, "the player keeps what was clicked with");
        helper.assertTrue(basic.signs().views().get(Direction.NORTH).icon().is(Items.COBBLESTONE), "and shows it right away");

        player.containerMenu.setCarried(ItemStack.EMPTY);
        player.containerMenu.clicked(SignItemMenu.ITEM_SLOT, 0, ClickType.PICKUP, player);
        helper.assertTrue(basic.signs().counted(Direction.NORTH).isEmpty(), "an empty hand clears the sign");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void usingTheCreatorOnAnItemAmountSignOpensItAgain(GameTestHelper helper) {
        PipeBlockEntity basic = pipe(helper, PipeKind.BASIC, PIPE);
        TestPlayer player = playerWithCreator(helper, SignKind.ITEM_AMOUNT);
        use(helper, player, PIPE, Direction.NORTH, false);
        player.containerMenu = player.inventoryMenu;
        // whatever kind the creator is set to
        player.getMainHandItem().set(SignCreatorItem.MODE, SignKind.CRAFTING.ordinal());

        use(helper, player, PIPE, Direction.NORTH, false);

        helper.assertTrue(player.containerMenu instanceof SignItemMenu, "the screen is open again");
        helper.assertTrue(player.getMainHandItem().getDamageValue() == 1, "and only putting the sign up cost a use");
        helper.assertTrue(player.containerMenu.stillValid(player), "the screen stays while the sign is there");
        basic.signs().remove(Direction.NORTH);
        helper.assertTrue(!player.containerMenu.stillValid(player), "and closes when it goes");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void anItemAmountSignGoesOnAnyRoutedPipeButNotOnARequestTable(GameTestHelper helper) {
        PipeBlockEntity provider = pipe(helper, PipeKind.PROVIDER, PIPE);
        PipeBlockEntity fluid = pipe(helper, PipeKind.FLUID_BASIC, PIPE.east(2));
        PipeBlockEntity table = pipe(helper, PipeKind.REQUEST_TABLE, PIPE.east(4));
        TestPlayer player = playerWithCreator(helper, SignKind.ITEM_AMOUNT);

        use(helper, player, PIPE, Direction.UP, false);
        use(helper, player, PIPE.east(2), Direction.UP, false);
        use(helper, player, PIPE.east(4), Direction.UP, false);

        helper.assertTrue(provider.signs().at(Direction.UP).isPresent() && fluid.signs().at(Direction.UP).isPresent(),
                "on a provider and on a fluid pipe");
        helper.assertTrue(table.signs().at(Direction.UP).isEmpty(), "a request table has no room for one");
        helper.assertTrue(player.getMainHandItem().getDamageValue() == 2, "two signs, two uses");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void anItemAmountSignShowsHowManyTheNetworkHolds(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity basic = pipe(helper, PipeKind.BASIC, PIPE);
        pipe(helper, PipeKind.PROVIDER, PIPE.east());
        fillChest(helper, PIPE.east().above(), new ItemStack(Items.COBBLESTONE, 64), 27);
        TestPlayer player = playerWithCreator(helper, SignKind.ITEM_AMOUNT);
        use(helper, player, PIPE, Direction.NORTH, false);
        pick(player, new ItemStack(Items.COBBLESTONE));
        use(helper, player, PIPE, Direction.SOUTH, false);
        pick(player, new ItemStack(Items.DIAMOND));

        helper.succeedWhen(() -> {
            SignView cobblestone = basic.signs().views().get(Direction.NORTH);
            helper.assertTrue(cobblestone.icon().is(Items.COBBLESTONE), "the item is the picture");
            // 27 stacks are 1728
            helper.assertTrue(keys(cobblestone).equals(List.of("routedpipes.sign.amount", "1.72K", "block.minecraft.cobblestone")),
                    "the amount in three digits above the name, got " + keys(cobblestone));
            SignView diamonds = basic.signs().views().get(Direction.SOUTH);
            helper.assertTrue(keys(diamonds).equals(List.of("routedpipes.sign.amount", "0", "item.minecraft.diamond")),
                    "what the network does not have is counted as none, got " + keys(diamonds));
        });
    }

    @GameTest(template = EMPTY)
    public static void theAmountFollowsWhatIsInTheNetwork(GameTestHelper helper) {
        GameTestPower.supply(helper);
        PipeBlockEntity provider = pipe(helper, PipeKind.PROVIDER, PIPE);
        fillChest(helper, PIPE.above(), new ItemStack(Items.COBBLESTONE, 10), 1);
        TestPlayer player = playerWithCreator(helper, SignKind.ITEM_AMOUNT);
        use(helper, player, PIPE, Direction.NORTH, false);
        pick(player, new ItemStack(Items.COBBLESTONE));

        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(keys(provider.signs().views().get(Direction.NORTH)).contains("10"),
                        "the inventory of the pipe itself counts"))
                .thenExecute(() -> helper.<ChestBlockEntity>getBlockEntity(PIPE.above()).setItem(1, new ItemStack(Items.COBBLESTONE, 5)))
                .thenWaitUntil(() -> helper.assertTrue(keys(provider.signs().views().get(Direction.NORTH)).contains("15"),
                        "and the sign keeps up, got " + keys(provider.signs().views().get(Direction.NORTH))))
                .thenSucceed();
    }

    @GameTest(template = EMPTY)
    public static void theItemOfASignIsSavedAndGoesWithTheSign(GameTestHelper helper) {
        PipeBlockEntity original = pipe(helper, PipeKind.BASIC, PIPE);
        TestPlayer player = playerWithCreator(helper, SignKind.ITEM_AMOUNT);
        use(helper, player, PIPE, Direction.EAST, false);
        pick(player, new ItemStack(Items.COBBLESTONE));

        PipeBlockEntity loaded = new PipeBlockEntity(original.getType(), original.getBlockPos(), original.getBlockState());
        loaded.loadWithComponents(original.saveWithoutMetadata(helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        helper.assertTrue(loaded.signs().counted(Direction.EAST).is(Items.COBBLESTONE), "the sign still counts cobblestone");

        // clients only get what is written on the sign
        PipeBlockEntity viewed = new PipeBlockEntity(original.getType(), original.getBlockPos(), original.getBlockState());
        viewed.loadWithComponents(original.getUpdateTag(helper.getLevel().registryAccess()), helper.getLevel().registryAccess());
        SignView view = viewed.signs().views().get(Direction.EAST);
        helper.assertTrue(view.icon().is(Items.COBBLESTONE) && keys(view).equals(
                List.of("routedpipes.sign.amount", "0", "block.minecraft.cobblestone")), "the client draws it, got " + keys(view));
        helper.assertTrue(viewed.signs().counted(Direction.EAST).isEmpty(), "without being told the item apart from that");

        use(helper, player, PIPE, Direction.EAST, true);
        use(helper, player, PIPE, Direction.EAST, false);
        helper.assertTrue(original.signs().counted(Direction.EAST).isEmpty(), "a new sign on the face starts empty");
        helper.succeed();
    }

    // one of the pipes asks the network and tells the others, so all of them know in the same tick
    @GameTest(template = EMPTY)
    public static void aWallOfItemAmountSignsLearnsTheAmountInOneGo(GameTestHelper helper) {
        GameTestPower.supply(helper);
        List<BlockPos> places = List.of(PIPE, PIPE.east(), PIPE.east(2), PIPE.east(2).above());
        List<PipeBlockEntity> wall = places.stream().map(pos -> pipe(helper, PipeKind.BASIC, pos)).toList();
        pipe(helper, PipeKind.PROVIDER, PIPE.east(3));
        fillChest(helper, PIPE.east(3).above(), new ItemStack(Items.COBBLESTONE, 64), 1);
        TestPlayer player = playerWithCreator(helper, SignKind.ITEM_AMOUNT);
        for (BlockPos pos : places) {
            use(helper, player, pos, Direction.NORTH, false);
            pick(player, new ItemStack(Items.COBBLESTONE));
        }

        helper.onEachTick(() -> {
            List<Boolean> knows = wall.stream().map(pipe -> keys(pipe.signs().views().get(Direction.NORTH)).contains("64")).toList();
            if (knows.contains(true)) {
                helper.assertTrue(!knows.contains(false), "every sign of the wall shows the amount as soon as one does, got " + knows);
                helper.succeed();
            }
        });
    }

    @GameTest(template = EMPTY)
    public static void theSignCreatorIsCraftedFromASignAndChips(GameTestHelper helper) {
        ItemStack creator = RecipeGameTest.crafted(helper, 3, 3,
                RecipeGameTest.item("chip_basic"), null, RecipeGameTest.item("chip_basic"),
                null, Items.OAK_SIGN, null,
                null, RecipeGameTest.item("chip_advanced"), null);
        helper.assertTrue(creator.is(LogisticsPipes.SIGN_CREATOR.get()), "got " + creator);
        helper.assertTrue(SignCreatorItem.modeOf(creator) == SignKind.CRAFTING, "a fresh creator makes crafting signs");
        helper.succeed();
    }
}
