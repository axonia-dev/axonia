package dev.axonia.mentoring.application;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.mentoring.api.MentorFace;
import dev.axonia.mentoring.application.port.out.CommunityAccess;
import dev.axonia.mentoring.application.port.out.CurrentUser;
import dev.axonia.mentoring.application.port.out.MentorListingRepository;
import dev.axonia.mentoring.application.port.out.OrganizationContext;
import dev.axonia.mentoring.application.port.out.PairingRepository;
import dev.axonia.mentoring.application.port.out.PersonDirectory;
import dev.axonia.mentoring.domain.ListingSwitch;
import dev.axonia.mentoring.domain.MentorHistory;
import dev.axonia.mentoring.domain.MentorListing;
import dev.axonia.mentoring.domain.MentorRoster;
import dev.axonia.mentoring.domain.MentorRoster.Status;
import dev.axonia.mentoring.domain.MentoringPerson;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.ForbiddenException;
import dev.axonia.shared.api.error.OwnSignInRequiredException;
import lombok.RequiredArgsConstructor;

/**
 * The community's mentors as a member sees them: current members and present people only, never the caller, Mentoring
 * now first, then by name, then by id. The order is fixed, so a sort the client asks for is not honoured.
 */
@Service
@RequiredArgsConstructor
public class MentorRosterReads {

    static final String LISTED = "LISTED";
    static final String OFF = "OFF";
    static final String ASKED = "ASKED";

    private static final Comparator<MentorFace> ORDER = Comparator
            .comparing((MentorFace f) -> !Status.MENTORING_NOW.name().equals(f.status()))
            .thenComparing(MentorFace::displayName, String.CASE_INSENSITIVE_ORDER).thenComparing(MentorFace::userId);

    private final OrganizationContext organizationContext;
    private final CurrentUser currentUser;
    private final CommunityAccess communityAccess;
    private final PairingRepository pairings;
    private final MentorListingRepository listings;
    private final PersonDirectory personDirectory;

    /** Derives the whole community roster and slices it in memory: a community's mentors fit in one read. */
    @Transactional(readOnly = true)
    public Page<MentorFace> page(String slug, Pageable pageable) {

        if (currentUser.delegated()) {
            throw new OwnSignInRequiredException();
        }
        var me = currentUser.userId();
        var community = communityAccess.communityForSlug(slug);
        if (!communityAccess.isMember(community.id(), me)) {
            throw new ForbiddenException("Only members see the mentors");
        }
        if (!community.mentoringEnabled()) {
            throw new ConflictException("MENTORING_OFF", "Mentoring is switched off in this community");
        }
        var faces = rosterOf(organizationContext.organizationId(), community.id(), me).faces();
        var request = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        var from = (int) Math.min(request.getOffset(), faces.size());
        var to = Math.min(from + request.getPageSize(), faces.size());
        return new PageImpl<>(faces.subList(from, to), request, faces.size());
    }

    /** Every listed mentor in order, and the caller's own listing: LISTED, OFF, ASKED, or null when not eligible. */
    Roster rosterOf(String org, long communityId, String me) {

        var histories = pairings.mentorHistoriesIn(org, communityId);
        var switches = listings.in(org, communityId);
        var members = communityAccess.memberIdsOf(org, communityId);
        var statuses = new HashMap<String, Status>();
        histories.stream().filter(h -> !h.mentorId().equals(me) && members.contains(h.mentorId()))
                .forEach(h -> MentorRoster.status(h, MentorListing.switchOf(switches.get(h.mentorId())))
                        .ifPresent(status -> statuses.put(h.mentorId(), status)));
        var mine = histories.stream().filter(h -> h.mentorId().equals(me)).findFirst()
                .map(h -> callerListing(h, switches.get(me))).orElse(null);
        if (statuses.isEmpty()) {
            return new Roster(List.of(), mine);
        }
        statuses.keySet().retainAll(personDirectory.presentOf(org, statuses.keySet()));
        var people = personDirectory.resolve(statuses.keySet());
        var faces = statuses.entrySet().stream().map(e -> face(people.get(e.getKey()), e.getValue())).sorted(ORDER)
                .toList();
        return new Roster(faces, mine);
    }

    private static MentorFace face(MentoringPerson person, Status status) {

        return new MentorFace(person.userId(), person.displayName(), person.avatarUrl(), status.name());
    }

    private static String callerListing(MentorHistory history, MentorListing listing) {

        var state = MentorListing.switchOf(listing);
        if (MentorRoster.status(history, state).isPresent()) {
            return LISTED;
        }
        if (state == ListingSwitch.OFF) {
            return OFF;
        }
        var asked = listing != null && listing.askedAt() != null;
        return MentorRoster.needsAsk(history, state) && asked ? ASKED : null;
    }

    /** The listed mentors, the caller excluded, and the caller's own listing. */
    record Roster(List<MentorFace> faces, String listing) {
    }
}
