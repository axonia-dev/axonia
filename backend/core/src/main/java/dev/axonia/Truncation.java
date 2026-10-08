package dev.axonia;

/**
 * The truncate-on-write mechanic, shared so a persistence adapter states only the bound its own column carries rather
 * than re-deriving the mechanic itself. Free-text notes are the first callers, each writing a {@code reason} column of
 * its own width - the bound is column-specific and stays with the adapter that owns the column, never here.
 */
public final class Truncation {

    private Truncation() {
    }

    /**
     * {@code value} unchanged if it already fits within {@code maxLength}, otherwise cut to it. Null in, null out,
     * since the free text a caller is truncating is often optional.
     */
    public static String truncate(String value, int maxLength) {

        return value == null ? null : value.substring(0, Math.min(value.length(), maxLength));
    }
}
