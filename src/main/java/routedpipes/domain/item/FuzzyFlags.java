package routedpipes.domain.item;

import java.util.Set;

// how loosely a marker slot with a fuzzy upgrade takes the item in it
// tag: anything that shares an item tag with it, tagFamily: anything with a tag of the same family, like any ingot
// ignoreDamage and ignoreData: the same item however worn, and whatever else it carries
public record FuzzyFlags(boolean tag, boolean ignoreDamage, boolean ignoreData, boolean tagFamily) {

    public static final FuzzyFlags NONE = new FuzzyFlags(false, false, false, false);
    public static final int COUNT = 4;

    // one bit per flag in the order they are declared
    public static FuzzyFlags of(int bits) {
        return new FuzzyFlags((bits & 1) != 0, (bits & 2) != 0, (bits & 4) != 0, (bits & 8) != 0);
    }

    public int bits() {
        return (tag ? 1 : 0) | (ignoreDamage ? 2 : 0) | (ignoreData ? 4 : 0) | (tagFamily ? 8 : 0);
    }

    public boolean any() {
        return bits() != 0;
    }

    // the part of a tag name that its relatives share: c:ingots for c:ingots/iron
    static String family(String tag) {
        int slash = tag.indexOf('/');
        return slash < 0 ? tag : tag.substring(0, slash);
    }

    private boolean sharesTag(ItemType wanted, ItemType candidate) {
        Set<String> theirs = candidate.tags();
        for (String mine : wanted.tags()) {
            if (theirs.contains(mine)) {
                return true;
            }
            if (tagFamily && theirs.stream().anyMatch(other -> family(other).equals(family(mine)))) {
                return true;
            }
        }
        return false;
    }

    // whether the candidate will do where the wanted item is asked for
    public boolean matches(ItemType wanted, ItemType candidate) {
        if (wanted.equals(candidate)) {
            return true;
        }
        if ((tag || tagFamily) && sharesTag(wanted, candidate)) {
            return true;
        }
        if (!wanted.plain().equals(candidate.plain())) {
            return false;
        }
        if (!ignoreDamage && wanted.damage() != candidate.damage()) {
            return false;
        }
        return ignoreData || wanted.undamaged().equals(candidate.undamaged());
    }
}
