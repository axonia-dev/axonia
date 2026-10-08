package dev.axonia;

/** One personal column, what happens to it when a right is exercised, and whether an export prints it. */
public record ColumnFootprint(String column, Classification classification, Disposition disposition,
        ExportTreatment exportTreatment) {

    /**
     * {@link ExportTreatment#WITHHELD} marks a column the member's erasure takes and their export leaves out. An
     * {@link Disposition#EXPORT_ONLY} column is declared only so an export carries it, and a {@link Disposition#RETAIN}
     * one sits outside the member regime, so withholding either is a contradiction rather than a choice.
     */
    public ColumnFootprint {

        if (exportTreatment == ExportTreatment.WITHHELD
                && (disposition == Disposition.EXPORT_ONLY || disposition == Disposition.RETAIN)) {
            throw new IllegalArgumentException(("Column '%s' cannot be WITHHELD with %s: WITHHELD only applies to a "
                    + "column an erasure takes from the member").formatted(column, disposition));
        }
    }

    public ColumnFootprint(String column, Classification classification, Disposition disposition) {
        this(column, classification, disposition, ExportTreatment.CARRIED);
    }

    /**
     * Whether a data-subject export may carry this column: it is neither kept outside the member regime by
     * {@link Disposition#RETAIN} nor held back by {@link ExportTreatment#WITHHELD}. The one condition every export
     * projection and every check of what an export must carry reads.
     */
    public boolean reachesAnExport() {

        return disposition != Disposition.RETAIN && exportTreatment != ExportTreatment.WITHHELD;
    }
}
