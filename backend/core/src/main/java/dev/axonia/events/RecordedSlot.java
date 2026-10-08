package dev.axonia.events;

/** The saved session, the id of the slot just recorded, and which of its presenter references placed. */
public record RecordedSlot(Session session, Long slotId, PresenterResolution presenters) {
}
