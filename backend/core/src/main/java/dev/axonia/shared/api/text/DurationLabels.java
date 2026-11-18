package dev.axonia.shared.api.text;

public final class DurationLabels {

    private static final int MINUTES_PER_HOUR = 60;

    private DurationLabels() {
    }

    /** "45 min", "1h", "1h 12"; absent when the session never stated how long it ran. */
    public static String of(Integer minutes) {

        if (minutes == null || minutes <= 0) {
            return null;
        }
        if (minutes < MINUTES_PER_HOUR) {
            return minutes + " min";
        }
        var hours = minutes / MINUTES_PER_HOUR;
        var rest = minutes % MINUTES_PER_HOUR;
        return rest == 0 ? hours + "h" : "%dh %02d".formatted(hours, rest);
    }
}
