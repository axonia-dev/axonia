package dev.axonia.expertise;

import java.util.Collection;
import java.util.Map;

/**
 * Resolves human-readable titles for activity sources referenced by evidence. Sources that no longer resolve (deleted
 * slot/topic/artifact) are omitted from the result, so callers must tolerate missing keys.
 */
public interface SourceLabelResolver {

    Map<SourceRef, String> titlesFor(Collection<SourceRef> refs);
}
