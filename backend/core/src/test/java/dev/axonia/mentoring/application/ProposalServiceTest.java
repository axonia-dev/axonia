package dev.axonia.mentoring.application;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DuplicateKeyException;

import dev.axonia.expertise.api.CommunityAreas;
import dev.axonia.mentoring.api.PairingSummary;
import dev.axonia.mentoring.api.ProposalView;
import dev.axonia.mentoring.api.ProposeRequest;
import dev.axonia.mentoring.application.port.out.AvailabilityRepository;
import dev.axonia.mentoring.application.port.out.CommunityAccess;
import dev.axonia.mentoring.application.port.out.CurrentUser;
import dev.axonia.mentoring.application.port.out.JournalRepository;
import dev.axonia.mentoring.application.port.out.MemberClocks;
import dev.axonia.mentoring.application.port.out.MentoringNotices;
import dev.axonia.mentoring.application.port.out.OrganizationContext;
import dev.axonia.mentoring.application.port.out.PairingRepository;
import dev.axonia.mentoring.application.port.out.PersonDirectory;
import dev.axonia.mentoring.application.port.out.ProposalRepository;
import dev.axonia.mentoring.application.port.out.Vocabulary;
import dev.axonia.mentoring.domain.Availability;
import dev.axonia.mentoring.domain.MentoringCommunity;
import dev.axonia.mentoring.domain.MentoringGuild;
import dev.axonia.mentoring.domain.MentoringPerson;
import dev.axonia.mentoring.domain.MentoringWindows;
import dev.axonia.mentoring.domain.Pairing;
import dev.axonia.mentoring.domain.Proposal;
import dev.axonia.mentoring.domain.ProposalStatus;
import dev.axonia.mentoring.domain.Role;
import dev.axonia.mentoring.domain.Topic;
import dev.axonia.mentoring.domain.TopicState;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.ForbiddenException;
import dev.axonia.shared.api.error.NotFoundException;
import dev.axonia.shared.api.error.OwnSignInRequiredException;
import dev.axonia.shared.api.error.ValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ProposalServiceTest {

    private static final String ORG = "org-1";
    private static final String SLUG = "platform";
    private static final long COMMUNITY = 7L;
    private static final String ME = "max";
    private static final String MENTOR = "mia";
    private static final String MENTEE = "noah";
    private static final Instant NOW = Instant.parse("2026-11-16T10:00:00.123456Z");
    private static final Instant TODAY = Instant.parse("2026-11-16T10:00:00.123Z");
    private static final long PROPOSAL = 40L;
    private static final long PAIRING = 90L;

    @Mock
    private OrganizationContext organizationContext;
    @Mock
    private CurrentUser currentUser;
    @Mock
    private CommunityAccess communityAccess;
    @Mock
    private AvailabilityRepository availabilities;
    @Mock
    private ProposalRepository proposals;
    @Mock
    private PairingRepository pairings;
    @Mock
    private JournalRepository journal;
    @Mock
    private Vocabulary vocabulary;
    @Mock
    private PersonDirectory personDirectory;
    @Mock
    private MentoringNotices notices;
    @Mock
    private MemberClocks memberClocks;

    private ProposalService service;

    @BeforeEach
    void setUp() {

        when(organizationContext.organizationId()).thenReturn(ORG);
        when(currentUser.userId()).thenReturn(ME);
        when(communityAccess.communityForSlug(SLUG)).thenReturn(community(true, false));
        when(communityAccess.communityById(COMMUNITY)).thenReturn(community(true, false));
        when(communityAccess.isMember(eq(COMMUNITY), anyString())).thenReturn(true);
        when(availabilities.find(any(), anyLong(), any())).thenReturn(Optional.empty());
        when(availabilities.find(ORG, COMMUNITY, MENTOR)).thenReturn(Optional.of(mentor(MENTOR, null)));
        when(availabilities.find(ORG, COMMUNITY, MENTEE)).thenReturn(Optional.of(mentee(MENTEE)));
        when(personDirectory.isPresent(eq(ORG), anyString())).thenReturn(true);
        when(pairings.ongoingInvolving(any(), any(), any())).thenReturn(List.of());
        when(proposals.between(any(), anyLong(), any(), any())).thenReturn(List.of());
        when(availabilities.findListed(any(), anyLong(), any()))
                .thenAnswer(inv -> availabilities.find(inv.getArgument(0), inv.getArgument(1), inv.getArgument(2)));
        when(proposals.insert(eq(ORG), any())).thenReturn(PROPOSAL);
        when(proposals.answer(eq(ORG), anyLong(), any(), any())).thenReturn(true);
        when(pairings.insert(eq(ORG), any())).thenReturn(PAIRING);
        when(vocabulary.areasOf(COMMUNITY)).thenReturn(new CommunityAreas(Map.of()));
        when(vocabulary.areasIn(SLUG)).thenReturn(Set.of());
        when(vocabulary.normalized(anyString())).thenAnswer(inv -> inv.<String>getArgument(0).toLowerCase());
        when(personDirectory.resolve(anyCollection())).thenAnswer(inv -> {
            Collection<String> ids = inv.getArgument(0);
            return ids.stream()
                    .collect(Collectors.toMap(id -> id, id -> new MentoringPerson(id, name(id), null, null)));
        });
        when(memberClocks.zoneOf(anyString(), anyString())).thenReturn(ZoneOffset.UTC);
        service = service(NOW);
    }

    private ProposalService service(Instant now) {

        return new ProposalService(organizationContext, currentUser, communityAccess, proposals, pairings,
                new PairingGuards(availabilities, pairings), new FirstTopics(vocabulary, journal), personDirectory,
                notices, memberClocks, Clock.fixed(now, ZoneOffset.UTC));
    }

    private static String name(String id) {

        return id.substring(0, 1).toUpperCase() + id.substring(1);
    }

    private static MentoringCommunity community(boolean enabled, boolean archived) {

        return new MentoringCommunity(COMMUNITY, SLUG, "Platform", archived, enabled);
    }

    private static Availability mentor(String user, Integer limit) {

        return Availability.builder().communityId(COMMUNITY).userId(user).takingMentees(true).menteeLimit(limit)
                .build();
    }

    private static Availability mentee(String user) {

        return Availability.builder().communityId(COMMUNITY).userId(user).lookingForMentor(true).build();
    }

    private static ProposeRequest request(String to, Role asRole) {

        return new ProposeRequest(to, asRole.name(), List.of("Kafka", "Giving feedback"), "Hello");
    }

    private static Proposal.ProposalBuilder proposal(String mentor, String mentee, Role proposedBy) {

        return Proposal.builder().id(PROPOSAL).communityId(COMMUNITY).mentorUserId(mentor).menteeUserId(mentee)
                .proposedBy(proposedBy).topics(List.of("Kafka", "Giving feedback")).message("Hello")
                .status(ProposalStatus.OPEN).createdAt(NOW.minus(Duration.ofDays(2)));
    }

    /** {@code ME} receives a proposal from {@code MENTEE}, as their mentor. */
    private void receive(Proposal proposal) {

        when(proposals.find(ORG, proposal.id())).thenReturn(Optional.of(proposal));
    }

    @Test
    void aMenteeProposesToAMentorTakingMentees() {

        // when
        var actual = service.propose(SLUG, request(MENTOR, Role.MENTEE));

        // then
        var stored = ArgumentCaptor.forClass(Proposal.class);
        verify(proposals).insert(eq(ORG), stored.capture());
        var expected = Proposal.builder().communityId(COMMUNITY).mentorUserId(MENTOR).menteeUserId(ME)
                .proposedBy(Role.MENTEE).topics(List.of("Kafka", "Giving feedback")).message("Hello")
                .status(ProposalStatus.OPEN).createdAt(TODAY);
        assertThat(stored.getValue()).isEqualTo(expected.build());
        verify(notices, times(1)).proposed(ORG, expected.id(PROPOSAL).build(),
                new MentoringGuild("platform", "Platform"), "Max");
        assertThat(actual).isEqualTo(ProposalView.builder().id(PROPOSAL).communitySlug(SLUG).communityName("Platform")
                .proposedBy("MENTEE").partnerId(MENTOR).partnerName("Mia").topics(List.of("Kafka", "Giving feedback"))
                .message("Hello").status("OPEN").createdAt(TODAY).lapsesOn(LocalDate.parse("2026-12-16")).build());
    }

    @Test
    void theLapseDayIsReadInTheProposersOwnZone() {

        // given
        when(memberClocks.zoneOf(ORG, ME)).thenReturn(ZoneId.of("Pacific/Kiritimati"));

        // when
        var actual = service.propose(SLUG, request(MENTOR, Role.MENTEE));

        // then
        assertThat(actual.lapsesOn()).isEqualTo(LocalDate.parse("2026-12-17"));
    }

    @Test
    void aMentorProposesToAMenteeLookingForOne() {

        // when
        var actual = service.propose(SLUG, request(MENTEE, Role.MENTOR));

        // then
        var stored = ArgumentCaptor.forClass(Proposal.class);
        verify(notices, times(1)).proposed(eq(ORG), stored.capture(), eq(new MentoringGuild("platform", "Platform")),
                eq("Max"));
        assertThat(stored.getValue()).satisfies(p -> {
            assertThat(p.id()).isEqualTo(PROPOSAL);
            assertThat(p.mentorUserId()).isEqualTo(ME);
            assertThat(p.menteeUserId()).isEqualTo(MENTEE);
            assertThat(p.receiverId()).isEqualTo(MENTEE);
            assertThat(p.status()).isEqualTo(ProposalStatus.OPEN);
        });
        assertThat(actual.proposedBy()).isEqualTo("MENTOR");
        assertThat(actual.partnerId()).isEqualTo(MENTEE);
    }

    @Test
    void proposingToSomeoneNotListedIsRefused() {

        // given
        when(availabilities.find(ORG, COMMUNITY, "zoe")).thenReturn(Optional.of(mentee("zoe")));
        when(communityAccess.isMember(COMMUNITY, "gone")).thenReturn(false);

        // when / then
        assertRefused(() -> service.propose(SLUG, request("zoe", Role.MENTEE)), "MENTORING_NOT_AVAILABLE");
        assertRefused(() -> service.propose(SLUG, request(MENTOR, Role.MENTOR)), "MENTORING_NOT_AVAILABLE");
        assertRefused(() -> service.propose(SLUG, request("nobody", Role.MENTEE)), "MENTORING_NOT_AVAILABLE");
        assertRefused(() -> service.propose(SLUG, request("gone", Role.MENTOR)), "MENTORING_NOT_AVAILABLE");
        verify(proposals, never()).insert(any(), any());
    }

    @Test
    void aMentorAtTheirLimitIsNotAvailable() {

        // given
        when(availabilities.find(ORG, COMMUNITY, MENTOR)).thenReturn(Optional.of(mentor(MENTOR, 2)));
        when(pairings.ongoingAsMentor(ORG, COMMUNITY, MENTOR)).thenReturn(2);

        // when / then
        assertRefused(() -> service.propose(SLUG, request(MENTOR, Role.MENTEE)), "MENTORING_NOT_AVAILABLE");
    }

    @Test
    void proposingToOneselfIsRefused() {

        // when / then
        assertThatThrownBy(() -> service.propose(SLUG, request(ME, Role.MENTEE))).isInstanceOfSatisfying(
                ValidationException.class, e -> assertThat(e.getCode()).isEqualTo("MENTORING_SELF"));
    }

    @Test
    void anUnknownRoleIsRefused() {

        // when / then
        assertThatThrownBy(() -> service.propose(SLUG, new ProposeRequest(MENTOR, "BOSS", List.of("Kafka"), "Hi")))
                .isInstanceOfSatisfying(ValidationException.class,
                        e -> assertThat(e.getCode()).isEqualTo("field_invalid"));
        assertThatThrownBy(() -> service.propose(SLUG, new ProposeRequest(MENTOR, null, List.of("Kafka"), "Hi")))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void aNonMemberCannotPropose() {

        // given
        when(communityAccess.isMember(COMMUNITY, ME)).thenReturn(false);

        // when / then
        assertThatThrownBy(() -> service.propose(SLUG, request(MENTOR, Role.MENTEE)))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void aSecondOpenProposalToTheSamePersonIsRefused() {

        // given
        when(proposals.between(ORG, COMMUNITY, ME, MENTOR))
                .thenReturn(List.of(proposal(MENTOR, ME, Role.MENTEE).build()));

        // when / then
        assertRefused(() -> service.propose(SLUG, request(MENTOR, Role.MENTEE)), "MENTORING_PROPOSAL_OPEN");
        verify(proposals, never()).insert(any(), any());
    }

    @Test
    void aSecondOpenProposalInTheOtherRoleIsRefused() {

        // given
        when(availabilities.find(ORG, COMMUNITY, MENTOR)).thenReturn(Optional.of(Availability.builder()
                .communityId(COMMUNITY).userId(MENTOR).takingMentees(true).lookingForMentor(true).build()));
        when(proposals.between(ORG, COMMUNITY, ME, MENTOR))
                .thenReturn(List.of(proposal(MENTOR, ME, Role.MENTEE).build()));

        // when / then
        assertRefused(() -> service.propose(SLUG, request(MENTOR, Role.MENTOR)), "MENTORING_PROPOSAL_OPEN");
        verify(proposals, never()).insert(any(), any());
    }

    @Test
    void anOpenProposalFromTheOtherPersonAlsoHoldsTheSecond() {

        // given
        when(proposals.between(ORG, COMMUNITY, ME, MENTOR))
                .thenReturn(List.of(proposal(ME, MENTOR, Role.MENTOR).build()));

        // when / then
        assertRefused(() -> service.propose(SLUG, request(MENTOR, Role.MENTEE)), "MENTORING_PROPOSAL_OPEN");
    }

    @Test
    void aDeclineHoldsWhicheverRoleTheSenderTakes() {

        // given
        var declinedAt = Instant.parse("2026-10-01T09:00:00Z");
        when(availabilities.find(ORG, COMMUNITY, MENTOR)).thenReturn(Optional.of(Availability.builder()
                .communityId(COMMUNITY).userId(MENTOR).takingMentees(true).lookingForMentor(true).build()));
        when(proposals.between(ORG, COMMUNITY, ME, MENTOR)).thenReturn(List
                .of(proposal(MENTOR, ME, Role.MENTEE).status(ProposalStatus.DECLINED).answeredAt(declinedAt).build()));

        // when / then
        assertRefused(() -> service(declinedAt.plus(Duration.ofDays(29))).propose(SLUG, request(MENTOR, Role.MENTOR)),
                "MENTORING_DECLINED_RECENTLY");
        assertThat(service(declinedAt.plus(Duration.ofDays(30))).propose(SLUG, request(MENTOR, Role.MENTOR)).status())
                .isEqualTo("OPEN");
    }

    @Test
    void theDeclinerMayProposeToTheSenderAtAnyTime() {

        // given
        var declinedAt = Instant.parse("2026-10-01T09:00:00Z");
        when(proposals.between(ORG, COMMUNITY, ME, MENTOR)).thenReturn(List
                .of(proposal(MENTOR, ME, Role.MENTOR).status(ProposalStatus.DECLINED).answeredAt(declinedAt).build()));

        // when
        var actual = service(declinedAt.plus(Duration.ofDays(1))).propose(SLUG, request(MENTOR, Role.MENTEE));

        // then
        assertThat(actual.status()).isEqualTo("OPEN");
    }

    @Test
    void aProposalNamingNobodyIsInvalid() {

        // when / then
        assertThatThrownBy(() -> service.propose(SLUG, new ProposeRequest(null, "MENTOR", List.of("Kafka"), "Hi")))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.propose(SLUG, new ProposeRequest(" ", "MENTOR", List.of("Kafka"), "Hi")))
                .isInstanceOf(ValidationException.class);
        verify(proposals, never()).insert(any(), any());
    }

    @Test
    void anOpenProposalOlderThan30DaysReadsAsLapsedAndAllowsANewOne() {

        // given
        when(proposals.between(ORG, COMMUNITY, ME, MENTOR)).thenReturn(
                List.of(proposal(MENTOR, ME, Role.MENTEE).createdAt(TODAY.minus(Duration.ofDays(30))).build()));

        // when
        var actual = service.propose(SLUG, request(MENTOR, Role.MENTEE));

        // then
        assertThat(actual.status()).isEqualTo("OPEN");
        verify(proposals).insert(eq(ORG), any());
    }

    @Test
    void aDeclinedPairMayProposeAgainAfter30Days() {

        // given
        var declinedAt = Instant.parse("2026-10-01T09:00:00Z");
        when(proposals.between(ORG, COMMUNITY, ME, MENTOR)).thenReturn(List
                .of(proposal(MENTOR, ME, Role.MENTEE).status(ProposalStatus.DECLINED).answeredAt(declinedAt).build()));

        // when / then
        assertRefused(() -> service(declinedAt.plus(Duration.ofDays(29))).propose(SLUG, request(MENTOR, Role.MENTEE)),
                "MENTORING_DECLINED_RECENTLY");
        assertThat(service(declinedAt.plus(Duration.ofDays(30))).propose(SLUG, request(MENTOR, Role.MENTEE)).status())
                .isEqualTo("OPEN");
    }

    @Test
    void aWithdrawnProposalDoesNotStandInTheWay() {

        // given
        when(proposals.between(ORG, COMMUNITY, ME, MENTOR)).thenReturn(
                List.of(proposal(MENTOR, ME, Role.MENTEE).status(ProposalStatus.WITHDRAWN).answeredAt(NOW).build()));

        // when
        var actual = service.propose(SLUG, request(MENTOR, Role.MENTEE));

        // then
        assertThat(actual.status()).isEqualTo("OPEN");
    }

    @Test
    void proposingToSomeoneAlreadyPairedWithIsRefused() {

        // given
        when(pairings.ongoingInvolving(ORG, COMMUNITY, ME))
                .thenReturn(List.of(pairing(MENTOR, null), pairing(MENTOR, ME)));

        // when / then
        assertRefused(() -> service.propose(SLUG, request(MENTOR, Role.MENTEE)), "MENTORING_ALREADY_PAIRED");
    }

    @Test
    void aMentorProposingToTheirOwnMenteeIsRefused() {

        // given
        when(pairings.ongoingInvolving(ORG, COMMUNITY, ME)).thenReturn(List.of(pairing(ME, MENTEE)));

        // when / then
        assertRefused(() -> service.propose(SLUG, request(MENTEE, Role.MENTOR)), "MENTORING_ALREADY_PAIRED");
    }

    @Test
    void proposalNeedsOneToFiveTopicsAndAShortMessage() {

        // when / then
        assertInvalid(new ProposeRequest(MENTOR, "MENTEE", List.of(), "Hi"), "field_invalid");
        assertInvalid(new ProposeRequest(MENTOR, "MENTEE", null, "Hi"), "field_invalid");
        assertInvalid(new ProposeRequest(MENTOR, "MENTEE", List.of("a", "b", "c", "d", "e", "f"), "Hi"),
                "field_invalid");
        assertInvalid(new ProposeRequest(MENTOR, "MENTEE", List.of("Kafka", " Kafka "), "Hi"), "field_invalid");
        assertInvalid(new ProposeRequest(MENTOR, "MENTEE", List.of("  "), "Hi"), "field_invalid");
        assertInvalid(new ProposeRequest(MENTOR, "MENTEE", Collections.singletonList(null), "Hi"), "field_invalid");
        assertInvalid(new ProposeRequest(MENTOR, "MENTEE", List.of("x".repeat(81)), "Hi"), "MENTORING_TOO_LONG");
        assertInvalid(new ProposeRequest(MENTOR, "MENTEE", List.of("Kafka"), " "), "field_invalid");
        assertInvalid(new ProposeRequest(MENTOR, "MENTEE", List.of("Kafka"), null), "field_invalid");
        assertInvalid(new ProposeRequest(MENTOR, "MENTEE", List.of("Kafka"), "x".repeat(501)), "MENTORING_TOO_LONG");
        verify(proposals, never()).insert(any(), any());
    }

    @Test
    void aProposalAtTheLimitsIsTrimmedAndKept() {

        // when
        var actual = service.propose(SLUG, new ProposeRequest(MENTOR, "MENTEE",
                List.of(" " + "x".repeat(80) + " ", "b", "c", "d", "e"), " " + "y".repeat(500) + " "));

        // then
        assertThat(actual.topics()).containsExactly("x".repeat(80), "b", "c", "d", "e");
        assertThat(actual.message()).isEqualTo("y".repeat(500));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void aSwitchedOffOrArchivedGuildRefusesProposalsAndAcceptance(boolean switchedOff) {

        // given
        var code = switchedOff ? "MENTORING_OFF" : "COMMUNITY_ARCHIVED";
        var guild = switchedOff ? community(false, false) : community(true, true);
        when(communityAccess.communityForSlug(SLUG)).thenReturn(guild);
        when(communityAccess.communityById(COMMUNITY)).thenReturn(guild);
        receive(proposal(ME, MENTEE, Role.MENTEE).build());

        // when / then
        assertRefused(() -> service.propose(SLUG, request(MENTOR, Role.MENTEE)), code);
        assertRefused(() -> service.accept(PROPOSAL), code);
        verify(proposals, never()).insert(any(), any());
        verify(pairings, never()).insert(any(), any());
    }

    @Test
    void acceptingCreatesAPairingDatedTodayWithTheProposalsTopics() {

        // given
        receive(proposal(ME, MENTEE, Role.MENTEE).topics(List.of("Giving feedback", "Kafka", "Event Sourcing"))
                .build());
        when(vocabulary.areasOf(COMMUNITY)).thenReturn(new CommunityAreas(Map.of("event sourcing", "es")));
        when(vocabulary.areasIn(SLUG)).thenReturn(Set.of("kafka", "es"));

        // when
        var actual = service.accept(PROPOSAL);

        // then
        assertThat(actual).isEqualTo(PairingSummary.builder().id(PAIRING).communitySlug(SLUG).communityName("Platform")
                .myRole("MENTOR").partnerId(MENTEE).partnerName("Noah").startedAt(TODAY)
                .stops(List.of("TODO", "TODO", "TODO")).build());
        verify(pairings).insert(ORG, Pairing.builder().communityId(COMMUNITY).mentorUserId(ME).menteeUserId(MENTEE)
                .startedAt(TODAY).build());
        verify(proposals).answer(ORG, PROPOSAL, ProposalStatus.ACCEPTED, TODAY);
        var topics = ArgumentCaptor.forClass(Topic.class);
        verify(journal, times(3)).insertTopic(eq(ORG), topics.capture());
        assertThat(topics.getAllValues()).containsExactly(topic("Giving feedback", null, 0), topic("Kafka", "kafka", 1),
                topic("Event Sourcing", "es", 2));
        verify(vocabulary).shareLock(ORG, COMMUNITY);
    }

    @Test
    void acceptAnswersTheFullSummary() {

        // given
        receive(proposal(ME, MENTEE, Role.MENTEE).topics(List.of("Kafka", "Giving feedback")).build());

        // when
        var actual = service.accept(PROPOSAL);

        // then
        assertThat(actual.stops()).containsExactly("TODO", "TODO");
        assertThat(actual.nowTopic()).isNull();
        assertThat(actual.nextMeeting()).isNull();
        assertThat(actual.meetings()).isZero();
        assertThat(actual.notes()).isZero();
        assertThat(actual.lastMetOn()).isNull();
    }

    @Test
    void acceptingSeedsTheProposalsTopicsAsToDo() {

        // given
        receive(proposal(ME, MENTEE, Role.MENTEE).topics(List.of("Kafka", "Helm")).build());

        // when
        service.accept(PROPOSAL);

        // then
        var topics = ArgumentCaptor.forClass(Topic.class);
        verify(journal, times(2)).insertTopic(eq(ORG), topics.capture());
        assertThat(topics.getAllValues()).extracting(Topic::state, Topic::position)
                .containsExactly(tuple(TopicState.TODO, 0), tuple(TopicState.TODO, 1));
    }

    private static Topic topic(String label, String area, int position) {

        return Topic.builder().pairingId(PAIRING).label(label).area(area).state(TopicState.TODO).position(position)
                .createdAt(TODAY).build();
    }

    private static Pairing pairing(String mentor, String mentee) {

        return Pairing.builder().id(PAIRING).communityId(COMMUNITY).mentorUserId(mentor).menteeUserId(mentee)
                .startedAt(TODAY).build();
    }

    @Test
    void theMenteeAcceptsAMentorsProposal() {

        // given
        receive(proposal(MENTOR, ME, Role.MENTOR).build());

        // when
        var actual = service.accept(PROPOSAL);

        // then
        assertThat(actual.myRole()).isEqualTo("MENTEE");
        assertThat(actual.partnerId()).isEqualTo(MENTOR);
    }

    @Test
    void acceptingPastTheLimitIsRefused() {

        // given
        receive(proposal(ME, MENTEE, Role.MENTEE).build());
        when(availabilities.find(ORG, COMMUNITY, ME)).thenReturn(Optional.of(mentor(ME, 2)));
        when(pairings.ongoingAsMentor(ORG, COMMUNITY, ME)).thenReturn(2);

        // when / then
        assertRefused(() -> service.accept(PROPOSAL), "MENTORING_MENTOR_FULL");
        verify(proposals, never()).answer(any(), anyLong(), any(), any());
        verify(pairings, never()).insert(any(), any());
        var order = inOrder(pairings);
        order.verify(pairings).lockMember(ORG, COMMUNITY, ME);
        order.verify(pairings).ongoingAsMentor(ORG, COMMUNITY, ME);
    }

    @Test
    void acceptingWithRoomLeftSucceedsAndBothSidesAreLockedInIdOrder() {

        // given
        receive(proposal(ME, MENTEE, Role.MENTEE).build());
        when(availabilities.find(ORG, COMMUNITY, ME)).thenReturn(Optional.of(mentor(ME, 2)));
        when(pairings.ongoingAsMentor(ORG, COMMUNITY, ME)).thenReturn(1);

        // when
        service.accept(PROPOSAL);

        // then
        var order = inOrder(vocabulary, pairings);
        order.verify(vocabulary).shareLock(ORG, COMMUNITY);
        order.verify(pairings).lockMember(ORG, COMMUNITY, ME);
        order.verify(pairings).lockMember(ORG, COMMUNITY, MENTEE);
        order.verify(pairings).ongoingAsMentor(ORG, COMMUNITY, ME);
        order.verify(pairings).insert(eq(ORG), any());
        order.verify(vocabulary).areasOf(COMMUNITY);
    }

    @Test
    void aReceiverDeactivatedSinceTheProposalCannotAcceptIt() {

        // given
        receive(proposal(ME, MENTEE, Role.MENTEE).build());
        when(personDirectory.isPresent(ORG, ME)).thenReturn(false);

        // when / then
        assertRefused(() -> service.accept(PROPOSAL), "MENTORING_PROPOSAL_CLOSED");
        verify(pairings, never()).insert(any(), any());
    }

    @Test
    void proposingLocksBothPeopleInIdOrderBeforeReadingWhatHoldsBetweenThem() {

        // when
        service.propose(SLUG, request(MENTOR, Role.MENTEE));

        // then
        var order = inOrder(pairings, availabilities, proposals);
        order.verify(pairings).lockMember(ORG, COMMUNITY, ME);
        order.verify(pairings).lockMember(ORG, COMMUNITY, MENTOR);
        order.verify(availabilities).findListed(ORG, COMMUNITY, MENTOR);
        order.verify(proposals).between(ORG, COMMUNITY, ME, MENTOR);
        order.verify(proposals).insert(eq(ORG), any());
    }

    @Test
    void crossedProposalsYieldOnePairing() {

        // given: Noah proposed to Max, Max to Noah; Max accepts Noah's first
        var noahsProposal = proposal(ME, MENTEE, Role.MENTEE).build();
        var maxsProposal = proposal(ME, MENTEE, Role.MENTOR).id(41L).build();
        receive(noahsProposal);
        service.accept(PROPOSAL);
        verify(proposals).withdrawOthersBetween(ORG, noahsProposal, TODAY);
        when(currentUser.userId()).thenReturn(MENTEE);
        when(proposals.find(ORG, 41L)).thenReturn(Optional.of(maxsProposal));
        when(pairings.insert(eq(ORG), any())).thenThrow(new DuplicateKeyException("uq_mentoring_pairing_ongoing"));

        // when / then
        assertRefused(() -> service.accept(41L), "MENTORING_ALREADY_PAIRED");
    }

    @Test
    void acceptingWhileAlreadyPairedIsRefusedBeforeTheIndex() {

        // given
        receive(proposal(ME, MENTEE, Role.MENTEE).build());
        when(pairings.ongoingInvolving(ORG, COMMUNITY, ME)).thenReturn(List.of(pairing(MENTEE, ME)));

        // when / then
        assertRefused(() -> service.accept(PROPOSAL), "MENTORING_ALREADY_PAIRED");
        verify(pairings, never()).insert(any(), any());
    }

    @Test
    void onlyTheReceiverAnswersAndOnlyTheSenderWithdraws() {

        // given: Noah sent it to Max
        receive(proposal(ME, MENTEE, Role.MENTEE).build());
        when(proposals.find(ORG, 404L)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> service.withdraw(PROPOSAL)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.withdraw(404L)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.accept(404L)).isInstanceOf(NotFoundException.class);
        when(currentUser.userId()).thenReturn(MENTEE);
        assertThatThrownBy(() -> service.accept(PROPOSAL)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.decline(PROPOSAL)).isInstanceOf(NotFoundException.class);
        when(currentUser.userId()).thenReturn("stranger");
        assertThatThrownBy(() -> service.decline(PROPOSAL)).isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> service.withdraw(PROPOSAL)).isInstanceOf(NotFoundException.class);
        verify(proposals, never()).answer(any(), anyLong(), any(), any());
    }

    @Test
    void theSenderWithdraws() {

        // given
        receive(proposal(MENTOR, ME, Role.MENTEE).build());

        // when
        service.withdraw(PROPOSAL);

        // then
        verify(proposals).answer(ORG, PROPOSAL, ProposalStatus.WITHDRAWN, TODAY);
        verifyNoInteractions(notices);
    }

    @Test
    void aProposalThatVanishesWhileWaitingForTheLockIsMissing() {

        // given
        when(proposals.find(ORG, PROPOSAL)).thenReturn(Optional.of(proposal(ME, MENTEE, Role.MENTEE).build()),
                Optional.empty());

        // when / then
        assertThatThrownBy(() -> service.accept(PROPOSAL)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void aDeclineThatVanishesWhileWaitingForTheLockIsMissing() {

        // given
        var declined = declinedDaysAgo(3);
        when(proposals.find(ORG, PROPOSAL)).thenReturn(Optional.of(declined), Optional.empty());

        // when / then
        assertThatThrownBy(() -> service.reopen(PROPOSAL)).isInstanceOf(NotFoundException.class);
        verify(proposals, never()).reopen(any(), anyLong(), any());
    }

    @Test
    void answeringAClosedOrLapsedProposalIsRefused() {

        // given
        var lapsed = proposal(ME, MENTEE, Role.MENTEE).createdAt(NOW.minus(Duration.ofDays(31))).build();
        receive(lapsed);

        // when / then
        assertRefused(() -> service.accept(PROPOSAL), "MENTORING_PROPOSAL_CLOSED");
        assertRefused(() -> service.decline(PROPOSAL), "MENTORING_PROPOSAL_CLOSED");
        receive(proposal(ME, MENTEE, Role.MENTEE).status(ProposalStatus.WITHDRAWN).build());
        assertRefused(() -> service.accept(PROPOSAL), "MENTORING_PROPOSAL_CLOSED");
        assertRefused(() -> service.decline(PROPOSAL), "MENTORING_PROPOSAL_CLOSED");
        verify(proposals, never()).answer(any(), anyLong(), any(), any());
    }

    @Test
    void aProposalAnsweredByAnotherRequestFirstIsClosed() {

        // given
        receive(proposal(ME, MENTEE, Role.MENTEE).build());
        when(proposals.answer(eq(ORG), eq(PROPOSAL), any(), any())).thenReturn(false);

        // when / then
        assertRefused(() -> service.accept(PROPOSAL), "MENTORING_PROPOSAL_CLOSED");
        assertRefused(() -> service.decline(PROPOSAL), "MENTORING_PROPOSAL_CLOSED");
        verify(pairings, never()).insert(any(), any());
    }

    @Test
    void aProposalWithASideThatLeftTheGuildIsClosed() {

        // given
        receive(proposal(ME, MENTEE, Role.MENTEE).build());
        when(communityAccess.isMember(COMMUNITY, MENTEE)).thenReturn(false);

        // when / then
        assertRefused(() -> service.accept(PROPOSAL), "MENTORING_PROPOSAL_CLOSED");
        when(communityAccess.isMember(COMMUNITY, MENTEE)).thenReturn(true);
        when(communityAccess.isMember(COMMUNITY, ME)).thenReturn(false);
        assertRefused(() -> service.accept(PROPOSAL), "MENTORING_PROPOSAL_CLOSED");
    }

    @Test
    void aProposalFromADeactivatedSenderIsClosed() {

        // given
        receive(proposal(ME, MENTEE, Role.MENTEE).build());
        when(personDirectory.isPresent(ORG, MENTEE)).thenReturn(false);

        // when / then
        assertRefused(() -> service.accept(PROPOSAL), "MENTORING_PROPOSAL_CLOSED");
        verify(pairings, never()).insert(any(), any());
    }

    @Test
    void aDeclineTellsTheSenderNotThisTimeAndNothingMore() {

        // given
        var proposal = proposal(ME, MENTEE, Role.MENTEE).build();
        receive(proposal);
        when(communityAccess.guildOf(ORG, COMMUNITY)).thenReturn(new MentoringGuild("platform", "Platform"));
        when(personDirectory.displayName(ORG, ME)).thenReturn("Max");

        // when
        service.decline(PROPOSAL);

        // then
        verify(proposals).answer(ORG, PROPOSAL, ProposalStatus.DECLINED, TODAY);
        verify(notices).declined(proposal.toBuilder().status(ProposalStatus.DECLINED).answeredAt(TODAY).build(),
                new MentoringGuild("platform", "Platform"), "Max");
        verifyNoMoreInteractions(notices);
    }

    @Test
    void aRefusedDeclineTellsNobody() {

        // given
        receive(proposal(ME, MENTEE, Role.MENTEE).build());
        when(proposals.answer(ORG, PROPOSAL, ProposalStatus.DECLINED, TODAY)).thenReturn(false);

        // when / then
        assertRefused(() -> service.decline(PROPOSAL), "MENTORING_PROPOSAL_CLOSED");
        verifyNoInteractions(notices);
    }

    @Test
    void aDelegatedSessionWritesNothing() {

        // given
        when(currentUser.delegated()).thenReturn(true);

        // when / then
        assertThatThrownBy(() -> service.propose(SLUG, request(MENTOR, Role.MENTEE)))
                .isInstanceOf(OwnSignInRequiredException.class);
        assertThatThrownBy(() -> service.accept(PROPOSAL)).isInstanceOf(OwnSignInRequiredException.class);
        assertThatThrownBy(() -> service.decline(PROPOSAL)).isInstanceOf(OwnSignInRequiredException.class);
        assertThatThrownBy(() -> service.withdraw(PROPOSAL)).isInstanceOf(OwnSignInRequiredException.class);
        assertThatThrownBy(() -> service.reopen(PROPOSAL)).isInstanceOf(OwnSignInRequiredException.class);
        verifyNoInteractions(proposals, pairings, notices);
    }

    /** {@code ME} declined {@code MENTOR}'s proposal to mentor them, {@code days} ago. */
    private Proposal declinedDaysAgo(long days) {

        var declined = proposal(MENTOR, ME, Role.MENTOR).status(ProposalStatus.DECLINED)
                .answeredAt(TODAY.minus(Duration.ofDays(days))).build();
        receive(declined);
        when(proposals.between(ORG, COMMUNITY, ME, MENTOR)).thenReturn(List.of(declined));
        when(proposals.reopen(ORG, PROPOSAL, TODAY)).thenReturn(true);
        when(personDirectory.displayName(ORG, ME)).thenReturn("Max");
        return declined;
    }

    @Test
    void theDeclinerReopensWithAFreshThirtyDays() {

        // given
        var declined = declinedDaysAgo(29);

        // when
        service.reopen(PROPOSAL);

        // then
        verify(proposals).reopen(ORG, PROPOSAL, TODAY);
        var reopened = declined.toBuilder().status(ProposalStatus.OPEN).answeredAt(null).reopenedAt(TODAY).build();
        assertThat(MentoringWindows.isOpen(reopened, TODAY.plus(Duration.ofDays(30)).minusMillis(1))).isTrue();
        var inOrder = inOrder(pairings, proposals);
        inOrder.verify(pairings).lockMember(ORG, COMMUNITY, ME);
        inOrder.verify(pairings).lockMember(ORG, COMMUNITY, MENTOR);
        inOrder.verify(proposals).reopen(ORG, PROPOSAL, TODAY);
    }

    @Test
    void reopeningTellsTheSender() {

        // given
        var declined = declinedDaysAgo(3);

        // when
        service.reopen(PROPOSAL);

        // then
        verify(notices).withdrawDeclined(declined);
        verify(notices).reopened(
                declined.toBuilder().status(ProposalStatus.OPEN).answeredAt(null).reopenedAt(TODAY).build(),
                new MentoringGuild("platform", "Platform"), "Max");
        verifyNoMoreInteractions(notices);
    }

    @Test
    void reopeningBeforeTheDeclineReachedTheSenderWithdrawsItAndTellsNothingMore() {

        // given
        var declined = declinedDaysAgo(0);
        when(notices.withdrawDeclined(declined)).thenReturn(true);

        // when
        service.reopen(PROPOSAL);

        // then
        verify(proposals).reopen(ORG, PROPOSAL, TODAY);
        verify(notices).withdrawDeclined(declined);
        verifyNoMoreInteractions(notices);
    }

    @Test
    void reopeningTwiceIsRefusedTheSecondTime() {

        // given
        var declined = declinedDaysAgo(3);
        when(proposals.reopen(ORG, PROPOSAL, TODAY)).thenReturn(true, false);

        // when
        service.reopen(PROPOSAL);
        receive(declined.toBuilder().status(ProposalStatus.OPEN).answeredAt(null).reopenedAt(TODAY).build());

        // then
        assertRefused(() -> service.reopen(PROPOSAL), "MENTORING_PROPOSAL_CLOSED");
        verify(notices, times(1)).reopened(any(), any(), anyString());
    }

    @Test
    void reopeningRefusedByTheStoreTellsNobody() {

        // given
        declinedDaysAgo(3);
        when(proposals.reopen(ORG, PROPOSAL, TODAY)).thenReturn(false);

        // when / then
        assertRefused(() -> service.reopen(PROPOSAL), "MENTORING_PROPOSAL_CLOSED");
        verifyNoInteractions(notices);
    }

    @Test
    void reopeningIsRefusedOnceTheTwoArePaired() {

        // given
        declinedDaysAgo(3);
        when(pairings.ongoingInvolving(ORG, COMMUNITY, ME)).thenReturn(List.of(Pairing.builder().id(PAIRING)
                .communityId(COMMUNITY).mentorUserId(MENTOR).menteeUserId(ME).startedAt(TODAY).build()));

        // when / then
        assertRefused(() -> service.reopen(PROPOSAL), "MENTORING_ALREADY_PAIRED");
        verify(proposals, never()).reopen(any(), anyLong(), any());
        verifyNoInteractions(notices);
    }

    @Test
    void reopeningIsRefusedWhenTheSenderProposedAgain() {

        // given
        var declined = declinedDaysAgo(3);
        var again = proposal(MENTOR, ME, Role.MENTEE).id(41L).createdAt(TODAY.minus(Duration.ofDays(1))).build();
        when(proposals.between(ORG, COMMUNITY, ME, MENTOR)).thenReturn(List.of(again, declined));

        // when / then
        assertRefused(() -> service.reopen(PROPOSAL), "MENTORING_PROPOSAL_OPEN");
        verify(proposals, never()).reopen(any(), anyLong(), any());
    }

    @Test
    void reopeningIsRefusedOverTheSendersNewerAnswer() {

        // given: after declining, Max proposed to Mia, and Mia declined
        var declined = declinedDaysAgo(3);
        var mine = proposal(MENTOR, ME, Role.MENTEE).id(41L).createdAt(TODAY.minus(Duration.ofHours(30)))
                .status(ProposalStatus.DECLINED).answeredAt(TODAY.minus(Duration.ofDays(1))).build();
        when(proposals.between(ORG, COMMUNITY, ME, MENTOR)).thenReturn(List.of(mine, declined));

        // when / then
        assertRefused(() -> service.reopen(PROPOSAL), "MENTORING_PROPOSAL_CLOSED");
        verify(proposals, never()).reopen(any(), anyLong(), any());
        verifyNoInteractions(notices);
    }

    @Test
    void reopeningIsRefusedOnceTheTwoPairedSinceTheDecline() {

        // given: after declining, the two paired and the pairing ended
        declinedDaysAgo(3);
        when(pairings.involving(ORG, ME)).thenReturn(
                List.of(Pairing.builder().id(PAIRING).communityId(COMMUNITY).mentorUserId(MENTOR).menteeUserId(ME)
                        .startedAt(TODAY.minus(Duration.ofDays(2))).endedAt(TODAY.minus(Duration.ofDays(1))).build()));

        // when / then
        assertRefused(() -> service.reopen(PROPOSAL), "MENTORING_PROPOSAL_CLOSED");
        verify(proposals, never()).reopen(any(), anyLong(), any());
    }

    @Test
    void aPairingBeforeTheDeclineOrWithSomeoneElseDoesNotStopAReopening() {

        // given
        declinedDaysAgo(3);
        when(pairings.involving(ORG, ME)).thenReturn(List.of(
                Pairing.builder().id(PAIRING).communityId(COMMUNITY).mentorUserId(MENTOR).menteeUserId(ME)
                        .startedAt(TODAY.minus(Duration.ofDays(90))).endedAt(TODAY.minus(Duration.ofDays(60))).build(),
                Pairing.builder().id(91L).communityId(COMMUNITY).mentorUserId("zoe").menteeUserId(ME)
                        .startedAt(TODAY.minus(Duration.ofDays(1))).build(),
                Pairing.builder().id(92L).communityId(8L).mentorUserId(MENTOR).menteeUserId(ME)
                        .startedAt(TODAY.minus(Duration.ofDays(1))).build()));

        // when
        service.reopen(PROPOSAL);

        // then
        verify(proposals).reopen(ORG, PROPOSAL, TODAY);
    }

    @Test
    void reopeningAfterThirtyDaysIsRefused() {

        // given
        declinedDaysAgo(30);

        // when / then
        assertRefused(() -> service.reopen(PROPOSAL), "MENTORING_PROPOSAL_CLOSED");
        verify(proposals, never()).reopen(any(), anyLong(), any());
    }

    @Test
    void onlyTheDeclinerReopens() {

        // given
        receive(proposal(ME, MENTEE, Role.MENTOR).status(ProposalStatus.DECLINED)
                .answeredAt(TODAY.minus(Duration.ofDays(1))).build());

        // when / then
        assertThatThrownBy(() -> service.reopen(PROPOSAL)).isInstanceOf(NotFoundException.class);
        verify(proposals, never()).reopen(any(), anyLong(), any());
    }

    @Test
    void reopeningAProposalThatWasNeverDeclinedIsRefused() {

        // given
        receive(proposal(MENTOR, ME, Role.MENTOR).build());

        // when / then
        assertRefused(() -> service.reopen(PROPOSAL), "MENTORING_PROPOSAL_CLOSED");
    }

    @Test
    void reopeningIsRefusedWhenEitherIsNoLongerHere() {

        // given
        declinedDaysAgo(3);
        when(communityAccess.isMember(COMMUNITY, MENTOR)).thenReturn(false);

        // when / then
        assertRefused(() -> service.reopen(PROPOSAL), "MENTORING_PROPOSAL_CLOSED");
        verifyNoInteractions(notices);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void reopeningInASwitchedOffOrArchivedGuildIsRefused(boolean switchedOff) {

        // given
        declinedDaysAgo(3);
        when(communityAccess.communityById(COMMUNITY))
                .thenReturn(switchedOff ? community(false, false) : community(true, true));

        // when / then
        assertRefused(() -> service.reopen(PROPOSAL), switchedOff ? "MENTORING_OFF" : "COMMUNITY_ARCHIVED");
    }

    @Test
    void aMentorAtTheLimitIsStillCheckedAtAccept() {

        // given
        var declined = declinedDaysAgo(3);
        when(availabilities.find(ORG, COMMUNITY, MENTOR)).thenReturn(Optional.of(mentor(MENTOR, 1)));
        when(pairings.ongoingAsMentor(ORG, COMMUNITY, MENTOR)).thenReturn(1);

        // when
        service.reopen(PROPOSAL);
        receive(declined.toBuilder().status(ProposalStatus.OPEN).answeredAt(null).reopenedAt(TODAY).build());

        // then
        verify(proposals).reopen(ORG, PROPOSAL, TODAY);
        assertRefused(() -> service.accept(PROPOSAL), "MENTORING_MENTOR_FULL");
    }

    private void assertInvalid(ProposeRequest request, String code) {

        assertThatThrownBy(() -> service.propose(SLUG, request)).isInstanceOfSatisfying(ValidationException.class,
                e -> assertThat(e.getCode()).isEqualTo(code));
    }

    private static void assertRefused(ThrowingCallable call, String code) {

        assertThatThrownBy(call).isInstanceOfSatisfying(ConflictException.class,
                e -> assertThat(e.getCode()).isEqualTo(code));
    }
}
