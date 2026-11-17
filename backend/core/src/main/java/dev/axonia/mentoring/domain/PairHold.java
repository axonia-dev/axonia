package dev.axonia.mentoring.domain;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import dev.axonia.shared.api.error.ConflictException;

/**
 * What the proposals between two people allow one of them, whichever role each took: one open proposal at a time, and
 * after a decline, no new one from its sender until the wait is over. The person who declined may propose at any time,
 * and their proposal lifts the wait, since only the newest proposal between the two counts.
 */
public enum PairHold {

    OPEN, DECLINED_RECENTLY, NONE;

    public void requireMayPropose() {

        switch (this) {
            case OPEN -> throw alreadyOpen();
            case DECLINED_RECENTLY -> throw new ConflictException("MENTORING_DECLINED_RECENTLY",
                    "A declined pair proposes again " + MentoringWindows.RETRY_AFTER_DECLINE.toDays() + " days on");
            case NONE -> {
            }
        }
    }

    /** Refused only while a proposal between the two is open: what reopening a declined one must respect. */
    public void requireNoneOpen() {

        if (this == OPEN) {
            throw alreadyOpen();
        }
    }

    private static ConflictException alreadyOpen() {

        return new ConflictException("MENTORING_PROPOSAL_OPEN", "A proposal between these two is already open");
    }

    /** The hold for {@code me}: newest means the proposal most recently sent or reopened. */
    public static PairHold of(List<Proposal> betweenTheTwo, String me, Instant now) {

        if (betweenTheTwo.stream().anyMatch(p -> MentoringWindows.isOpen(p, now))) {
            return OPEN;
        }
        return declinedRecently(betweenTheTwo, me, now).map(p -> DECLINED_RECENTLY).orElse(NONE);
    }

    /** The newest proposal between the two, when it is {@code me}'s and was declined less than the wait ago. */
    public static Optional<Proposal> declinedRecently(List<Proposal> betweenTheTwo, String me, Instant now) {

        return newest(betweenTheTwo)
                .filter(p -> MentoringWindows.isDeclinedRecently(p, now) && me.equals(p.senderId()));
    }

    /**
     * Whether the person who declined {@code declined} may still reopen it: declined less than the wait ago, still the
     * newest proposal between the two, and no pairing of the two started since the decline. A newer answer, either way,
     * or a pairing since, is the two people's latest word, and a reopening must not go over it.
     */
    public static boolean isReopenable(Proposal declined, List<Proposal> betweenTheTwo, List<Pairing> pairingsOfTheTwo,
            Instant now) {

        return MentoringWindows.isDeclinedRecently(declined, now)
                && newest(betweenTheTwo).filter(p -> p.id().equals(declined.id())).isPresent()
                && pairingsOfTheTwo.stream().allMatch(p -> p.startedAt().isBefore(declined.answeredAt()));
    }

    /** The proposal most recently sent or reopened, then the latest stored. */
    private static Optional<Proposal> newest(List<Proposal> betweenTheTwo) {

        return betweenTheTwo.stream().max(Comparator.comparing(MentoringWindows::openSince).thenComparing(Proposal::id,
                Comparator.nullsFirst(Comparator.naturalOrder())));
    }
}
