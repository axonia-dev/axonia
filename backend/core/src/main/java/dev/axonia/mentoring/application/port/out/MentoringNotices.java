package dev.axonia.mentoring.application.port.out;

import dev.axonia.mentoring.domain.MentoringGuild;
import dev.axonia.mentoring.domain.Pairing;
import dev.axonia.mentoring.domain.Proposal;
import lombok.Builder;

/** What the two people of a proposal or a pairing are told. No notice carries a message or words text. */
public interface MentoringNotices {

    /** Mailed and in the panel, to the receiver. */
    void proposed(String organizationId, Proposal proposal, MentoringGuild guild, String proposerName);

    /** In the panel, to the sender: a neutral "not this time" over the first topic, with no reason and no message. */
    void declined(Proposal proposal, MentoringGuild guild, String declinerName);

    /**
     * Takes back the "not this time" of a {@code declined} proposal while it still waits in the outbox, so its sender
     * is never shown it; true when it was taken back, false when it may already have been read.
     */
    boolean withdrawDeclined(Proposal declined);

    /** In the panel, to the sender: the proposal as just reopened by the person who declined it. */
    void reopened(Proposal proposal, MentoringGuild guild, String reopenerName);

    /**
     * In the panel, to {@code recipientId}, the side that did not end it; a mentee is invited to write the postcard.
     */
    void ended(Pairing pairing, MentoringGuild guild, String endedByName, String recipientId);

    /** In the panel, to the mentor. */
    void wordsReceived(Pairing pairing, MentoringGuild guild, String menteeName);

    /** In the panel, to {@code recipientId}: the pairing as just written, naming its next meeting's day. */
    void meetingSet(Pairing pairing, MentoringGuild guild, String setByName, String recipientId);

    /** Reminds {@code recipientId} of the pairing's next meeting, the day before it. */
    void meetingReminder(MeetingReminder reminder);

    @Builder
    record MeetingReminder(String organizationId, Pairing pairing, MentoringGuild guild, String partnerName,
            String recipientId) {
    }
}
