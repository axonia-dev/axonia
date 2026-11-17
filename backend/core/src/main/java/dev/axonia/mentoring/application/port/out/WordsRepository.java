package dev.axonia.mentoring.application.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import dev.axonia.mentoring.domain.Words;
import dev.axonia.mentoring.domain.WordsForMentor;

public interface WordsRepository {

    Optional<Words> find(String org, long pairingId);

    /**
     * Creates the words, true, or replaces them, false. A changed body is stamped {@code updatedAt} with the given
     * {@code writtenAt} and sealed again ({@code openedAt} cleared); the same body again changes nothing.
     */
    boolean upsert(String org, Words words);

    /** Stamps the first opening of the words by the mentor only; later openings keep it. */
    void markOpened(String org, long pairingId, Instant at);

    void delete(String org, long pairingId);

    /** Every words row on a pairing where the user is the mentor, newest first. */
    List<WordsForMentor> toMentor(String org, String mentorId);
}
