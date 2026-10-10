package dev.axonia.bookmarks.application.port.out;

import java.util.List;
import java.util.Optional;

import dev.axonia.bookmarks.domain.Bookmark;
import dev.axonia.bookmarks.domain.SaveableRef;

public interface BookmarkStore {

    Optional<Bookmark> find(String organizationId, String memberUserId, SaveableRef ref);

    List<Bookmark> listNewestFirst(String organizationId, String memberUserId);

    /** Saving what the member already saved keeps the existing bookmark. */
    void add(Bookmark bookmark);

    void update(Bookmark bookmark);

    void remove(Bookmark bookmark);
}
