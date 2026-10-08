package dev.axonia.health;

/**
 * A dial's movement vs. the prior window: the previous value plus its direction. The current value lives on the dial
 * itself.
 */
public record Trend(double previous, Direction direction) {

    public enum Direction {
        UP, DOWN, FLAT
    }

    public static Trend of(double current, double previous) {

        if (Math.abs(current - previous) < 1e-9) {
            return new Trend(previous, Direction.FLAT);
        }
        return new Trend(previous, current > previous ? Direction.UP : Direction.DOWN);
    }
}
