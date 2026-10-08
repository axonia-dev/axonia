package dev.axonia.bookmarks.mother;

import java.time.Instant;

import dev.axonia.bookmarks.SaveableType;
import dev.axonia.bookmarks.internal.BookmarkEntity;
import lombok.experimental.UtilityClass;

@UtilityClass
public class BookmarkEntityMother {

    public BookmarkEntity.BookmarkEntityBuilder anyArtifact() {
        return BookmarkEntity.builder().organizationId("default").communityId(1L).saveableType(SaveableType.ARTIFACT)
                .saveableId(100L).memberUserId("bob").sourceAuthorUserId("alice")
                .createdAt(Instant.parse("2026-06-02T00:00:00Z"));
    }

    public BookmarkEntity.BookmarkEntityBuilder anySession() {
        return anyArtifact().saveableType(SaveableType.SESSION).saveableId(500L);
    }
}
