package dev.axonia.bookmarks.domain;

import java.time.Instant;

import lombok.experimental.UtilityClass;

@UtilityClass
public class BookmarkMother {

    public Bookmark.BookmarkBuilder anyArtifact() {
        return Bookmark.builder().organizationId("default").communityId(1L).saveableType(SaveableType.ARTIFACT)
                .saveableId(100L).memberUserId("bob").sourceAuthorUserId("alice")
                .createdAt(Instant.parse("2026-06-02T00:00:00Z"));
    }

    public Bookmark.BookmarkBuilder anySession() {
        return anyArtifact().saveableType(SaveableType.SESSION).saveableId(500L);
    }
}
