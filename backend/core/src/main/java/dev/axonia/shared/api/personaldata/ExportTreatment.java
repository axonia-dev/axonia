package dev.axonia.shared.api.personaldata;

/**
 * Whether a personal column reaches a data-subject export at all. The inverse of {@link Disposition#EXPORT_ONLY}, which
 * is carried by an export and untouched by one member's erasure.
 *
 * <p>
 * {@code WITHHELD} is not {@link Disposition#RETAIN}. {@code RETAIN} means the column is somebody else's data and sits
 * outside the member regime entirely, so neither erasure nor export touches it. {@code WITHHELD} means the column is
 * squarely the member's, an erasure deletes it exactly as it deletes their name, and an export still declines to print
 * it. Two kinds of column earn that. One is a key into a system rather than a fact about a person: a SCIM id and an
 * avatar's storage key are the cases, where the directory record and the image both leave with the person but the
 * handles axonia filed them under do not need to travel in a file the person forwards to whoever asked. The other is a
 * snapshot of the member taken onto an operator-side record: the row records what an operator did, not something the
 * member did or asked for, so the member's details on it are erased with them and printed nowhere.
 */
public enum ExportTreatment {

    /** Named in the export's projection, like every other personal column. */
    CARRIED,

    /** Erased normally, never exported: a key into a system, or a snapshot on an operator-side record. */
    WITHHELD
}
