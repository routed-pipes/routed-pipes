package logisticspipes.domain.request;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import logisticspipes.domain.item.ItemBatch;
import logisticspipes.domain.item.ItemType;
import logisticspipes.domain.routing.RouterId;

// works out where requested items come from: stock first, then crafting, recursively for the ingredients
// what can be had is planned even when not all of it can, the rest is reported as missing
public final class RequestPlanner {

    private static final class Draft {
        private final Map<RouterId, Map<ItemType, Integer>> stock = new LinkedHashMap<>();
        private final List<Provision> provisions = new ArrayList<>();
        private final List<CraftJob> crafts = new ArrayList<>();
        private final Map<ItemType, Integer> missing = new LinkedHashMap<>();

        private Draft copy() {
            Draft copy = new Draft();
            stock.forEach((provider, items) -> copy.stock.put(provider, new LinkedHashMap<>(items)));
            copy.provisions.addAll(provisions);
            copy.crafts.addAll(crafts);
            copy.missing.putAll(missing);
            return copy;
        }

        private void become(Draft other) {
            stock.clear();
            stock.putAll(other.stock);
            provisions.clear();
            provisions.addAll(other.provisions);
            crafts.clear();
            crafts.addAll(other.crafts);
            missing.clear();
            missing.putAll(other.missing);
        }
    }

    private final List<Stock> stock;
    private final List<Crafter> crafters;

    // stock and crafters are used in the given order, so pass the nearest providers and the least busy crafters first
    public RequestPlanner(List<Stock> stock, List<Crafter> crafters) {
        this.stock = List.copyOf(stock);
        this.crafters = List.copyOf(crafters);
    }

    public RequestPlan plan(RouterId destination, ItemBatch wanted) {
        return plan(destination, List.of(wanted));
    }

    // several items for one destination in one plan, a later one only gets what the earlier ones left
    public RequestPlan plan(RouterId destination, List<ItemBatch> wanted) {
        Draft draft = new Draft();
        for (Stock entry : stock) {
            draft.stock.computeIfAbsent(entry.provider(), provider -> new LinkedHashMap<>()).putAll(entry.items());
        }
        for (ItemBatch batch : wanted) {
            resolve(draft, destination, batch.type(), batch.count(), new HashSet<>());
        }
        return new RequestPlan(mergedProvisions(draft.provisions), merged(draft.crafts), Map.copyOf(draft.missing));
    }

    private static List<Provision> mergedProvisions(List<Provision> provisions) {
        Map<List<Object>, Integer> totals = new LinkedHashMap<>();
        for (Provision provision : provisions) {
            totals.merge(List.of(provision.provider(), provision.destination(), provision.batch().type()), provision.batch().count(),
                    Integer::sum);
        }
        List<Provision> merged = new ArrayList<>();
        totals.forEach((key, count) -> merged.add(new Provision((RouterId) key.get(0), (RouterId) key.get(1),
                new ItemBatch((ItemType) key.get(2), count))));
        return List.copyOf(merged);
    }

    // a crafter that got its work in several helpings still gets one job per destination and item
    private static List<CraftJob> merged(List<CraftJob> jobs) {
        List<CraftJob> merged = new ArrayList<>();
        for (CraftJob job : jobs) {
            int same = -1;
            for (int index = 0; index < merged.size(); index++) {
                CraftJob other = merged.get(index);
                if (other.crafter().equals(job.crafter()) && other.destination().equals(job.destination())
                        && other.output().type().equals(job.output().type())) {
                    same = index;
                }
            }
            if (same < 0) {
                merged.add(job);
            } else {
                int total = merged.get(same).output().count() + job.output().count();
                merged.set(same, new CraftJob(job.crafter(), job.destination(), new ItemBatch(job.output().type(), total)));
            }
        }
        return List.copyOf(merged);
    }

    private void resolve(Draft draft, RouterId destination, ItemType type, int count, Set<ItemType> crafting) {
        int remaining = takeFromStock(draft, destination, type, count);
        if (remaining == 0) {
            return;
        }
        // an item that is already being crafted further up must not be crafted again for itself
        if (crafting.add(type)) {
            List<Crafter> able = crafters.stream().filter(crafter -> crafter.output().type().equals(type))
                    .sorted(Comparator.comparingInt(Crafter::priority).reversed()).toList();
            for (int from = 0; from < able.size() && remaining > 0;) {
                int to = from;
                while (to < able.size() && able.get(to).priority() == able.get(from).priority()) {
                    to++;
                }
                remaining = spread(draft, destination, type, remaining, able.subList(from, to), crafting);
                from = to;
            }
            if (remaining > 0 && !able.isEmpty()) {
                // report the ingredients that are short, not the crafted item
                Draft report = draft.copy();
                resolveInputs(report, able.get(0), runsFor(able.get(0), remaining), crafting);
                if (!report.missing.equals(draft.missing)) {
                    draft.missing.clear();
                    draft.missing.putAll(report.missing);
                    remaining = 0;
                }
            }
            crafting.remove(type);
        }
        if (remaining > 0) {
            draft.missing.merge(type, remaining, Integer::sum);
        }
    }

    private static int runsFor(Crafter crafter, int items) {
        int perRun = crafter.output().count();
        return (items + perRun - 1) / perRun;
    }

    // shares the work between crafters of the same priority so that they end up equally busy, in whole runs
    // returns how many items could not be crafted
    private int spread(Draft draft, RouterId destination, ItemType type, int count, List<Crafter> group, Set<ItemType> crafting) {
        Map<Crafter, Integer> load = new LinkedHashMap<>();
        group.forEach(crafter -> load.put(crafter, crafter.backlog()));
        int remaining = count;
        while (remaining > 0 && !load.isEmpty()) {
            int floor = load.values().stream().mapToInt(Integer::intValue).min().getAsInt();
            List<Crafter> least = load.keySet().stream().filter(crafter -> load.get(crafter) == floor).toList();
            // the least busy are filled up to the next one, or share what is left
            int next = load.values().stream().mapToInt(Integer::intValue).filter(value -> value > floor).min().orElse(Integer.MAX_VALUE);
            int share = Math.min(next - floor, (remaining + least.size() - 1) / least.size());
            for (Crafter crafter : least) {
                if (remaining == 0) {
                    break;
                }
                int wanted = runsFor(crafter, Math.min(remaining, share));
                int done = craft(draft, crafter, destination, type, wanted, remaining, crafting);
                int made = Math.min(remaining, done * crafter.output().count());
                remaining -= made;
                load.merge(crafter, done * crafter.output().count(), Integer::sum);
                if (done < wanted) {
                    // ran out of ingredients
                    load.remove(crafter);
                }
            }
        }
        return remaining;
    }

    // plans as many of the wanted runs as there are ingredients for, returns how many that was
    private int craft(Draft draft, Crafter crafter, RouterId destination, ItemType type, int runs, int needed, Set<ItemType> crafting) {
        int possible = runs;
        Draft attempt = attempt(draft, crafter, possible, crafting);
        if (attempt == null) {
            int low = 0;
            int high = runs - 1;
            while (low < high) {
                int middle = (low + high + 1) / 2;
                if (attempt(draft, crafter, middle, crafting) != null) {
                    low = middle;
                } else {
                    high = middle - 1;
                }
            }
            possible = low;
            attempt = possible == 0 ? null : attempt(draft, crafter, possible, crafting);
        }
        if (attempt == null) {
            return 0;
        }
        attempt.crafts.add(new CraftJob(crafter.router(), destination, new ItemBatch(type, Math.min(needed, possible * crafter.output().count()))));
        draft.become(attempt);
        return possible;
    }

    // the draft with the ingredients for that many runs taken care of, null if they cannot all be had
    private Draft attempt(Draft draft, Crafter crafter, int runs, Set<ItemType> crafting) {
        Draft attempt = draft.copy();
        resolveInputs(attempt, crafter, runs, crafting);
        return attempt.missing.equals(draft.missing) ? attempt : null;
    }

    private void resolveInputs(Draft draft, Crafter crafter, int runs, Set<ItemType> crafting) {
        for (Map.Entry<ItemType, Integer> input : crafter.ownInputs().entrySet()) {
            resolve(draft, crafter.router(), input.getKey(), input.getValue() * runs, crafting);
        }
        for (Map.Entry<ItemType, Integer> input : crafter.satelliteInputs().entrySet()) {
            resolve(draft, crafter.satellite(), input.getKey(), input.getValue() * runs, crafting);
        }
    }

    // returns how many are still needed
    private static int takeFromStock(Draft draft, RouterId destination, ItemType type, int count) {
        int remaining = count;
        for (Map.Entry<RouterId, Map<ItemType, Integer>> entry : draft.stock.entrySet()) {
            if (remaining == 0) {
                break;
            }
            int available = entry.getValue().getOrDefault(type, 0);
            if (available == 0 || entry.getKey().equals(destination)) {
                continue;
            }
            int taken = Math.min(available, remaining);
            entry.getValue().put(type, available - taken);
            draft.provisions.add(new Provision(entry.getKey(), destination, new ItemBatch(type, taken)));
            remaining -= taken;
        }
        return remaining;
    }
}
