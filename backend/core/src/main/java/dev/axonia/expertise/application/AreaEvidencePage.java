package dev.axonia.expertise.application;

import java.util.List;

import dev.axonia.expertise.domain.EvidenceItem;

/** One page of an area's full evidence (newest-first), with whether more remains beyond this window. */
public record AreaEvidencePage(List<EvidenceItem> items, boolean hasMore) {
}
