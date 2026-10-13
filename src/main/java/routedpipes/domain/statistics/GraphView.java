package routedpipes.domain.statistics;

// which part of an amount history a graph shows, and where a sample ends up on it
// a sample is told by its age: 0 is the newest, one more for every minute before that
// x runs from the left edge, y from the bottom edge, both in pixels
public final class GraphView {

    private static final double MIN_SAMPLE_WIDTH = 0.1;
    private static final double MAX_SAMPLE_WIDTH = 50;
    private static final double MIN_ITEM_HEIGHT = 1e-7;
    private static final double MAX_ITEM_HEIGHT = 50;
    // minutes between two labels on the time axis, the first that leaves enough room is used
    private static final int[] AGE_STEPS = {1, 2, 5, 10, 15, 30, 60, 120, 180, 360, 720};

    private final int width;
    private final int height;
    private double sampleWidth = 15;
    private double itemHeight = 15;
    // the age at the right edge and the amount at the bottom edge
    private double rightAge;
    private double bottom;

    public GraphView(int width, int height) {
        this.width = width;
        this.height = height;
    }

    public double x(double age) {
        return width - (age - rightAge) * sampleWidth;
    }

    public double y(double amount) {
        return (amount - bottom) * itemHeight;
    }

    public boolean shows(double x, double y) {
        return x >= 0 && x <= width && y >= 0 && y <= height;
    }

    // moves what is shown along with the mouse, never past the newest or the oldest sample a history can hold
    public void pan(double right, double up) {
        rightAge = clamp(rightAge + right / sampleWidth, 0, AmountHistory.SAMPLES - 1);
        bottom -= up / itemHeight;
    }

    // the right edge stays where it is
    public void scaleTime(double factor) {
        sampleWidth = clamp(sampleWidth * factor, MIN_SAMPLE_WIDTH, MAX_SAMPLE_WIDTH);
    }

    // the bottom edge stays where it is
    public void scaleAmount(double factor) {
        itemHeight = clamp(itemHeight * factor, MIN_ITEM_HEIGHT, MAX_ITEM_HEIGHT);
    }

    // sets the amounts up so that the samples in view fill the height with a little room around them
    public void fit(long[] oldestFirst) {
        long low = Long.MAX_VALUE;
        long high = Long.MIN_VALUE;
        for (int age = 0; age < oldestFirst.length; age++) {
            double x = x(age);
            if (x >= 0 && x <= width) {
                long amount = oldestFirst[oldestFirst.length - 1 - age];
                low = Math.min(low, amount);
                high = Math.max(high, amount);
            }
        }
        if (low > high) {
            return;
        }
        double range = Math.max(1, high - low);
        itemHeight = clamp(height / (range * 1.2), MIN_ITEM_HEIGHT, MAX_ITEM_HEIGHT);
        bottom = low - range * 0.1;
    }

    // minutes between two labels on the time axis that are at least the given pixels apart
    public int ageStep(int pixels) {
        for (int step : AGE_STEPS) {
            if (step * sampleWidth >= pixels) {
                return step;
            }
        }
        return AmountHistory.SAMPLES;
    }

    // items between two labels on the amount axis that are at least the given pixels apart: 1, 2 or 5 times a power of ten
    public long amountStep(int pixels) {
        long magnitude = 1;
        while (true) {
            for (int factor : new int[] {1, 2, 5}) {
                if (factor * magnitude * itemHeight >= pixels) {
                    return factor * magnitude;
                }
            }
            magnitude *= 10;
        }
    }

    // the oldest and the newest age in view, for the labels of the time axis
    public double leftAge() {
        return rightAge + width / sampleWidth;
    }

    public double rightAge() {
        return rightAge;
    }

    // the lowest and the highest amount in view
    public double bottom() {
        return bottom;
    }

    public double top() {
        return bottom + height / itemHeight;
    }

    // how long ago a sample was taken, as the time axis says it
    public static String ago(int minutes) {
        if (minutes <= 0) {
            return "Now";
        }
        if (minutes < 60) {
            return minutes + "min";
        }
        return minutes % 60 == 0 ? minutes / 60 + "h" : minutes / 60 + "h" + minutes % 60 + "min";
    }

    private static double clamp(double value, double low, double high) {
        return Math.max(low, Math.min(high, value));
    }
}
