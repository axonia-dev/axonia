package dev.axonia;

/**
 * What one eraser did, summed into the receipt. {@code deleted} counts whole rows removed outright - the row existed
 * only to describe this person, or was a live commitment nobody will now honour and gone rather than nulled.
 * {@code cleared} counts columns nulled in place: a live pointer to this person, not a historical fact, on a row that
 * otherwise survives. {@code anonymised} counts columns where the person was replaced by the erasure's surrogate,
 * because the row is community history and survives, unnamed. {@code mediaResidue} counts personal media the eraser
 * could not delete because object storage failed - not a fatal error, but a fact that must survive to the receipt so it
 * can be seen and re-run.
 */
public record ErasureCounts(long deleted, long cleared, long anonymised, long mediaResidue) {

    public static final ErasureCounts NONE = new ErasureCounts(0, 0, 0, 0);

    public ErasureCounts plus(ErasureCounts other) {

        return new ErasureCounts(deleted + other.deleted(), cleared + other.cleared(), anonymised + other.anonymised(),
                mediaResidue + other.mediaResidue());
    }
}
