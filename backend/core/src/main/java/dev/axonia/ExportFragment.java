package dev.axonia;

import java.util.List;
import java.util.Map;

/**
 * What one bounded context contributes to an export package: one JSON file and the media keys it references.
 *
 * <p>
 * A member-scoped fragment contains rows where the person is the actor or subject, never rows where they are merely
 * referenced by somebody else. Their artifacts are theirs; the fact that three colleagues bookmarked one of them is
 * those colleagues' behaviour, and shipping it would answer one person's access request with three other people's data.
 * Erasure is deliberately not symmetric here: it does reach those referencing rows.
 */
public record ExportFragment(String fileName, Map<String, Object> payload, List<String> mediaKeys) {

    /**
     * The answer of a module that holds nothing at all at the scope it was asked for, as opposed to one that holds its
     * tables there and found no rows in them: an Organization-rooted module asked about a {@link ExportScope.Person}.
     * The package leaves its file out rather than carrying one with no tables in it.
     */
    public static ExportFragment nothing(String fileName) {

        return new ExportFragment(fileName, Map.of(), List.of());
    }

    public boolean holdsNothing() {

        return payload.isEmpty() && mediaKeys.isEmpty();
    }
}
