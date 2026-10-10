package dev.axonia.bookmarks.application.port.out;

import dev.axonia.bookmarks.domain.SavedSession;

public interface SessionAccess {

    SavedSession get(long sessionId);
}
