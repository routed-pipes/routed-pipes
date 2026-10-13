package routedpipes.application;

import static routedpipes.testing.Fixtures.batch;
import static routedpipes.testing.Fixtures.fluid;
import static routedpipes.testing.Fixtures.router;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import routedpipes.domain.crafting.CraftingSetup;
import routedpipes.domain.crafting.FluidInput;
import routedpipes.domain.crafting.Recipe;
import routedpipes.domain.fluid.FluidType;
import routedpipes.domain.fluid.PackedFluid;
import routedpipes.domain.logistics.PipeKind;
import routedpipes.domain.routing.Link;
import routedpipes.domain.routing.RouterId;
import routedpipes.domain.routing.Side;
import routedpipes.testing.FakeFluidNode;
import routedpipes.testing.FakeFluidNode.Sent;
import routedpipes.testing.FakeNode;
import routedpipes.testing.FakeNode.Shipment;
import org.junit.jupiter.api.Test;

// crafters that take fluid: the fluid is planned with the items of the request and ordered from fluid providers
class LogisticsNetworkFluidCraftingTest {

    private static final RouterId REQUEST = router(1);
    private static final RouterId PROVIDER = router(2);
    private static final RouterId CRAFTING = router(3);
    private static final RouterId TANK = router(4);
    private static final RouterId FLUID_PROVIDER = router(5);
    private static final FluidType LAVA = fluid("lava", 1).type();
    private static final PackedFluid LAVA_ITEM = new PackedFluid(LAVA);
    private static final long BUILT = -100;

    private final LogisticsNetwork network = new LogisticsNetwork();
    private final FakeNode request = new FakeNode(PipeKind.REQUEST);
    private final FakeNode provider = new FakeNode(PipeKind.PROVIDER);
    private final FakeNode crafting = new FakeNode(PipeKind.CRAFTING);
    private final FakeNode tank = new FakeNode(PipeKind.FLUID_SATELLITE);
    private final FakeNode fluidProvider = new FakeNode(PipeKind.FLUID_PROVIDER);
    private final FakeFluidNode lava = new FakeFluidNode();

    // all five in a row, one apart, linked both ways
    private void connect() {
        provider.provides = true;
        tank.fluid = new FakeFluidNode();
        tank.satelliteName = "Tank";
        fluidProvider.fluid = lava;
        List<RouterId> ids = List.of(REQUEST, PROVIDER, CRAFTING, TANK, FLUID_PROVIDER);
        List<FakeNode> nodes = List.of(request, provider, crafting, tank, fluidProvider);
        for (int i = 0; i < ids.size(); i++) {
            network.addRouter(ids.get(i), nodes.get(i), BUILT);
        }
        for (int i = 0; i < ids.size(); i++) {
            List<Link> links = new java.util.ArrayList<>();
            if (i > 0) {
                links.add(new Link(ids.get(i - 1), Side.WEST, 1));
            }
            if (i < ids.size() - 1) {
                links.add(new Link(ids.get(i + 1), Side.EAST, 1));
            }
            network.updateLinks(ids.get(i), links, BUILT);
        }
    }

    // obsidian from a stone and a bucket of lava a run, the lava goes to the fluid satellite called Tank
    private void obsidianCrafter(String satellite) {
        Recipe recipe = new Recipe(List.of(batch("stone", 1)), batch("obsidian", 1));
        crafting.crafts = List.of(new CraftingSetup(recipe, Map.of(), 0, java.util.Optional.empty())
                .withFluids(List.of(new FluidInput(LAVA, 1000, satellite, LAVA_ITEM))));
    }

    private void shipFluid(long from, long to) {
        for (long now = from; now <= to; now++) {
            network.fluids().tick(FLUID_PROVIDER, now);
        }
    }

    @Test
    void theFluidOfARecipeIsOrderedForItsFluidSatelliteWithTheItems() {
        connect();
        provider.stock("stone", 10);
        lava.stock(fluid("lava", 8000));
        obsidianCrafter("Tank");

        RequestOutcome outcome = network.request(REQUEST, batch("obsidian", 3), 0);
        shipFluid(1, 30);

        assertThat(outcome.started()).isTrue();
        assertThat(provider.shipments).containsExactly(new Shipment(batch("stone", 3), CRAFTING));
        assertThat(lava.sent).extracting(Sent::destination).containsOnly(TANK);
        assertThat(lava.sent.stream().mapToInt(sent -> sent.batch().amount()).sum()).isEqualTo(3000);
        assertThat(crafting.outputOrders.owedInTotal()).isEqualTo(3);
    }

    @Test
    void tooLittleFluidStopsTheWholeRequestAndIsReportedInMb() {
        connect();
        provider.stock("stone", 10);
        lava.stock(fluid("lava", 2500));
        obsidianCrafter("Tank");

        RequestOutcome outcome = network.request(REQUEST, batch("obsidian", 3), 0);
        shipFluid(1, 30);

        assertThat(outcome.started()).isFalse();
        assertThat(outcome.missing()).containsExactly(Map.entry(LAVA_ITEM, 500));
        assertThat(provider.shipments).isEmpty();
        assertThat(lava.sent).isEmpty();
    }

    @Test
    void aCrafterWithoutItsFluidSatelliteIsNotUsed() {
        connect();
        provider.stock("stone", 10);
        lava.stock(fluid("lava", 8000));
        obsidianCrafter("Elsewhere");

        assertThat(network.craftable(REQUEST)).isEmpty();
        assertThat(network.request(REQUEST, batch("obsidian", 1), 0).started()).isFalse();
    }

    @Test
    void fluidIsNotPaidForLikeItems() {
        connect();
        provider.stock("stone", 10);
        lava.stock(fluid("lava", 8000));
        obsidianCrafter("Tank");
        provider.power = 0;
        request.power = 0;
        crafting.power = 0;
        tank.power = 0;
        fluidProvider.power = LogisticsNetwork.ENERGY_PER_PROVIDED_ITEM * 2;

        assertThat(network.request(REQUEST, batch("obsidian", 2), 0).started()).isTrue();
    }

    @Test
    void thePreviewListsTheFluidAsTakenFromStock() {
        connect();
        provider.stock("stone", 10);
        lava.stock(fluid("lava", 8000));
        obsidianCrafter("Tank");

        assertThat(network.preview(REQUEST, batch("obsidian", 2), 0).fromStock()).containsEntry(LAVA_ITEM, 2000);
        assertThat(lava.sent).isEmpty();
    }
}
