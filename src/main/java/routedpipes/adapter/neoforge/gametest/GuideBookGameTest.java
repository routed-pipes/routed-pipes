package routedpipes.adapter.neoforge.gametest;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import routedpipes.adapter.minecraft.GuideBookItem;
import routedpipes.adapter.neoforge.GuideBookGift;
import routedpipes.adapter.neoforge.RoutedPipes;
import routedpipes.domain.guide.GuideChapter;
import routedpipes.domain.logistics.PipeKind;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder(RoutedPipes.MODID)
@PrefixGameTestTemplate(false)
public class GuideBookGameTest {

    private static final String EMPTY = "empty";

    // the english texts as the client gets them
    private static Set<String> langKeys() {
        try (InputStream in = GuideBookGameTest.class.getResourceAsStream("/assets/routedpipes/lang/en_us.json")) {
            JsonObject lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            return lang.keySet();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static List<Item> modItems() {
        return BuiltInRegistries.ITEM.stream()
                .filter(item -> BuiltInRegistries.ITEM.getKey(item).getNamespace().equals(RoutedPipes.MODID)).toList();
    }

    // the book is made from the registry, so whatever is added later shows up as long as it has a description
    @GameTest(template = EMPTY)
    public static void everyThingOfTheModIsInTheBook(GameTestHelper helper) {
        Set<String> keys = langKeys();
        Map<GuideChapter, List<Item>> book = GuideBookItem.entries(keys::contains);
        List<Item> listed = book.values().stream().flatMap(List::stream).toList();
        List<String> missing = new ArrayList<>();
        for (Item item : modItems()) {
            // never in a player's hands
            if (item == RoutedPipes.FLUID_CONTAINER.get()) {
                continue;
            }
            if (!keys.contains(item.getDescriptionId()) || !listed.contains(item)) {
                missing.add(BuiltInRegistries.ITEM.getKey(item).getPath());
            }
        }
        helper.assertTrue(missing.isEmpty(), "without a name or a description in the book: " + missing);
        helper.assertTrue(!listed.contains(RoutedPipes.FLUID_CONTAINER.get()), "the fluid container is left out");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void thingsAreSortedIntoTheirChapters(GameTestHelper helper) {
        Map<GuideChapter, List<Item>> book = GuideBookItem.entries(langKeys()::contains);
        helper.assertTrue(book.get(GuideChapter.PIPES).contains(RoutedPipes.pipe(PipeKind.CRAFTING).asItem())
                && book.get(GuideChapter.PIPES).contains(RoutedPipes.TUBES.values().iterator().next().get().asItem()), "pipes and tubes");
        helper.assertTrue(book.get(GuideChapter.MODULES).size() == RoutedPipes.MODULES.size(), "every module");
        helper.assertTrue(book.get(GuideChapter.UPGRADES).size() == RoutedPipes.UPGRADES.size(), "every upgrade");
        helper.assertTrue(book.get(GuideChapter.BLOCKS).contains(RoutedPipes.POWER_JUNCTION.get().asItem()), "blocks");
        helper.assertTrue(book.get(GuideChapter.TOOLS).contains(RoutedPipes.SIGN_CREATOR.get())
                && book.get(GuideChapter.TOOLS).contains(RoutedPipes.GUIDE_BOOK.get()), "tools");
        helper.assertTrue(langKeys().contains("routedpipes.guide.start.1"), "and there is a chapter to start with");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void theBookIsCraftedFromABookAndAPipe(GameTestHelper helper) {
        ItemStack book = RecipeGameTest.crafted(helper, 2, 1, Items.BOOK, RoutedPipes.TRANSPORT_PIPE.get().asItem());
        helper.assertTrue(book.is(RoutedPipes.GUIDE_BOOK.get()) && book.getCount() == 1, "got " + book);
        helper.assertTrue(book.getMaxStackSize() == 1, "books do not stack");
        helper.succeed();
    }

    private static void craft(Player player, ItemStack crafted) {
        NeoForge.EVENT_BUS.post(new PlayerEvent.ItemCraftedEvent(player, crafted, new SimpleContainer(1)));
    }

    private static int books(Player player) {
        return player.getInventory().countItem(RoutedPipes.GUIDE_BOOK.get());
    }

    @GameTest(template = EMPTY)
    public static void theFirstThingOfTheModAPlayerCraftsBringsTheBook(GameTestHelper helper) {
        TestPlayer player = TestPlayer.in(helper);

        craft(player, new ItemStack(Items.STICK));
        helper.assertTrue(books(player) == 0, "crafting something else brings nothing");

        craft(player, new ItemStack(RoutedPipes.TRANSPORT_PIPE.get(), 8));
        helper.assertTrue(books(player) == 1, "the first pipe brings the book");

        craft(player, new ItemStack(RoutedPipes.TRANSPORT_PIPE.get(), 8));
        helper.assertTrue(books(player) == 1, "only the first time");
        helper.assertTrue(player.getData(GuideBookGift.GIVEN), "the player is marked");
        helper.succeed();
    }

    @GameTest(template = EMPTY)
    public static void craftingTheBookFirstBringsNoSecondOne(GameTestHelper helper) {
        TestPlayer player = TestPlayer.in(helper);

        craft(player, new ItemStack(RoutedPipes.GUIDE_BOOK.get()));
        craft(player, new ItemStack(RoutedPipes.TRANSPORT_PIPE.get(), 8));

        helper.assertTrue(books(player) == 0, "the crafted book is all there is, got " + books(player));
        helper.succeed();
    }
}
