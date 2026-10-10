package dev.axonia.expertise.application.port.out;

import java.util.Collection;
import java.util.Map;

import dev.axonia.expertise.domain.SourceRef;

/**
 * Resolves human-readable titles for activity sources referenced by evidence. Sources that no longer resolve (deleted
 * slot/topic/artifact) are omitted from the result, so callers must tolerate missing keys.
 */
public interface SourceLabelResolver {

    Map<SourceRef, String> titlesFor(Collection<SourceRef> refs);
}
