package dev.axonia.expertise;

import java.util.List;

/** One page of an area's full evidence (newest-first), with whether more remains beyond this window. */
public record AreaEvidencePage(List<EvidenceItem> items, boolean hasMore) {
}
