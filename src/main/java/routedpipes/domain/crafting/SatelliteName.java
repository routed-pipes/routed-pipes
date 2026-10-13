package routedpipes.domain.crafting;

import java.util.Collection;

// the name a satellite pipe goes by, crafters say with it where part of their ingredients goes
public final class SatelliteName {

    public static final int MAX_LENGTH = 32;
    public static final String NONE = "";

    // what becomes of a name a player typed for a satellite
    public enum Verdict {
        ACCEPTED, BLANK, TAKEN
    }

    private SatelliteName() {
    }

    // a name as it is kept: nothing blank around it and not too long
    public static String clean(String typed) {
        String name = typed.strip();
        return name.length() > MAX_LENGTH ? name.substring(0, MAX_LENGTH).strip() : name;
    }

    // satellites used to go by a number, 0 for none, an old world keeps working with that number as the name
    public static String ofNumber(int number) {
        return number == 0 ? NONE : Integer.toString(number);
    }

    // a satellite needs a name, and one no other satellite of its kind within reach has
    public static Verdict check(String name, Collection<String> others) {
        String cleaned = clean(name);
        if (cleaned.isEmpty()) {
            return Verdict.BLANK;
        }
        return others.contains(cleaned) ? Verdict.TAKEN : Verdict.ACCEPTED;
    }
}
