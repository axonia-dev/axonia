package dev.axonia.mentoring.domain;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Comparator;

/** A pairing's notes counted: how many, on how many distinct meeting days, and the latest of those days. */
public record NoteTally(int notes, int meetings, LocalDate lastMetOn) {

    public static final NoteTally NONE = new NoteTally(0, 0, null);

    public static NoteTally of(Collection<Note> notes) {

        var days = notes.stream().map(Note::meetingOn).distinct().toList();
        return new NoteTally(notes.size(), days.size(), days.stream().max(Comparator.naturalOrder()).orElse(null));
    }
}
