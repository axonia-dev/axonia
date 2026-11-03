package dev.axonia.bookmarks.adapter.out.events;

import org.springframework.stereotype.Component;

import dev.axonia.bookmarks.application.port.out.BookmarkStore;
import dev.axonia.bookmarks.domain.SaveableRef;
import dev.axonia.events.api.spi.SessionDeletionParticipant;
import lombok.RequiredArgsConstructor;

/** A deleted session is nothing to come back to, so no member keeps it saved. */
@Component
@RequiredArgsConstructor
class SessionDeletionAdapter implements SessionDeletionParticipant {

    private final BookmarkStore bookmarkStore;

    @Override
    public void sessionDeleted(String organizationId, long communityId, long sessionId) {

        bookmarkStore.removeAllOf(organizationId, SaveableRef.session(sessionId));
    }
}
