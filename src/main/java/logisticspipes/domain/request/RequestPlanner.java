package logisticspipes.domain.request;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import logisticspipes.domain.item.ItemBatch;
import logisticspipes.domain.item.ItemType;
import logisticspipes.domain.routing.RouterId;

// works out where requested items come from: stock first, then crafting, recursively for the ingredients
// what can be had is planned even when not all of it can, the rest is reported as missing
public final class RequestPlanner {

    // what is planned so far, everything written to it can be taken back to an earlier mark
    // trying out a number of runs must not cost a copy of the stock of a whole base
    private record Holding(RouterId provider, int[] left) {
    }

    private static final class Draft {
        // every provider once, in the order they are used
        private List<Stock> stock = List.of();
        private final Set<RouterId> providers = new HashSet<>();
        // who still holds how many of an item, looked up when the item is first asked for
        // most of what a big base holds has nothing to do with one request
        private final Map<ItemType, List<Holding>> holders = new HashMap<>();
        private final List<Provision> provisions = new ArrayList<>();
        private final List<CraftJob> crafts = new ArrayList<>();
        private Map<ItemType, Integer> missing = new LinkedHashMap<>();
        private final List<Runnable> undo = new ArrayList<>();
        // how many tries this is inside of, in a try anything that cannot be had ends it
        private int trying;
        private boolean cannot;
        private int steps;

        private void fill(List<Stock> entries) {
            boolean twice = false;
            for (Stock entry : entries) {
                twice |= !providers.add(entry.provider());
            }
            if (!twice) {
                stock = entries;
                return;
            }
            // a provider that is listed twice counts once, what is listed later replaces what was listed before
            Map<RouterId, Map<ItemType, Integer>> merged = new LinkedHashMap<>();
            for (Stock entry : entries) {
                merged.computeIfAbsent(entry.provider(), provider -> new LinkedHashMap<>()).putAll(entry.items());
            }
            List<Stock> once = new ArrayList<>();
            merged.forEach((provider, items) -> once.add(new Stock(provider, items)));
            stock = once;
        }

        private List<Holding> holders(ItemType type) {
            List<Holding> known = holders.get(type);
            if (known == null) {
                known = new ArrayList<>();
                for (Stock entry : stock) {
                    Integer count = entry.items().get(type);
                    if (count != null) {
                        known.add(new Holding(entry.provider(), new int[] {count}));
                    }
                }
                holders.put(type, known);
            }
            return known;
        }

        // every kind of item some provider still has
        private Set<ItemType> stocked() {
            Set<ItemType> types = new LinkedHashSet<>();
            for (Stock entry : stock) {
                entry.items().forEach((type, count) -> {
                    int left = count;
                    for (Holding holding : holders.getOrDefault(type, List.of())) {
                        if (holding.provider().equals(entry.provider())) {
                            left = holding.left()[0];
                        }
                    }
                    if (left > 0) {
                        types.add(type);
                    }
                });
            }
            return types;
        }

        private int mark() {
            return undo.size();
        }

        private void backTo(int mark) {
            while (undo.size() > mark) {
                undo.remove(undo.size() - 1).run();
            }
        }

        private void take(Holding from, int count, Provision provision) {
            from.left()[0] -= count;
            provisions.add(provision);
            undo.add(() -> {
                from.left()[0] += count;
                provisions.remove(provisions.size() - 1);
            });
        }

        private void craft(CraftJob job) {
            crafts.add(job);
            undo.add(() -> crafts.remove(crafts.size() - 1));
        }

        private void miss(ItemType type, int count) {
            Integer before = missing.get(type);
            missing.merge(type, count, RequestPlanner::plus);
            undo.add(() -> {
                if (before == null) {
                    missing.remove(type);
                } else {
                    missing.put(type, before);
                }
            });
        }

        private void missInstead(Map<ItemType, Integer> other) {
            Map<ItemType, Integer> before = missing;
            missing = other;
            undo.add(() -> missing = before);
        }
    }

    // finding out how many runs every crafter of a deep recipe tree could do multiplies with every level
    // a plan that has looked at this many amounts stops asking for part of the runs, so it may plan less than could be had
    static final int MAX_STEPS = 100_000;

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
        draft.fill(stock);
        for (ItemBatch batch : wanted) {
            resolve(draft, destination, batch.type(), batch.count(), new HashSet<>());
        }
        return new RequestPlan(mergedProvisions(draft.provisions), merged(draft.crafts), Map.copyOf(FuzzyMatch.exact(draft.missing)));
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

    // type may carry fuzzy flags: then every matching item in stock will do, and so will every crafter that makes one
    private void resolve(Draft draft, RouterId destination, ItemType type, int count, Set<ItemType> crafting) {
        draft.steps++;
        int remaining = count;
        for (ItemType candidate : FuzzyMatch.candidates(type, draft::stocked)) {
            remaining = takeFromStock(draft, destination, candidate, remaining);
        }
        if (remaining == 0) {
            return;
        }
        // an item that is already being crafted further up must not be crafted again for itself
        if (crafting.add(type)) {
            List<Crafter> able = crafters.stream().filter(crafter -> FuzzyMatch.makes(crafter.output().type(), type))
                    .sorted(Comparator.comparingInt(Crafter::priority).reversed()).toList();
            for (int from = 0; from < able.size() && remaining > 0 && !draft.cannot;) {
                int to = from;
                while (to < able.size() && able.get(to).priority() == able.get(from).priority()) {
                    to++;
                }
                remaining = spread(draft, destination, remaining, able.subList(from, to), to == able.size(), crafting);
                from = to;
            }
            if (remaining > 0 && draft.trying == 0 && !able.isEmpty()) {
                // report the ingredients that are short, not the crafted item
                int mark = draft.mark();
                resolveInputs(draft, able.get(0), runsFor(able.get(0), remaining), crafting);
                Map<ItemType, Integer> reported = new LinkedHashMap<>(draft.missing);
                draft.backTo(mark);
                if (!reported.equals(draft.missing)) {
                    draft.missInstead(reported);
                    remaining = 0;
                }
            }
            crafting.remove(type);
        }
        if (remaining > 0 && draft.trying > 0) {
            // whoever is trying only wants to know that it does not work out
            draft.cannot = true;
        } else if (remaining > 0) {
            draft.miss(type, remaining);
        }
    }

    private static int runsFor(Crafter crafter, int items) {
        int perRun = crafter.output().count();
        return (int) ((items + (long) perRun - 1) / perRun);
    }

    private static int plus(int a, int b) {
        return (int) Math.min(Integer.MAX_VALUE, (long) a + b);
    }

    // counts above what an int holds stay at the most it holds, a request that big cannot be met anyway
    private static int times(int count, int factor) {
        return (int) Math.min(Integer.MAX_VALUE, (long) count * factor);
    }

    // shares the work between crafters of the same priority so that they end up equally busy, in whole runs
    // returns how many items could not be crafted
    // last says that no other crafters come after this group
    private int spread(Draft draft, RouterId destination, int count, List<Crafter> group, boolean last, Set<ItemType> crafting) {
        Map<Crafter, Integer> load = new LinkedHashMap<>();
        group.forEach(crafter -> load.put(crafter, crafter.backlog()));
        // machines with the same recipe draw on the same ingredients, what one of them cannot make none of them can
        boolean alike = group.stream().allMatch(crafter -> alike(draft, crafter, group.get(0)));
        int remaining = count;
        while (remaining > 0 && !load.isEmpty() && !draft.cannot) {
            int floor = load.values().stream().mapToInt(Integer::intValue).min().getAsInt();
            List<Crafter> least = load.keySet().stream().filter(crafter -> load.get(crafter) == floor).toList();
            // the least busy are filled up to the next one, or share what is left
            int next = load.values().stream().mapToInt(Integer::intValue).filter(value -> value > floor).min().orElse(Integer.MAX_VALUE);
            int share = Math.min(next - floor, (remaining + least.size() - 1) / least.size());
            for (Crafter crafter : least) {
                if (remaining == 0 || draft.cannot) {
                    break;
                }
                int wanted = runsFor(crafter, Math.min(remaining, share));
                int done = craft(draft, crafter, destination, wanted, remaining, last && (alike || load.size() == 1), crafting);
                int made = Math.min(remaining, times(done, crafter.output().count()));
                remaining -= made;
                load.merge(crafter, times(done, crafter.output().count()), (before, added) -> (int) Math.min(Integer.MAX_VALUE, (long) before + added));
                if (done < wanted) {
                    // ran out of ingredients
                    load.remove(crafter);
                }
            }
        }
        return remaining;
    }

    // whether two crafters ask for the same things in the same helpings, wherever those are delivered
    // a provider never sends to itself, so one that sits on stock is not like any other
    private static boolean alike(Draft draft, Crafter one, Crafter other) {
        return one == other || one.output().equals(other.output()) && one.ownInputs().equals(other.ownInputs())
                && List.copyOf(one.satelliteInputs().values()).equals(List.copyOf(other.satelliteInputs().values()))
                && !onStock(draft, one) && !onStock(draft, other);
    }

    private static boolean onStock(Draft draft, Crafter crafter) {
        return draft.providers.contains(crafter.router()) || crafter.satelliteInputs().keySet().stream().anyMatch(draft.providers::contains);
    }

    // plans as many of the wanted runs as there are ingredients for, returns how many that was
    // the job is for what the crafter says it makes, which is how the crafter knows its orders
    // lastHope says that nobody else can make what this crafter does not
    private int craft(Draft draft, Crafter crafter, RouterId destination, int runs, int needed, boolean lastHope,
            Set<ItemType> crafting) {
        int possible = runs;
        if (!attempt(draft, crafter, possible, crafting)) {
            if (draft.trying > 0 && (lastHope || draft.steps > MAX_STEPS)) {
                // part of the runs would not be enough, so how many it could be does not matter
                draft.cannot = true;
                return 0;
            }
            if (draft.steps > MAX_STEPS) {
                return 0;
            }
            int low = 0;
            int high = runs - 1;
            while (low < high) {
                int middle = (low + high + 1) / 2;
                int mark = draft.mark();
                if (attempt(draft, crafter, middle, crafting)) {
                    low = middle;
                    draft.backTo(mark);
                } else {
                    high = middle - 1;
                }
            }
            possible = low;
            if (possible == 0 || !attempt(draft, crafter, possible, crafting)) {
                return 0;
            }
        }
        draft.craft(new CraftJob(crafter.router(), destination,
                new ItemBatch(crafter.output().type(), Math.min(needed, times(possible, crafter.output().count())))));
        return possible;
    }

    // takes care of the ingredients for that many runs, false and nothing planned if they cannot all be had
    private boolean attempt(Draft draft, Crafter crafter, int runs, Set<ItemType> crafting) {
        int mark = draft.mark();
        draft.trying++;
        resolveInputs(draft, crafter, runs, crafting);
        draft.trying--;
        boolean worked = !draft.cannot;
        draft.cannot = false;
        if (!worked) {
            draft.backTo(mark);
        }
        return worked;
    }

    private void resolveInputs(Draft draft, Crafter crafter, int runs, Set<ItemType> crafting) {
        for (Map.Entry<ItemType, Integer> input : crafter.ownInputs().entrySet()) {
            if (!draft.cannot) {
                resolve(draft, crafter.router(), input.getKey(), times(input.getValue(), runs), crafting);
            }
        }
        crafter.satelliteInputs().forEach((satellite, inputs) -> inputs.forEach((type, count) -> {
            if (!draft.cannot) {
                resolve(draft, satellite, type, times(count, runs), crafting);
            }
        }));
    }

    // returns how many are still needed
    private static int takeFromStock(Draft draft, RouterId destination, ItemType type, int count) {
        int remaining = count;
        for (Holding holding : draft.holders(type)) {
            if (remaining == 0) {
                break;
            }
            int available = holding.left()[0];
            if (available == 0 || holding.provider().equals(destination)) {
                continue;
            }
            int taken = Math.min(available, remaining);
            draft.take(holding, taken, new Provision(holding.provider(), destination, new ItemBatch(type, taken)));
            remaining -= taken;
        }
        return remaining;
    }
}
