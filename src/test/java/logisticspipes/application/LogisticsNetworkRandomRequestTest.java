package logisticspipes.application;

import static logisticspipes.testing.Fixtures.router;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import logisticspipes.domain.crafting.CraftingSetup;
import logisticspipes.domain.crafting.OutputOrder;
import logisticspipes.domain.crafting.Recipe;
import logisticspipes.domain.item.ItemBatch;
import logisticspipes.domain.item.ItemType;
import logisticspipes.domain.logistics.PipeKind;
import logisticspipes.domain.request.CraftJob;
import logisticspipes.domain.request.Provision;
import logisticspipes.domain.request.RequestPlan;
import logisticspipes.domain.routing.Link;
import logisticspipes.domain.routing.RouterId;
import logisticspipes.domain.routing.Side;
import logisticspipes.testing.FakeNode;
import logisticspipes.testing.FakeNode.Shipment;
import logisticspipes.testing.Fixtures.TestItem;
import org.junit.jupiter.api.Test;

// random small recipe graphs, stock and crafters, random requests, and what every plan has to get right
class LogisticsNetworkRandomRequestTest {

    static final long SEED = 20261012L;
    private static final int ROUNDS = 3000;
    private static final long BUILT = -100;
    private static final long NOW = 7;

    private record World(LogisticsNetwork network, RouterId requester, Map<RouterId, FakeNode> providers,
            Map<RouterId, FakeNode> crafters, String description) {
    }

    private static ItemType item(int index) {
        return new TestItem("i" + index);
    }

    private static World world(Random random) {
        LogisticsNetwork network = new LogisticsNetwork();
        int items = 3 + random.nextInt(4);
        List<RouterId> ids = new ArrayList<>();
        List<FakeNode> nodes = new ArrayList<>();
        Map<RouterId, FakeNode> providers = new LinkedHashMap<>();
        Map<RouterId, FakeNode> crafters = new LinkedHashMap<>();
        StringBuilder description = new StringBuilder();
        FakeNode requester = new FakeNode(PipeKind.REQUEST);
        ids.add(router(1));
        nodes.add(requester);
        int providerCount = 1 + random.nextInt(3);
        for (int p = 0; p < providerCount; p++) {
            FakeNode provider = new FakeNode(PipeKind.PROVIDER);
            for (int i = 0; i < items; i++) {
                if (random.nextInt(2) == 0) {
                    provider.stock("i" + i, 1 + random.nextInt(24));
                }
            }
            RouterId id = router(10 + p);
            ids.add(id);
            nodes.add(provider);
            providers.put(id, provider);
            description.append("provider ").append(id.value().getLeastSignificantBits()).append(' ').append(provider.contents).append('\n');
        }
        int crafterCount = 1 + random.nextInt(5);
        for (int c = 0; c < crafterCount; c++) {
            FakeNode crafter = new FakeNode(PipeKind.CRAFTING);
            int output = random.nextInt(items);
            List<ItemBatch> inputs = new ArrayList<>();
            int inputCount = 1 + random.nextInt(2);
            for (int k = 0; k < inputCount; k++) {
                inputs.add(new ItemBatch(item(random.nextInt(items)), 1 + random.nextInt(3)));
            }
            Recipe recipe = new Recipe(inputs, new ItemBatch(item(output), 1 + random.nextInt(3)));
            crafter.crafts = List.of(new CraftingSetup(recipe, Map.of(), random.nextInt(2), java.util.Optional.empty()));
            crafter.inventories.add(Side.UP);
            if (random.nextInt(3) == 0) {
                crafter.contents.put(item(output), 1 + random.nextInt(4));
            }
            RouterId id = router(20 + c);
            ids.add(id);
            nodes.add(crafter);
            crafters.put(id, crafter);
            description.append("crafter ").append(id.value().getLeastSignificantBits()).append(' ').append(recipe)
                    .append(" prio ").append(crafter.crafts.get(0).priority()).append(" holds ").append(crafter.contents).append('\n');
        }
        // shuffled so that distances differ from world to world
        List<Integer> order = new ArrayList<>();
        for (int i = 1; i < ids.size(); i++) {
            order.add(i);
        }
        java.util.Collections.shuffle(order, random);
        order.add(0, 0);
        for (int i = 0; i < ids.size(); i++) {
            network.addRouter(ids.get(i), nodes.get(i), BUILT);
        }
        for (int i = 0; i < order.size(); i++) {
            List<Link> links = new ArrayList<>();
            if (i > 0) {
                links.add(new Link(ids.get(order.get(i - 1)), Side.WEST, 1));
            }
            if (i < order.size() - 1) {
                links.add(new Link(ids.get(order.get(i + 1)), Side.EAST, 1));
            }
            network.updateLinks(ids.get(order.get(i)), links, BUILT);
        }
        return new World(network, router(1), providers, crafters, description.toString());
    }

    // what each source could hand out right now, the way the network counts it
    private static Map<RouterId, Map<ItemType, Integer>> stock(World world) {
        Map<RouterId, Map<ItemType, Integer>> stock = new LinkedHashMap<>();
        world.providers.forEach((id, node) -> stock.put(id, new LinkedHashMap<>(node.contents)));
        world.crafters.forEach((id, node) -> {
            ItemType made = node.crafts.get(0).recipe().output().type();
            int spare = node.contents.getOrDefault(made, 0) - node.outputOrders.owed(made);
            stock.put(id, spare > 0 ? new LinkedHashMap<>(Map.of(made, spare)) : new LinkedHashMap<>());
        });
        return stock;
    }

    private static void checkPlan(World world, RequestPlan plan, Map<RouterId, Map<ItemType, Integer>> before, String context) {
        // provisions never exceed stock
        Map<RouterId, Map<ItemType, Integer>> taken = new HashMap<>();
        for (Provision provision : plan.provisions()) {
            taken.computeIfAbsent(provision.provider(), key -> new HashMap<>()).merge(provision.batch().type(), provision.batch().count(),
                    Integer::sum);
            assertThat(provision.provider()).as(context + "provider is not the destination").isNotEqualTo(provision.destination());
            assertThat(provision.batch().count()).as(context + "provision is positive").isPositive();
        }
        taken.forEach((provider, types) -> types.forEach((type, count) -> assertThat(count)
                .as(context + "taken from " + provider + " of " + type)
                .isLessThanOrEqualTo(before.getOrDefault(provider, Map.of()).getOrDefault(type, 0))));

        // every craft job gets the ingredients for its runs
        Map<RouterId, Integer> runs = new HashMap<>();
        for (CraftJob job : plan.crafts()) {
            FakeNode crafter = world.crafters.get(job.crafter());
            int perRun = crafter.crafts.get(0).recipe().output().count();
            runs.merge(job.crafter(), (job.output().count() + perRun - 1) / perRun, Integer::sum);
            assertThat(job.output().count()).as(context + "job is positive").isPositive();
        }
        runs.forEach((crafterId, count) -> {
            Recipe recipe = world.crafters.get(crafterId).crafts.get(0).recipe();
            recipe.inputTotals().forEach((type, perRun) -> {
                int delivered = plan.provisions().stream()
                        .filter(provision -> provision.destination().equals(crafterId) && provision.batch().type().equals(type))
                        .mapToInt(provision -> provision.batch().count()).sum()
                        + plan.crafts().stream().filter(job -> job.destination().equals(crafterId) && job.output().type().equals(type))
                                .mapToInt(job -> job.output().count()).sum();
                assertThat(delivered).as(context + "ingredient " + type + " for " + count + " runs of " + crafterId)
                        .isGreaterThanOrEqualTo(count * perRun);
            });
        });

        // an item nobody crafts is only missing once all of it is taken
        // ingredients are left out, what is reported for them counts what the failed runs would have used
        plan.missing().forEach((type, count) -> {
            boolean crafted = world.crafters.values().stream().anyMatch(node -> node.crafts.get(0).recipe().output().type().equals(type));
            boolean usedAnywhere = world.crafters.values().stream()
                    .anyMatch(node -> node.crafts.get(0).recipe().inputTotals().containsKey(type));
            if (crafted || usedAnywhere) {
                return;
            }
            int left = 0;
            for (RouterId source : before.keySet()) {
                left += before.get(source).getOrDefault(type, 0) - taken.getOrDefault(source, Map.of()).getOrDefault(type, 0);
            }
            assertThat(left).as(context + "missing " + count + " of " + type + " while sources still hold").isZero();
        });
    }

    private static void checkExecution(World world, RequestPlan plan, Map<RouterId, Integer> shipmentsBefore,
            Map<RouterId, Integer> ordersBefore, String context) {
        Map<List<Object>, Integer> promised = new HashMap<>();
        plan.provisions().forEach(provision -> promised.merge(
                List.of(provision.provider(), provision.destination(), provision.batch().type()), provision.batch().count(), Integer::sum));
        Map<List<Object>, Integer> sent = new HashMap<>();
        Map<RouterId, FakeNode> all = new LinkedHashMap<>(world.providers);
        all.putAll(world.crafters);
        all.forEach((id, node) -> {
            List<Shipment> fresh = node.shipments.subList(shipmentsBefore.get(id), node.shipments.size());
            fresh.forEach(shipment -> sent.merge(List.of(id, shipment.destination(), shipment.batch().type()), shipment.batch().count(),
                    Integer::sum));
        });
        assertThat(sent).as(context + "sent what was promised").isEqualTo(promised);

        Map<List<Object>, Integer> craftPromised = new HashMap<>();
        plan.crafts().forEach(job -> craftPromised.merge(List.of(job.crafter(), job.destination(), job.output().type()),
                job.output().count(), Integer::sum));
        Map<List<Object>, Integer> ordered = new HashMap<>();
        world.crafters.forEach((id, node) -> {
            List<OutputOrder> fresh = node.outputOrders.all().subList(ordersBefore.get(id), node.outputOrders.all().size());
            fresh.forEach(order -> ordered.merge(List.of(id, order.destination(), order.batch().type()), order.batch().count(),
                    Integer::sum));
        });
        assertThat(ordered).as(context + "crafters got what was planned").isEqualTo(craftPromised);
    }

    @Test
    void plansNeverPromiseMoreThanThereIsAndExecuteExactlyWhatTheyPromise() {
        Random random = new Random(SEED);
        int started = 0;
        int nested = 0;
        for (int round = 0; round < ROUNDS; round++) {
            World world = world(random);
            // two requests in the same tick, the second sees what the first left
            for (int step = 0; step < 2; step++) {
                List<ItemBatch> wanted = new ArrayList<>();
                int kinds = 1 + random.nextInt(2);
                int first = random.nextInt(4);
                for (int k = 0; k < kinds; k++) {
                    wanted.add(new ItemBatch(item((first + k) % 4), 1 + random.nextInt(16)));
                }
                String context = "seed " + SEED + " round " + round + " step " + step + " wanted " + wanted + "\n" + world.description;
                Map<RouterId, Map<ItemType, Integer>> before = stock(world);
                Map<RouterId, Integer> shipmentsBefore = new HashMap<>();
                Map<RouterId, Integer> ordersBefore = new HashMap<>();
                world.providers.forEach((id, node) -> shipmentsBefore.put(id, node.shipments.size()));
                world.crafters.forEach((id, node) -> {
                    shipmentsBefore.put(id, node.shipments.size());
                    ordersBefore.put(id, node.outputOrders.all().size());
                });

                // nothing is on its way yet in the first step, so a simulated shortfall order is the same request
                RequestPlan preview = step > 0
                        ? null
                        : world.network.requestShortfall(world.requester, totals(wanted), Map.of(), NOW, true).plan();
                RequestOutcome outcome = world.network.requestList(world.requester, wanted, NOW);
                RequestPlan plan = outcome.plan();
                checkPlan(world, plan, before, context);
                if (preview != null) {
                    assertThat(preview).as(context + "simulated plan matches the real one").isEqualTo(plan);
                }
                if (outcome.started()) {
                    started++;
                    nested += plan.crafts().size() > 1 ? 1 : 0;
                    checkExecution(world, plan, shipmentsBefore, ordersBefore, context);
                } else {
                    assertThat(world.providers.values().stream().mapToInt(node -> node.shipments.size()).sum())
                            .as(context + "nothing moves for an incomplete plan")
                            .isEqualTo(world.providers.keySet().stream().mapToInt(shipmentsBefore::get).sum());
                }
            }
        }
        // the worlds are not all hopeless
        assertThat(started).isGreaterThan(ROUNDS / 4);
        assertThat(nested).isGreaterThan(ROUNDS / 50);
    }

    private static Map<ItemType, Integer> totals(List<ItemBatch> wanted) {
        Map<ItemType, Integer> totals = new LinkedHashMap<>();
        wanted.forEach(batch -> totals.merge(batch.type(), batch.count(), Integer::sum));
        return totals;
    }
}
