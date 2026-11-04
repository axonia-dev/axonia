package dev.axonia.events.adapter.in.web;

/** A blank or null {@code title} clears the session's own title. */
record RenameSessionRequest(String title) {
}
