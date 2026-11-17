package dev.axonia.mentoring.domain;

import java.time.Duration;
import java.time.Instant;

/**
 * How long a proposal stays open, how long a declined sender waits before proposing again and the person who declined
 * may still reopen it, and how long a mentee may still write words once the pairing ended. Read, never swept.
 */
public final class MentoringWindows {

    public static final Duration PROPOSAL_LIFETIME = Duration.ofDays(30);

    public static final Duration RETRY_AFTER_DECLINE = Duration.ofDays(30);

    public static final Duration WORDS_AFTER_END = Duration.ofDays(365);

    private MentoringWindows() {
    }

    /** When the proposal last opened: its reopening, or else its sending. A proposal lapses counting from it. */
    public static Instant openSince(Proposal proposal) {

        return proposal.reopenedAt() == null ? proposal.createdAt() : proposal.reopenedAt();
    }

    /** Open and not yet lapsed. */
    public static boolean isOpen(Proposal proposal, Instant now) {

        return proposal.status() == ProposalStatus.OPEN && openSince(proposal).plus(PROPOSAL_LIFETIME).isAfter(now);
    }

    /** Still open in the store, but unanswered for longer than a proposal lives. */
    public static boolean isLapsed(Proposal proposal, Instant now) {

        return proposal.status() == ProposalStatus.OPEN && !isOpen(proposal, now);
    }

    /** Declined less than the wait ago: the sender waits, and the person who declined may still reopen it. */
    public static boolean isDeclinedRecently(Proposal proposal, Instant now) {

        return proposal.status() == ProposalStatus.DECLINED && reopenableUntil(proposal).isAfter(now);
    }

    /** The end of a declined proposal's wait, from its decline. */
    public static Instant reopenableUntil(Proposal proposal) {

        return proposal.answeredAt().plus(RETRY_AFTER_DECLINE);
    }

    /**
     * Words need both people, and new words wait for the pairing to end; from then on the mentee writes for a year.
     * Words written before the end, while words could still be written during a pairing, stay changeable while it is
     * ongoing and for the same year.
     */
    public static boolean isWordsWritable(Pairing pairing, boolean exists, Instant now) {

        return hasBothSides(pairing) && (exists || pairing.endedAt() != null) && isWithinWordsYear(pairing, now);
    }

    /** New words on a pairing that has not ended yet: they wait for the end rather than being closed. */
    public static boolean isWordsNotYet(Pairing pairing, boolean exists) {

        return hasBothSides(pairing) && !exists && pairing.endedAt() == null;
    }

    private static boolean hasBothSides(Pairing pairing) {

        return pairing.mentorUserId() != null && pairing.menteeUserId() != null;
    }

    private static boolean isWithinWordsYear(Pairing pairing, Instant now) {

        return pairing.endedAt() == null || !now.isAfter(pairing.endedAt().plus(WORDS_AFTER_END));
    }
}
