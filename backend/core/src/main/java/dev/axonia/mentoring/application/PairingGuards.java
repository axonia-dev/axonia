package dev.axonia.mentoring.application;

import org.springframework.stereotype.Component;

import dev.axonia.mentoring.application.port.out.AvailabilityRepository;
import dev.axonia.mentoring.application.port.out.PairingRepository;
import dev.axonia.mentoring.domain.Role;
import dev.axonia.shared.api.error.ConflictException;
import lombok.RequiredArgsConstructor;

/** Who may still be proposed to or paired with in a guild: listed on the other side, not paired, a mentor with room. */
@Component("mentoringPairingGuards")
@RequiredArgsConstructor
class PairingGuards {

    private final AvailabilityRepository availabilities;
    private final PairingRepository pairings;

    static ConflictException alreadyPaired() {

        return new ConflictException("MENTORING_ALREADY_PAIRED", "These two are already paired here");
    }

    /** {@code to} must be listed opposite {@code asRole}: a mentor with room, or a mentee looking for one. */
    void requireListed(String org, long communityId, String to, Role asRole) {

        var listed = availabilities.findListed(org, communityId, to)
                .filter(a -> asRole == Role.MENTOR
                        ? a.lookingForMentor()
                        : a.takingMentees() && a.hasRoom(pairings.ongoingAsMentor(org, communityId, to)))
                .isPresent();
        if (!listed) {
            throw new ConflictException("MENTORING_NOT_AVAILABLE", "This member is not listed on that side");
        }
    }

    void requireNotPaired(String org, long communityId, String me, String other) {

        if (pairings.ongoingInvolving(org, communityId, me).stream()
                .anyMatch(p -> other.equals(MentoringViews.partnerOf(p, me)))) {
            throw alreadyPaired();
        }
    }

    /** Called under the mentor's lock, so two acceptances never both take the mentor's last free slot. */
    void requireRoom(String org, long communityId, String mentorId) {

        var hasRoom = availabilities.find(org, communityId, mentorId)
                .map(a -> a.hasRoom(pairings.ongoingAsMentor(org, communityId, mentorId))).orElse(true);
        if (!hasRoom) {
            throw new ConflictException("MENTORING_MENTOR_FULL", "This mentor has no room for another mentee");
        }
    }
}
