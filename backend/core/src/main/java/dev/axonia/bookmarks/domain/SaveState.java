package dev.axonia.bookmarks.domain;

import lombok.Builder;

/** The viewer's own relationship to a saveable item. Deliberately carries no public save count. */
@Builder
public record SaveState(boolean viewerHasSaved, Boolean viewerHelped) {
}
