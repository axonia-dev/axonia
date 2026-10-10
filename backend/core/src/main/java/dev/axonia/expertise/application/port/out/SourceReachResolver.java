package dev.axonia.expertise.application.port.out;

import java.util.Collection;
import java.util.Map;

import dev.axonia.expertise.domain.SourceRef;

/**
 * How many people a contribution reached. Only a presented slot has an answer, so every other source type is absent
 * from the result, as is a slot that no longer resolves. Absence means unknown, never zero.
 */
public interface SourceReachResolver {

    Map<SourceRef, Integer> reachFor(Collection<SourceRef> refs);
}
