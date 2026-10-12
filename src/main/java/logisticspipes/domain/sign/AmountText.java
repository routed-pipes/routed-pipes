package logisticspipes.domain.sign;

// an amount in at most three digits and a unit, cut off and never rounded up so a sign does not promise too much
public final class AmountText {

    private static final String[] UNITS = {"K", "M", "G"};

    private AmountText() {
    }

    public static String of(int amount) {
        if (amount < 1000) {
            return Integer.toString(Math.max(amount, 0));
        }
        long unit = 1000;
        int index = 0;
        while (amount / unit >= 1000) {
            unit *= 1000;
            index++;
        }
        long whole = amount / unit;
        // as many places behind the point as three digits leave room for
        int places = whole >= 100 ? 0 : whole >= 10 ? 1 : 2;
        long scale = places == 0 ? 1 : places == 1 ? 10 : 100;
        long fraction = amount % unit * scale / unit;
        String text = Long.toString(whole);
        if (fraction > 0) {
            String digits = Long.toString(fraction);
            text += "." + ("0".repeat(places - digits.length()) + digits).replaceAll("0+$", "");
        }
        return text + UNITS[index];
    }
}
