package logisticspipes.domain.hud;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import logisticspipes.domain.hud.HudElement.Entry;
import logisticspipes.domain.hud.HudElement.Grid;
import logisticspipes.domain.hud.HudElement.Label;
import logisticspipes.domain.hud.HudElement.Modules;
import logisticspipes.domain.item.ItemBatch;
import logisticspipes.domain.logistics.PipeKind;
import logisticspipes.testing.Fixtures.TestItem;
import org.junit.jupiter.api.Test;

class HudPanelsTest {

    private static final ItemBatch STONE = new ItemBatch(new TestItem("stone"), 1);
    private static final ItemBatch COAL = new ItemBatch(new TestItem("coal"), 40);
    private static final ItemBatch SINK = new ItemBatch(new TestItem("item sink module"), 1);

    private static Label label(String name, Object... args) {
        return new Label(HudText.of("logisticspipes.hud." + name, args), false);
    }

    @Test
    void onlySomeKindsOfPipesHaveAPanel() {
        assertThat(HudCategory.of(PipeKind.CHASSIS)).contains(HudCategory.CHASSIS);
        assertThat(HudCategory.of(PipeKind.BASIC)).contains(HudCategory.CHASSIS);
        assertThat(HudCategory.of(PipeKind.PROVIDER)).contains(HudCategory.PROVIDER);
        assertThat(HudCategory.of(PipeKind.CRAFTING)).contains(HudCategory.CRAFTING);
        assertThat(HudCategory.of(PipeKind.SATELLITE)).contains(HudCategory.SATELLITE);
        assertThat(HudCategory.of(PipeKind.FLUID_SATELLITE)).contains(HudCategory.SATELLITE);
        assertThat(HudCategory.of(PipeKind.SUPPLIER)).contains(HudCategory.CHASSIS);
        assertThat(HudCategory.of(PipeKind.INVENTORY_SYSTEM_CONNECTOR)).contains(HudCategory.CONNECTOR);
        assertThat(HudCategory.of(PipeKind.REQUEST)).isEmpty();
        assertThat(HudCategory.of(PipeKind.FIREWALL)).isEmpty();
        assertThat(HudCategory.of(PipeKind.REQUEST_TABLE)).isEmpty();
    }

    @Test
    void aProviderShowsWhatItProvidesFourInARow() {
        HudPanel panel = HudPanels.of(new HudSource.Provider(List.of(STONE, COAL))).orElseThrow();

        assertThat(panel).isEqualTo(new HudPanel(HudCategory.PROVIDER, 100, 110,
                List.of(label("provided"), new Grid(List.of(STONE, COAL), 4, 12))));
    }

    @Test
    void aProviderWithNothingToProvideHasNoPanel() {
        assertThat(HudPanels.of(new HudSource.Provider(List.of()))).isEmpty();
    }

    @Test
    void longListsAreCut() {
        List<ItemBatch> many = new ArrayList<>();
        for (int index = 0; index < 100; index++) {
            many.add(new ItemBatch(new TestItem("item " + index), 1));
        }

        HudPanel panel = HudPanels.of(new HudSource.Provider(many)).orElseThrow();

        assertThat(((Grid) panel.elements().get(1)).stacks()).isEqualTo(many.subList(0, HudPanels.MAX_LISTED));
    }

    @Test
    void aCraftingPipeShowsItsResultInASmallPanel() {
        HudPanel panel = HudPanels.of(new HudSource.Crafting(Optional.of(STONE), List.of())).orElseThrow();

        assertThat(panel).isEqualTo(new HudPanel(HudCategory.CRAFTING, 60, 47,
                List.of(new Entry(HudText.of("logisticspipes.hud.result"), STONE))));
    }

    @Test
    void aCraftingPipeWithOrdersGrowsAndShowsTheFirstOne() {
        HudPanel panel = HudPanels.of(new HudSource.Crafting(Optional.of(STONE), List.of(COAL, STONE))).orElseThrow();

        assertThat(panel).isEqualTo(new HudPanel(HudCategory.CRAFTING, 100, 58,
                List.of(new Entry(HudText.of("logisticspipes.hud.result"), STONE), new Entry(HudText.of("logisticspipes.hud.todo"), COAL))));
    }

    @Test
    void aCraftingPipeWithoutRecipeAndOrdersHasNoPanel() {
        assertThat(HudPanels.of(new HudSource.Crafting(Optional.empty(), List.of()))).isEmpty();
    }

    @Test
    void aSatelliteAlwaysShowsItsNumber() {
        HudPanel panel = HudPanels.of(new HudSource.Satellite("7", List.of())).orElseThrow();

        assertThat(panel).isEqualTo(new HudPanel(HudCategory.SATELLITE, 100, 35,
                List.of(new Label(HudText.of("logisticspipes.hud.satellite", 7), true))));
    }

    @Test
    void aSatelliteGrowsWhileItemsAreOnTheirWayToIt() {
        HudPanel panel = HudPanels.of(new HudSource.Satellite("7", List.of(COAL))).orElseThrow();

        assertThat(panel.height()).isEqualTo(100);
        assertThat(panel.elements().get(1)).isEqualTo(new Grid(List.of(COAL), 4, 12));
    }

    @Test
    void aBasicPipeShowsItsFilterAndItsDefaultRoute() {
        assertThat(HudPanels.of(new HudSource.Basic(List.of(), false))).isEmpty();
        assertThat(HudPanels.of(new HudSource.Basic(List.of(), true)).orElseThrow().elements()).containsExactly(label("default_route"));
        assertThat(HudPanels.of(new HudSource.Basic(List.of(STONE), true)).orElseThrow().elements())
                .containsExactly(label("takes"), new Grid(List.of(STONE), 3, 12), label("default_route"));
    }

    @Test
    void aChassisListsItsModulesWithWhatIsSetOnThem() {
        HudSource.Module sink = new HudSource.Module(SINK, List.of(STONE), List.of());
        HudSource.Module named = new HudSource.Module(SINK, List.of(), List.of("minecraft"));
        HudSource.Module plain = new HudSource.Module(SINK, List.of(), List.of());

        HudPanel panel = HudPanels.of(new HudSource.Chassis(List.of(sink, named, plain), List.of(STONE), true)).orElseThrow();

        assertThat(panel.category()).isEqualTo(HudCategory.CHASSIS);
        assertThat(panel.elements()).containsExactly(new Modules(List.of(
                new HudElement.Module(SINK, List.of(new Grid(List.of(STONE), 3, 12))),
                new HudElement.Module(SINK, List.of(label("plain", "minecraft"))),
                new HudElement.Module(SINK, List.of(label("nothing")))),
                List.of(new Grid(List.of(STONE), 3, 12), label("default_route"))));
    }

    @Test
    void aChassisWithoutModulesHasNoPanel() {
        assertThat(HudPanels.of(new HudSource.Chassis(List.of(), List.of(), false))).isEmpty();
    }

    @Test
    void aSupplierShowsWhatItKeepsStocked() {
        assertThat(HudPanels.of(new HudSource.Supplier(List.of()))).isEmpty();
        assertThat(HudPanels.of(new HudSource.Supplier(List.of(COAL))).orElseThrow()).isEqualTo(new HudPanel(HudCategory.CHASSIS, 100, 100,
                List.of(label("stocks"), new Grid(List.of(COAL), 3, 12))));
    }

    @Test
    void aConnectorShowsWhatItWaitsForNineAtATime() {
        assertThat(HudPanels.of(new HudSource.Connector(List.of(COAL), 0)).orElseThrow()).isEqualTo(new HudPanel(HudCategory.CONNECTOR,
                100, 100, List.of(label("expected"), new Grid(List.of(COAL), 3, 9))));
    }

    @Test
    void aConnectorKeepsItsPanelForTwoSecondsAfterItGotEverything() {
        assertThat(HudPanels.of(new HudSource.Connector(List.of(), HudPanels.CONNECTOR_LINGER - 1))).isPresent();
        assertThat(HudPanels.of(new HudSource.Connector(List.of(), HudPanels.CONNECTOR_LINGER))).isEmpty();
    }

    @Test
    void powerStoresShowABarAndTheirCharge() {
        assertThat(HudPanels.of(new HudSource.Power(1_000, 4_000_000)).orElseThrow()).isEqualTo(new HudPanel(HudCategory.POWER, 120, 80,
                List.of(label("stored_energy"), new HudElement.Bar(1_000, 4_000_000), label("energy", 1000), label("energy_max", 4000000))));
    }
}
