package dev.axonia.events;

/**
 * One rendered iCalendar document and the community it belongs to. The name is carried alongside the body because the
 * response's filename comes from it, and re-deriving it from the document would mean parsing what was just written.
 */
public record CalendarFeed(String calendarName, String ics) {
}
