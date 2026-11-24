package dev.axonia.bookmarks.domain;

import lombok.Builder;

/** {@link SaveState} for one item of a batch, keyed by the item's id. */
@Builder
public record ItemSaveState(long id, boolean viewerHasSaved, Boolean viewerHelped) {
}
