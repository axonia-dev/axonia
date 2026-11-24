package dev.axonia.mentoring.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.mentoring.api.Availability;
import dev.axonia.mentoring.api.DirectoryPerson;
import dev.axonia.mentoring.api.MentoringPage;
import dev.axonia.mentoring.api.OtherMentors;
import dev.axonia.mentoring.api.ShownPairing;
import dev.axonia.mentoring.api.ShownPairing.DirectoryFace;
import dev.axonia.mentoring.application.port.out.AvailabilityRepository;
import dev.axonia.mentoring.application.port.out.CommunityAccess;
import dev.axonia.mentoring.application.port.out.CurrentUser;
import dev.axonia.mentoring.application.port.out.MemberClocks;
import dev.axonia.mentoring.application.port.out.MenteeAreas;
import dev.axonia.mentoring.application.port.out.MentorAreas;
import dev.axonia.mentoring.application.port.out.OrganizationContext;
import dev.axonia.mentoring.application.port.out.PairingRepository;
import dev.axonia.mentoring.application.port.out.PersonDirectory;
import dev.axonia.mentoring.application.port.out.ProposalRepository;
import dev.axonia.mentoring.domain.MeetingDays;
import dev.axonia.mentoring.domain.MentoringCommunity;
import dev.axonia.mentoring.domain.MentoringPerson;
import dev.axonia.mentoring.domain.MentoringWindows;
import dev.axonia.mentoring.domain.PairHold;
import dev.axonia.mentoring.domain.Pairing;
import dev.axonia.mentoring.domain.Proposal;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.ForbiddenException;
import dev.axonia.shared.api.error.OwnSignInRequiredException;
import lombok.Builder;
import lombok.RequiredArgsConstructor;

/**
 * The guild's mentoring page: who offers to mentor and who looks for a mentor, in a neutral order, save the people the
 * caller waits on after a decline, who come last; who is helping whom, by mentor then mentee name, with no tally; and
 * the first of the community's other mentors with how many there are, and the caller's own listing among them.
 */
@Service
@RequiredArgsConstructor
public class MentoringReads {

    private static final String PROPOSAL_SENT = "PROPOSAL_SENT";
    private static final String PROPOSAL_RECEIVED = "PROPOSAL_RECEIVED";
    private static final String DECLINED_RECENTLY = "DECLINED_RECENTLY";
    private static final String PAIRED = "PAIRED";
    private static final int FACES = 5;

    private final OrganizationContext organizationContext;
    private final CurrentUser currentUser;
    private final CommunityAccess communityAccess;
    private final AvailabilityRepository availabilities;
    private final PairingRepository pairings;
    private final ProposalRepository proposals;
    private final MentorAreas mentorAreas;
    private final MenteeAreas menteeAreas;
    private final PersonDirectory personDirectory;
    private final MemberClocks memberClocks;
    private final MentorRosterReads mentorRoster;
    private final Clock clock;

    @Transactional(readOnly = true)
    public MentoringPage page(String slug) {

        if (currentUser.delegated()) {
            throw new OwnSignInRequiredException();
        }
        var org = organizationContext.organizationId();
        var me = currentUser.userId();
        var community = communityAccess.communityForSlug(slug);
        if (!communityAccess.isMember(community.id(), me)) {
            throw new ForbiddenException("Only members see the mentoring page");
        }
        if (!community.mentoringEnabled()) {
            throw new ConflictException("MENTORING_OFF", "Mentoring is switched off in this community");
        }
        var listed = availabilities.listedIn(org, community.id());
        var mine = listed.stream().filter(a -> a.userId().equals(me)).findFirst()
                .map(a -> new Availability(a.takingMentees(), a.menteeLimit(), a.lookingForMentor()))
                .orElseGet(() -> availabilities.find(org, community.id(), me)
                        .map(a -> new Availability(a.takingMentees(), a.menteeLimit(), a.lookingForMentor()))
                        .orElse(new Availability(false, null, false)));
        var others = listed.stream().filter(a -> !a.userId().equals(me)).toList();
        var ongoing = pairings.ongoingAsMentorIn(org, community.id());
        var mentorIds = others.stream().filter(a -> a.takingMentees() && hasRoom(ongoing, a)).map(a -> a.userId())
                .toList();
        var menteeIds = others.stream().filter(a -> a.lookingForMentor()).map(a -> a.userId()).toList();
        var shown = pairings.shownIn(org, community.id());
        var people = personDirectory.resolve(Stream
                .of(mentorIds.stream(), menteeIds.stream(),
                        shown.stream().flatMap(p -> Stream.of(p.mentorUserId(), p.menteeUserId())))
                .flatMap(s -> s).distinct().toList());
        var zone = memberClocks.zoneOf(org, me);
        var involved = proposals.involvingIn(org, community.id(), me);
        var context = Context.builder().community(community).me(me).zone(zone).now(clock.instant())
                .partners(Context.partnersOf(me, pairings.ongoingInvolving(org, community.id(), me)))
                .proposalsWith(Context.byOtherPerson(me, involved)).build();
        var roster = mentorRoster.rosterOf(org, community.id(), me);
        var declared = mentorAreas.declaredIn(slug);
        var wanted = menteeAreas.wantedIn(slug);
        return MentoringPage.builder().enabled(true).member(true).archived(community.archived()).mine(mine)
                .mentors(directory(new Listing(mentorIds, declared), people, context))
                .mentees(directory(new Listing(menteeIds, wanted), people, context))
                .canMentorOn(declared.getOrDefault(me, List.of())).wantsToGrowIn(wanted.getOrDefault(me, List.of()))
                .guidedStart(isFirstVisit(org, community.id(), mine, involved))
                .pairings(shownPairings(shown, people, zone))
                .otherMentors(new OtherMentors(roster.faces().stream().limit(FACES).toList(), roster.faces().size()))
                .listing(roster.listing()).build();
    }

    /** By mentor name, then mentee name, then the people's ids: a neutral order, never a tally. */
    private static List<ShownPairing> shownPairings(List<Pairing> shown, Map<String, MentoringPerson> people,
            ZoneId zone) {

        return shown.stream()
                .map(p -> new ShownPairing(face(people.get(p.mentorUserId())), face(people.get(p.menteeUserId())),
                        MeetingDays.dayOf(p.startedAt(), zone)))
                .sorted(Comparator
                        .comparing((ShownPairing p) -> p.mentor().displayName(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(p -> p.mentee().displayName(), String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(p -> p.mentor().userId()).thenComparing(p -> p.mentee().userId()))
                .toList();
    }

    private static DirectoryFace face(MentoringPerson person) {

        return new DirectoryFace(person.userId(), person.displayName(), person.avatarUrl());
    }

    /** Both switches off, and never a proposal or a pairing here, whatever became of it. */
    private boolean isFirstVisit(String org, long communityId, Availability mine, List<Proposal> involved) {

        return !mine.takingMentees() && !mine.lookingForMentor() && involved.isEmpty() && pairings
                .involving(org, currentUser.userId()).stream().noneMatch(p -> p.communityId() == communityId);
    }

    private static boolean hasRoom(Map<String, Integer> ongoing, dev.axonia.mentoring.domain.Availability a) {

        return a.menteeLimit() == null || a.hasRoom(ongoing.getOrDefault(a.userId(), 0));
    }

    /** In order of name, each person declined recently last, since the viewer cannot propose to them yet. */
    private List<DirectoryPerson> directory(Listing listing, Map<String, MentoringPerson> people, Context context) {

        return listing.ids().stream().map(people::get).map(person -> {
            var state = stateWith(context, person.userId());
            var openOn = DECLINED_RECENTLY.equals(state) ? proposalsOpenOn(context, person.userId()) : null;
            return new DirectoryPerson(person.userId(), person.displayName(), person.avatarUrl(),
                    listing.areas().getOrDefault(person.userId(), List.of()),
                    state == null && !context.community().archived(), state, openOn);
        }).sorted(Comparator.comparing((DirectoryPerson p) -> DECLINED_RECENTLY.equals(p.proposalState()))
                .thenComparing(DirectoryPerson::displayName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(DirectoryPerson::userId)).toList();
    }

    private LocalDate proposalsOpenOn(Context context, String otherId) {

        return PairHold.declinedRecently(context.proposalsWith().get(otherId), context.me(), context.now())
                .map(p -> MeetingDays.dayOf(MentoringWindows.reopenableUntil(p), context.zone())).orElseThrow();
    }

    private String stateWith(Context context, String otherId) {

        if (context.partners().contains(otherId)) {
            return PAIRED;
        }
        var withThem = context.proposalsWith().getOrDefault(otherId, List.of());
        var now = context.now();
        return switch (PairHold.of(withThem, context.me(), now)) {
            case OPEN ->
                withThem.stream().anyMatch(p -> MentoringWindows.isOpen(p, now) && otherId.equals(p.senderId()))
                        ? PROPOSAL_RECEIVED
                        : PROPOSAL_SENT;
            case DECLINED_RECENTLY -> DECLINED_RECENTLY;
            case NONE -> null;
        };
    }

    private record Listing(List<String> ids, Map<String, List<String>> areas) {
    }

    /**
     * The caller, their zone, the moment of reading, their partners here, and their proposals here grouped by the other
     * person, read once per page.
     */
    @Builder
    private record Context(MentoringCommunity community, String me, ZoneId zone, Instant now, Set<String> partners,
            Map<String, List<Proposal>> proposalsWith) {

        static Set<String> partnersOf(String me, List<Pairing> ongoing) {

            var partners = new HashSet<String>();
            ongoing.forEach(p -> partners.add(me.equals(p.mentorUserId()) ? p.menteeUserId() : p.mentorUserId()));
            partners.remove(null);
            return partners;
        }

        static Map<String, List<Proposal>> byOtherPerson(String me, List<Proposal> proposals) {

            return proposals.stream().filter(p -> p.mentorUserId() != null && p.menteeUserId() != null).collect(
                    Collectors.groupingBy(p -> me.equals(p.mentorUserId()) ? p.menteeUserId() : p.mentorUserId()));
        }
    }
}
