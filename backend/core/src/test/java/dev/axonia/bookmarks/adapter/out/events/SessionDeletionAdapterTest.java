package dev.axonia.bookmarks.adapter.out.events;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.bookmarks.application.port.out.BookmarkStore;
import dev.axonia.bookmarks.domain.SaveableRef;

import static org.mockito.Mockito.verify;

@MockitoSettings
class SessionDeletionAdapterTest {

    @Mock
    private BookmarkStore bookmarkStore;

    @InjectMocks
    private SessionDeletionAdapter sut;

    @Test
    void sessionDeleted() {

        // when
        sut.sessionDeleted("default", 1L, 100L);

        // then
        verify(bookmarkStore).removeAllOf("default", SaveableRef.session(100L));
    }
}
