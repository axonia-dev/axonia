package dev.axonia.mentoring.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import dev.axonia.mentoring.api.Availability;
import dev.axonia.mentoring.api.DirectoryPerson;
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
import dev.axonia.mentoring.domain.MentoringCommunity;
import dev.axonia.mentoring.domain.MentoringPerson;
import dev.axonia.mentoring.domain.Pairing;
import dev.axonia.mentoring.domain.Proposal;
import dev.axonia.mentoring.domain.ProposalStatus;
import dev.axonia.mentoring.domain.Role;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.ForbiddenException;
import dev.axonia.shared.api.error.OwnSignInRequiredException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MentoringReadsTest {

    private static final String ORG = "org-1";
    private static final String SLUG = "platform";
    private static final String ME = "me";
    private static final long COMMUNITY = 7L;
    private static final Instant NOW = Instant.parse("2026-11-16T10:00:00Z");

    @Mock
    private OrganizationContext organizationContext;
    @Mock
    private CurrentUser currentUser;
    @Mock
    private CommunityAccess communityAccess;
    @Mock
    private AvailabilityRepository availabilities;
    @Mock
    private PairingRepository pairings;
    @Mock
    private ProposalRepository proposals;
    @Mock
    private MentorAreas mentorAreas;
    @Mock
    private MenteeAreas menteeAreas;
    @Mock
    private PersonDirectory personDirectory;
    @Mock
    private MemberClocks memberClocks;

    private MentoringReads reads;

    @BeforeEach
    void setUp() {

        when(organizationContext.organizationId()).thenReturn(ORG);
        when(currentUser.userId()).thenReturn(ME);
        when(communityAccess.communityForSlug(SLUG)).thenReturn(community(true, false));
        when(communityAccess.isMember(COMMUNITY, ME)).thenReturn(true);
        when(availabilities.listedIn(ORG, COMMUNITY)).thenReturn(List.of());
        when(availabilities.find(ORG, COMMUNITY, ME)).thenReturn(Optional.empty());
        when(pairings.ongoingInvolving(ORG, COMMUNITY, ME)).thenReturn(List.of());
        when(mentorAreas.declaredIn(SLUG)).thenReturn(Map.of());
        when(menteeAreas.wantedIn(SLUG)).thenReturn(Map.of());
        when(proposals.involvingIn(any(), anyLong(), any())).thenReturn(List.of());
        when(memberClocks.zoneOf(ORG, ME)).thenReturn(ZoneOffset.UTC);
        when(personDirectory.resolve(anyCollection())).thenAnswer(inv -> {
            Collection<String> ids = inv.getArgument(0);
            return ids.stream()
                    .collect(Collectors.toMap(id -> id, id -> new MentoringPerson(id, name(id), "avatar/" + id)));
        });
        reads = new MentoringReads(organizationContext, currentUser, communityAccess, availabilities, pairings,
                proposals, mentorAreas, menteeAreas, personDirectory, memberClocks, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static String name(String id) {

        return id.substring(0, 1).toUpperCase() + id.substring(1);
    }

    private static MentoringCommunity community(boolean enabled, boolean archived) {

        return new MentoringCommunity(COMMUNITY, SLUG, "Platform", archived, enabled);
    }

    private static dev.axonia.mentoring.domain.Availability offer(String user, Integer limit) {

        return dev.axonia.mentoring.domain.Availability.builder().communityId(COMMUNITY).userId(user)
                .takingMentees(true).menteeLimit(limit).build();
    }

    private static dev.axonia.mentoring.domain.Availability seek(String user) {

        return dev.axonia.mentoring.domain.Availability.builder().communityId(COMMUNITY).userId(user)
                .lookingForMentor(true).build();
    }

    private static Proposal proposal(String other, ProposalStatus status, Instant createdAt, Instant answeredAt) {

        return Proposal.builder().communityId(COMMUNITY).mentorUserId(other).menteeUserId(ME).status(status)
                .createdAt(createdAt).answeredAt(answeredAt).proposedBy(Role.MENTEE).build();
    }

    private static Pairing pairing(String mentor, String mentee) {

        return Pairing.builder().id(1L).communityId(COMMUNITY).mentorUserId(mentor).menteeUserId(mentee).build();
    }

    @Test
    void availabilityIsOffUntilSet() {

        // when
        var actual = reads.page(SLUG);

        // then
        assertThat(actual.mine()).isEqualTo(new Availability(false, null, false));
        assertThat(actual.mentors()).isEmpty();
        assertThat(actual.mentees()).isEmpty();
        assertThat(actual.enabled()).isTrue();
        assertThat(actual.member()).isTrue();
        assertThat(actual.archived()).isFalse();
    }

    @Test
    void mineIsReadFromTheListedRow() {

        // given
        when(availabilities.listedIn(ORG, COMMUNITY)).thenReturn(List.of(offer(ME, 2)));

        // when
        var actual = reads.page(SLUG);

        // then
        assertThat(actual.mine()).isEqualTo(new Availability(true, 2, false));
        assertThat(actual.mentors()).isEmpty();
    }

    @Test
    void mineIsReadEvenWhenTheRowIsNotListed() {

        // given
        when(availabilities.find(ORG, COMMUNITY, ME)).thenReturn(Optional
                .of(dev.axonia.mentoring.domain.Availability.builder().communityId(COMMUNITY).userId(ME).build()));

        // when
        var actual = reads.page(SLUG);

        // then
        assertThat(actual.mine()).isEqualTo(new Availability(false, null, false));
    }

    @Test
    void aDelegatedSessionReadsNothingOfThePage() {

        // given
        when(currentUser.delegated()).thenReturn(true);

        // when / then
        assertThatThrownBy(() -> reads.page(SLUG)).isInstanceOf(OwnSignInRequiredException.class);
        verifyNoInteractions(availabilities, pairings, proposals, personDirectory);
    }

    @Test
    void onlyMembersReadThePage() {

        // given
        when(communityAccess.isMember(COMMUNITY, ME)).thenReturn(false);

        // when / then
        assertThatThrownBy(() -> reads.page(SLUG)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void aSwitchedOffGuildRefusesThePage() {

        // given
        when(communityAccess.communityForSlug(SLUG)).thenReturn(community(false, false));

        // when / then
        assertThatThrownBy(() -> reads.page(SLUG)).isInstanceOf(ConflictException.class)
                .hasMessageContaining("switched off");
    }

    @Test
    void anArchivedGuildReadsButProposesNothing() {

        // given
        when(communityAccess.communityForSlug(SLUG)).thenReturn(community(true, true));
        when(availabilities.listedIn(ORG, COMMUNITY)).thenReturn(List.of(offer("lea", null)));

        // when
        var actual = reads.page(SLUG);

        // then
        assertThat(actual.archived()).isTrue();
        assertThat(actual.mentors()).extracting(DirectoryPerson::proposable).containsExactly(false);
    }

    @Test
    void aFullMentorIsListedAsNotTakingMentees() {

        // given
        when(availabilities.listedIn(ORG, COMMUNITY))
                .thenReturn(List.of(offer("full", 1), offer("room", 2), offer("free", null)));
        when(pairings.ongoingAsMentorIn(ORG, COMMUNITY)).thenReturn(Map.of("full", 1, "room", 1, "free", 3));

        // when
        var actual = reads.page(SLUG);

        // then
        assertThat(actual.mentors()).extracting(DirectoryPerson::userId).containsExactly("free", "room");
    }

    @Test
    void mentorsCarryTheirDeclaredAreas() {

        // given
        when(availabilities.listedIn(ORG, COMMUNITY)).thenReturn(List.of(offer("lea", null), offer("tom", null)));
        when(mentorAreas.declaredIn(SLUG)).thenReturn(Map.of("lea", List.of("kafka", "java")));

        // when
        var actual = reads.page(SLUG);

        // then
        assertThat(actual.mentors()).extracting(DirectoryPerson::areas).containsExactly(List.of("kafka", "java"),
                List.of());
    }

    @Test
    void menteesCarryTheirAreasToLearn() {

        // given
        when(availabilities.listedIn(ORG, COMMUNITY)).thenReturn(List.of(seek("ana")));
        when(menteeAreas.wantedIn(SLUG)).thenReturn(Map.of("ana", List.of("rust")));

        // when
        var actual = reads.page(SLUG);

        // then
        assertThat(actual.mentees()).singleElement().satisfies(p -> {
            assertThat(p.areas()).containsExactly("rust");
            assertThat(p.displayName()).isEqualTo("Ana");
            assertThat(p.avatarUrl()).isEqualTo("avatar/ana");
            assertThat(p.proposable()).isTrue();
            assertThat(p.proposalState()).isNull();
        });
    }

    @Test
    void listsAreOrderedByDisplayName() {

        // given
        when(availabilities.listedIn(ORG, COMMUNITY)).thenReturn(
                List.of(offer("zed", null), offer("Bob", null), offer("amy", null), seek("zed"), seek("amy")));

        // when
        var actual = reads.page(SLUG);

        // then
        assertThat(actual.mentors()).extracting(DirectoryPerson::userId).containsExactly("amy", "Bob", "zed");
        assertThat(actual.mentees()).extracting(DirectoryPerson::userId).containsExactly("amy", "zed");
    }

    @Test
    void equalNamesAreOrderedById() {

        // given
        when(personDirectory.resolve(anyCollection())).thenAnswer(inv -> {
            Collection<String> ids = inv.getArgument(0);
            return ids.stream().collect(Collectors.toMap(id -> id, id -> new MentoringPerson(id, "Same", null)));
        });
        when(availabilities.listedIn(ORG, COMMUNITY)).thenReturn(List.of(offer("b", null), offer("a", null)));

        // when
        var actual = reads.page(SLUG);

        // then
        assertThat(actual.mentors()).extracting(DirectoryPerson::userId).containsExactly("a", "b");
    }

    @Test
    void theCallerIsNotListedToThemself() {

        // given
        when(availabilities.listedIn(ORG, COMMUNITY)).thenReturn(List.of(offer(ME, null), seek("ana")));

        // when
        var actual = reads.page(SLUG);

        // then
        assertThat(actual.mentors()).isEmpty();
        assertThat(actual.mentees()).extracting(DirectoryPerson::userId).containsExactly("ana");
    }

    @Test
    void aPartnerOfAnOngoingPairingIsMarkedPaired() {

        // given
        when(availabilities.listedIn(ORG, COMMUNITY)).thenReturn(List.of(offer("lea", null), seek("ana")));
        when(pairings.ongoingInvolving(ORG, COMMUNITY, ME))
                .thenReturn(List.of(pairing("lea", ME), pairing(ME, "ana"), pairing(ME, null)));

        // when
        var actual = reads.page(SLUG);

        // then
        assertThat(actual.mentors()).singleElement().satisfies(p -> {
            assertThat(p.proposalState()).isEqualTo("PAIRED");
            assertThat(p.proposable()).isFalse();
        });
        assertThat(actual.mentees()).extracting(DirectoryPerson::proposalState).containsExactly("PAIRED");
    }

    @Test
    void anOpenProposalMarksTheMentorOpen() {

        // given
        when(availabilities.listedIn(ORG, COMMUNITY)).thenReturn(List.of(offer("lea", null)));
        when(proposals.involvingIn(ORG, COMMUNITY, ME))
                .thenReturn(List.of(proposal("lea", ProposalStatus.OPEN, NOW.minusSeconds(86400L * 29), null)));

        // when
        var actual = reads.page(SLUG);

        // then
        assertThat(actual.mentors()).singleElement().satisfies(p -> {
            assertThat(p.proposalState()).isEqualTo("PROPOSAL_SENT");
            assertThat(p.proposable()).isFalse();
        });
    }

    @Test
    void aProposalToAMenteeMarksTheMenteeOpen() {

        // given
        when(availabilities.listedIn(ORG, COMMUNITY)).thenReturn(List.of(seek("ana")));
        when(proposals.involvingIn(ORG, COMMUNITY, ME))
                .thenReturn(List.of(proposal("ana", ProposalStatus.OPEN, NOW.minusSeconds(60), null)));

        // when
        var actual = reads.page(SLUG);

        // then
        assertThat(actual.mentees()).extracting(DirectoryPerson::proposalState).containsExactly("PROPOSAL_SENT");
    }

    @Test
    void anOpenProposalFromTheOtherPersonReadsAsReceived() {

        // given
        when(availabilities.listedIn(ORG, COMMUNITY)).thenReturn(List.of(offer("lea", null)));
        var fromLea = Proposal.builder().communityId(COMMUNITY).mentorUserId("lea").menteeUserId(ME)
                .status(ProposalStatus.OPEN).createdAt(NOW.minusSeconds(60)).proposedBy(Role.MENTOR).build();
        var anOldOneToLea = proposal("lea", ProposalStatus.WITHDRAWN, NOW.minusSeconds(600), NOW.minusSeconds(300));
        when(proposals.involvingIn(ORG, COMMUNITY, ME)).thenReturn(List.of(anOldOneToLea, fromLea));

        // when
        var actual = reads.page(SLUG);

        // then
        assertThat(actual.mentors()).singleElement().satisfies(p -> {
            assertThat(p.proposalState()).isEqualTo("PROPOSAL_RECEIVED");
            assertThat(p.proposable()).isFalse();
        });
    }

    @Test
    void aLapsedOpenProposalIsProposableAgain() {

        // given
        when(availabilities.listedIn(ORG, COMMUNITY)).thenReturn(List.of(offer("lea", null)));
        when(proposals.involvingIn(ORG, COMMUNITY, ME))
                .thenReturn(List.of(proposal("lea", ProposalStatus.OPEN, NOW.minusSeconds(86400L * 30), null)));

        // when
        var actual = reads.page(SLUG);

        // then
        assertThat(actual.mentors()).singleElement().satisfies(p -> {
            assertThat(p.proposalState()).isNull();
            assertThat(p.proposable()).isTrue();
        });
    }

    @Test
    void thePageCarriesTheCallersOwnAreasForEachDoor() {

        // given
        when(mentorAreas.declaredIn(SLUG)).thenReturn(Map.of(ME, List.of("kafka", "helm"), "lea", List.of("go")));
        when(menteeAreas.wantedIn(SLUG)).thenReturn(Map.of(ME, List.of("rust"), "tom", List.of("go")));

        // when
        var actual = reads.page(SLUG);

        // then
        assertThat(actual.canMentorOn()).containsExactly("kafka", "helm");
        assertThat(actual.wantsToGrowIn()).containsExactly("rust");
    }

    @Test
    void aMemberWithNoAreasHasEmptyDoors() {

        // when
        var actual = reads.page(SLUG);

        // then
        assertThat(actual.canMentorOn()).isEmpty();
        assertThat(actual.wantsToGrowIn()).isEmpty();
    }

    @Test
    void aFirstVisitIsAGuidedStart() {

        // given
        when(pairings.involving(ORG, ME)).thenReturn(List.of(pairing("lea", ME).toBuilder().communityId(99L).build()));

        // when
        var actual = reads.page(SLUG);

        // then
        assertThat(actual.guidedStart()).isTrue();
    }

    @Test
    void aSwitchOnEndsTheGuidedStart() {

        // given
        when(availabilities.find(ORG, COMMUNITY, ME)).thenReturn(Optional.of(offer(ME, null)));
        var asMentor = reads.page(SLUG);
        when(availabilities.find(ORG, COMMUNITY, ME)).thenReturn(Optional.of(seek(ME)));
        var asMentee = reads.page(SLUG);

        // then
        assertThat(asMentor.guidedStart()).isFalse();
        assertThat(asMentee.guidedStart()).isFalse();
    }

    @Test
    void aPastProposalHereEndsTheGuidedStart() {

        // given
        when(proposals.involvingIn(ORG, COMMUNITY, ME))
                .thenReturn(List.of(proposal("lea", ProposalStatus.WITHDRAWN, NOW.minusSeconds(600), NOW)));

        // when
        var actual = reads.page(SLUG);

        // then
        assertThat(actual.guidedStart()).isFalse();
    }

    @Test
    void aPastPairingHereEndsTheGuidedStart() {

        // given
        when(pairings.involving(ORG, ME)).thenReturn(List.of(pairing("lea", ME).toBuilder().endedAt(NOW).build()));

        // when
        var actual = reads.page(SLUG);

        // then
        assertThat(actual.guidedStart()).isFalse();
    }

    @Test
    void aRecentDeclineBlocksAndAnOldOneDoesNot() {

        // given
        when(availabilities.listedIn(ORG, COMMUNITY)).thenReturn(List.of(offer("lea", null), offer("tom", null)));
        var created = NOW.minusSeconds(86400L * 90);
        when(proposals.involvingIn(ORG, COMMUNITY, ME))
                .thenReturn(List.of(proposal("lea", ProposalStatus.DECLINED, created, NOW.minusSeconds(86400L * 29)),
                        proposal("tom", ProposalStatus.DECLINED, created, NOW.minusSeconds(86400L * 30))));

        // when
        var actual = reads.page(SLUG);

        // then
        assertThat(actual.mentors()).extracting(DirectoryPerson::displayName, DirectoryPerson::proposalState)
                .containsExactly(tuple("Tom", null), tuple("Lea", "DECLINED_RECENTLY"));
    }

    @Test
    void aDeclinedPersonIsListedLastWithTheDayProposalsOpen() {

        // given
        when(memberClocks.zoneOf(ORG, ME)).thenReturn(ZoneId.of("Pacific/Kiritimati"));
        when(availabilities.listedIn(ORG, COMMUNITY))
                .thenReturn(List.of(offer("ana", null), offer("bob", null), offer("zoe", null)));
        var declinedAt = Instant.parse("2026-11-10T11:00:00Z");
        when(proposals.involvingIn(ORG, COMMUNITY, ME))
                .thenReturn(List.of(proposal("ana", ProposalStatus.DECLINED, declinedAt.minusSeconds(60), declinedAt)));

        // when
        var actual = reads.page(SLUG);

        // then
        assertThat(actual.mentors())
                .extracting(DirectoryPerson::userId, DirectoryPerson::proposable, DirectoryPerson::proposalsOpenOn)
                .containsExactly(tuple("bob", true, null), tuple("zoe", true, null),
                        tuple("ana", false, LocalDate.parse("2026-12-11")));
    }

    @Test
    void theDeclinerSeesTheSenderAsProposable() {

        // given
        when(availabilities.listedIn(ORG, COMMUNITY)).thenReturn(List.of(seek("ana")));
        var declinedAt = NOW.minusSeconds(3600);
        when(proposals.involvingIn(ORG, COMMUNITY, ME)).thenReturn(List.of(Proposal.builder().communityId(COMMUNITY)
                .mentorUserId(ME).menteeUserId("ana").proposedBy(Role.MENTEE).status(ProposalStatus.DECLINED)
                .createdAt(declinedAt.minusSeconds(60)).answeredAt(declinedAt).build()));

        // when
        var actual = reads.page(SLUG);

        // then
        assertThat(actual.mentees()).singleElement().satisfies(p -> {
            assertThat(p.proposalState()).isNull();
            assertThat(p.proposable()).isTrue();
            assertThat(p.proposalsOpenOn()).isNull();
        });
    }

    @Test
    void anAnsweredOrWithdrawnProposalDoesNotBlock() {

        // given
        when(availabilities.listedIn(ORG, COMMUNITY)).thenReturn(List.of(offer("lea", null), offer("tom", null)));
        when(proposals.involvingIn(ORG, COMMUNITY, ME))
                .thenReturn(List.of(proposal("lea", ProposalStatus.ACCEPTED, NOW.minusSeconds(60), NOW),
                        proposal("tom", ProposalStatus.WITHDRAWN, NOW.minusSeconds(60), NOW)));

        // when
        var actual = reads.page(SLUG);

        // then
        assertThat(actual.mentors()).extracting(DirectoryPerson::proposalState).containsExactly(null, null);
    }

    @Test
    void theStateWithEachPersonComesFromOneReadOfTheCallersProposalsAndIgnoresAnErasedSide() {

        // given
        when(availabilities.listedIn(ORG, COMMUNITY)).thenReturn(List.of(offer("lea", null), seek("ana")));
        var asMentor = Proposal.builder().communityId(COMMUNITY).mentorUserId(ME).menteeUserId("ana")
                .status(ProposalStatus.OPEN).createdAt(NOW.minusSeconds(60)).proposedBy(Role.MENTOR).build();
        var erased = Proposal.builder().communityId(COMMUNITY).mentorUserId(null).menteeUserId(ME)
                .status(ProposalStatus.OPEN).createdAt(NOW.minusSeconds(60)).proposedBy(Role.MENTEE).build();
        var erasedMentee = Proposal.builder().communityId(COMMUNITY).mentorUserId(ME).menteeUserId(null)
                .status(ProposalStatus.OPEN).createdAt(NOW.minusSeconds(60)).proposedBy(Role.MENTOR).build();
        when(proposals.involvingIn(ORG, COMMUNITY, ME)).thenReturn(List.of(asMentor, erased, erasedMentee));

        // when
        var actual = reads.page(SLUG);

        // then
        assertThat(actual.mentors()).extracting(DirectoryPerson::proposalState).containsExactly((String) null);
        assertThat(actual.mentees()).extracting(DirectoryPerson::proposalState).containsExactly("PROPOSAL_SENT");
        verify(proposals).involvingIn(ORG, COMMUNITY, ME);
        verifyNoMoreInteractions(proposals);
    }
}
