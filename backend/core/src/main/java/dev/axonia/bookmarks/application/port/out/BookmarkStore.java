package dev.axonia.bookmarks.application.port.out;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import dev.axonia.bookmarks.domain.Bookmark;
import dev.axonia.bookmarks.domain.SaveableRef;
import dev.axonia.bookmarks.domain.SaveableType;

public interface BookmarkStore {

    Optional<Bookmark> find(String organizationId, String memberUserId, SaveableRef ref);

    List<Bookmark> listNewestFirst(String organizationId, String memberUserId);

    /** The member's saves among {@code ids} of one kind; an id they never saved is simply absent. */
    List<Bookmark> findAll(String organizationId, String memberUserId, SaveableType type, Collection<Long> ids);

    /** Saving what the member already saved keeps the existing bookmark. */
    void add(Bookmark bookmark);

    void update(Bookmark bookmark);

    void remove(Bookmark bookmark);

    /** Every member's save of an item that no longer exists. */
    void removeAllOf(String organizationId, SaveableRef ref);
}
