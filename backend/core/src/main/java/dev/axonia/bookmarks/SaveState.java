package dev.axonia.bookmarks;

import lombok.Builder;

/** The viewer's own relationship to a saveable item. Deliberately carries no public save count. */
@Builder
public record SaveState(boolean viewerHasSaved, Boolean viewerHelped) {
}
