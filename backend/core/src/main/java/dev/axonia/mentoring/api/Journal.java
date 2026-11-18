package dev.axonia.mentoring.api;

import java.time.LocalDate;
import java.util.List;

import lombok.Builder;

/**
 * A pairing's shared journal as one of its two people reads it: topics To do, In progress, then Done, each in its board
 * order, and notes by meeting day, newest first. {@code today} and {@code startedOn} are the caller's today and the
 * pairing's first day, both in the caller's zone, between which a note's meeting day is picked. {@code meetings} are
 * the planned meetings no note has held, upcoming and passed, by day; none once the pairing ended. {@code writable} is
 * false once the pairing ended. {@code wordsWritten} tells both people the mentee left words; {@code canWriteWords} is
 * the mentee's alone, once the pairing ended or on words written before; {@code myWords} is their text, and only the
 * mentee reads it here (the mentor reads it on My impact). {@code showing} is whether the community sees the pairing,
 * and the caller's own answer while it asks.
 */
@Builder
public record Journal(PairingSummary pairing, LocalDate today, LocalDate startedOn, boolean writable,
        List<TopicView> topics, List<NoteView> notes, List<PlannedMeetingView> meetings, boolean wordsWritten,
        boolean canWriteWords, String myWords, ShowingView showing) {
}
