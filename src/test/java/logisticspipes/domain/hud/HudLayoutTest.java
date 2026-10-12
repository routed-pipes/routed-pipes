package logisticspipes.domain.hud;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import logisticspipes.domain.hud.HudAction.Kind;
import logisticspipes.domain.hud.HudElement.Grid;
import logisticspipes.domain.hud.HudElement.Label;
import logisticspipes.domain.hud.HudShape.Button;
import logisticspipes.domain.hud.HudShape.Stack;
import logisticspipes.domain.hud.HudShape.Text;
import logisticspipes.domain.item.ItemBatch;
import logisticspipes.testing.Fixtures.TestItem;
import org.junit.jupiter.api.Test;

class HudLayoutTest {

    private static final HudText TITLE = HudText.of("title");

    private static ItemBatch item(int number) {
        return new ItemBatch(new TestItem("item " + number), 1);
    }

    private static List<ItemBatch> items(int count) {
        List<ItemBatch> items = new ArrayList<>();
        for (int number = 0; number < count; number++) {
            items.add(item(number));
        }
        return items;
    }

    private static HudPanel listPanel(int count) {
        return new HudPanel(HudCategory.PROVIDER, 100, 110, List.of(new Label(TITLE, false), new Grid(items(count), 4, 12)));
    }

    private static HudPanel chassis(int modules) {
        List<HudElement.Module> list = new ArrayList<>();
        for (int number = 0; number < modules; number++) {
            list.add(new HudElement.Module(item(100 + number), List.of(new Grid(items(number + 1), 3, 12))));
        }
        return new HudPanel(HudCategory.CHASSIS, 100, 100, List.of(new HudElement.Modules(list, List.of(new Label(TITLE, false)))));
    }

    private static List<Button> buttons(List<HudShape> shapes) {
        return shapes.stream().filter(Button.class::isInstance).map(Button.class::cast).toList();
    }

    private static List<ItemBatch> stacks(List<HudShape> shapes) {
        return shapes.stream().filter(Stack.class::isInstance).map(shape -> ((Stack) shape).batch()).toList();
    }

    @Test
    void aShortListGoesUnderItsLabelInRowsWithoutAPager() {
        List<HudShape> shapes = HudLayout.place(listPanel(5), new HudView());

        assertThat(shapes).containsExactly(
                new Text(-45, -50, 90, TITLE, false),
                new Stack(-35, -39, item(0)), new Stack(-17, -39, item(1)), new Stack(1, -39, item(2)), new Stack(19, -39, item(3)),
                new Stack(-35, -21, item(4)));
    }

    @Test
    void aLongListGetsAPagerAndShowsItsFirstPage() {
        List<HudShape> shapes = HudLayout.place(listPanel(30), new HudView());

        assertThat(buttons(shapes)).containsExactly(
                new Button(35, -40, 10, 9, ">", Optional.empty(), new HudAction(Kind.TURN_PAGE, 1, 1), false));
        assertThat(shapes).contains(new Text(0, -39, 70, HudText.of("logisticspipes.hud.page", 1, 3), true));
        assertThat(stacks(shapes)).isEqualTo(items(30).subList(0, 12));
        assertThat(shapes).contains(new Stack(-35, -28, item(0)));
    }

    @Test
    void pressingAPagerButtonTurnsThePage() {
        HudView view = new HudView();
        HudPanel panel = listPanel(30);

        view.apply(buttons(HudLayout.place(panel, view)).get(0).action());
        List<HudShape> second = HudLayout.place(panel, view);

        assertThat(stacks(second)).isEqualTo(items(30).subList(12, 24));
        assertThat(buttons(second)).extracting(Button::label).containsExactly("<", ">");

        view.apply(buttons(second).get(1).action());
        List<HudShape> last = HudLayout.place(panel, view);

        assertThat(stacks(last)).isEqualTo(items(30).subList(24, 30));
        assertThat(buttons(last)).extracting(Button::label).containsExactly("<");
    }

    @Test
    void aListThatGotShorterShowsItsLastPageAndCanBeTurnedBack() {
        HudView view = new HudView();
        view.apply(new HudAction(Kind.TURN_PAGE, 1, 2));

        List<HudShape> shapes = HudLayout.place(listPanel(13), view);

        assertThat(stacks(shapes)).containsExactly(item(12));
        assertThat(buttons(shapes)).containsExactly(
                new Button(-45, -40, 10, 9, "<", Optional.empty(), new HudAction(Kind.TURN_PAGE, 1, 0), false));
    }

    @Test
    void aChassisShowsThreeModulesAtATimeAndThePipeBesideThem() {
        List<HudShape> shapes = HudLayout.place(chassis(4), new HudView());

        assertThat(buttons(shapes)).containsExactly(
                new Button(-33, -45, 10, 9, ">", Optional.empty(), new HudAction(Kind.TURN_MODULES, 0, 1), false),
                new Button(-45, -35, 20, 25, "", Optional.of(item(100)), new HudAction(Kind.PICK_MODULE, 0, 0), false),
                new Button(-45, -8, 20, 25, "", Optional.of(item(101)), new HudAction(Kind.PICK_MODULE, 0, 1), false),
                new Button(-45, 19, 20, 25, "", Optional.of(item(102)), new HudAction(Kind.PICK_MODULE, 0, 2), false));
        assertThat(shapes).contains(new Text(-23, -35, 68, TITLE, false));
    }

    @Test
    void theSecondPageOfModulesHasTheFourth() {
        HudView view = new HudView();
        view.apply(new HudAction(Kind.TURN_MODULES, 0, 1));

        assertThat(buttons(HudLayout.place(chassis(4), view))).containsExactly(
                new Button(-45, -45, 10, 9, "<", Optional.empty(), new HudAction(Kind.TURN_MODULES, 0, 0), false),
                new Button(-45, -35, 20, 25, "", Optional.of(item(103)), new HudAction(Kind.PICK_MODULE, 0, 3), false));
    }

    @Test
    void aPickedModuleShowsItsContentUntilItIsUnpicked() {
        HudView view = new HudView();
        view.apply(new HudAction(Kind.PICK_MODULE, 0, 1));

        List<HudShape> shapes = HudLayout.place(chassis(3), view);

        assertThat(stacks(shapes)).containsExactly(item(0), item(1));
        assertThat(shapes).doesNotContain(new Text(-23, -35, 68, TITLE, false));
        assertThat(buttons(shapes)).filteredOn(Button::chosen).extracting(button -> button.action().value()).containsExactly(1);
        Button unpick = new Button(37, -45, 8, 9, "x", Optional.empty(), new HudAction(Kind.UNPICK_MODULE, 0, 0), false);
        assertThat(buttons(shapes)).contains(unpick);

        view.apply(unpick.action());

        assertThat(HudLayout.place(chassis(3), view)).contains(new Text(-23, -35, 68, TITLE, false));
    }

    @Test
    void aPickedModuleThatWasPulledOutCountsAsNothingPicked() {
        HudView view = new HudView();
        view.apply(new HudAction(Kind.PICK_MODULE, 0, 2));

        assertThat(HudLayout.place(chassis(2), view)).contains(new Text(-23, -35, 68, TITLE, false));
    }

    @Test
    void anEntryHasItsStackAtTheRight() {
        HudPanel panel = new HudPanel(HudCategory.CRAFTING, 60, 47, List.of(new HudElement.Entry(TITLE, item(1))));

        assertThat(HudLayout.place(panel, new HudView())).containsExactly(new Text(-25, -13, 32, TITLE, false), new Stack(8, -17, item(1)));
    }

    @Test
    void aBarIsFilledByHowFullTheStoreIs() {
        HudPanel panel = new HudPanel(HudCategory.POWER, 120, 80,
                List.of(new HudElement.Bar(1, 4), new HudElement.Bar(9, 4), new HudElement.Bar(1, 0)));

        assertThat(HudLayout.place(panel, new HudView())).containsExactly(new HudShape.Bar(-55, -35, 110, 10, 0.25),
                new HudShape.Bar(-55, -22, 110, 10, 1), new HudShape.Bar(-55, -9, 110, 10, 0));
    }

    @Test
    void buttonsKnowWhatIsOnThem() {
        Button button = new Button(10, 20, 10, 9, "<", Optional.empty(), new HudAction(Kind.TURN_PAGE, 0, 0), false);

        assertThat(button.contains(10, 20)).isTrue();
        assertThat(button.contains(19.9, 28.9)).isTrue();
        assertThat(button.contains(20, 20)).isFalse();
        assertThat(button.contains(9.9, 20)).isFalse();
    }

    @Test
    void stacksShowHowManyTheyAreInShort() {
        assertThat(new Stack(0, 0, new ItemBatch(new TestItem("a"), 1)).amount()).isEmpty();
        assertThat(new Stack(0, 0, new ItemBatch(new TestItem("a"), 64)).amount()).isEqualTo("64");
        assertThat(new Stack(0, 0, new ItemBatch(new TestItem("a"), 12_345)).amount()).isEqualTo("12k");
        assertThat(new Stack(0, 0, new ItemBatch(new TestItem("a"), 3_000_000)).amount()).isEqualTo("3M");
    }
}
