package dev.axonia.mentoring.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import dev.axonia.CoreJdbcTestSupport;
import dev.axonia.expertise.api.CommunityAreas;
import dev.axonia.mentoring.adapter.out.persistence.AvailabilityRepositoryImpl;
import dev.axonia.mentoring.adapter.out.persistence.JournalRepositoryImpl;
import dev.axonia.mentoring.adapter.out.persistence.PairingRepositoryImpl;
import dev.axonia.mentoring.adapter.out.persistence.ProposalRepositoryImpl;
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
import dev.axonia.mentoring.domain.MentoringCommunity;
import dev.axonia.mentoring.domain.MentoringPerson;
import dev.axonia.mentoring.domain.Pairing;
import dev.axonia.mentoring.domain.Proposal;
import dev.axonia.mentoring.domain.ProposalStatus;
import dev.axonia.mentoring.domain.Role;
import dev.axonia.mentoring.domain.Topic;
import dev.axonia.shared.api.error.ConflictException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Acceptance against the real tables: the ongoing-pair index is the last word when a concurrent acceptance committed
 * the same pair after this one's check read nothing.
 */
@Import({ProposalRepositoryImpl.class, PairingRepositoryImpl.class, JournalRepositoryImpl.class,
        AvailabilityRepositoryImpl.class})
class ProposalServiceJdbcTest extends CoreJdbcTestSupport {

    private static final String ORG = "default";
    private static final long COMMUNITY = 7L;
    private static final Instant NOW = Instant.parse("2026-11-16T10:00:00Z");

    @Autowired
    private ProposalRepository proposals;
    @Autowired
    private PairingRepository pairings;
    @Autowired
    private JournalRepository journal;
    @Autowired
    private AvailabilityRepository availabilities;

    private final CurrentUser currentUser = mock(CurrentUser.class);
    private final CommunityAccess communityAccess = mock(CommunityAccess.class);
    private final Vocabulary vocabulary = mock(Vocabulary.class);
    private final PersonDirectory personDirectory = mock(PersonDirectory.class);

    @BeforeEach
    void setUp() {

        when(currentUser.userId()).thenReturn("mia");
        when(communityAccess.communityById(COMMUNITY))
                .thenReturn(new MentoringCommunity(COMMUNITY, "platform", "Platform", false, true));
        when(communityAccess.isMember(anyLong(), anyString())).thenReturn(true);
        when(personDirectory.isPresent(anyString(), anyString())).thenReturn(true);
        when(vocabulary.areasOf(COMMUNITY)).thenReturn(new CommunityAreas(Map.of()));
        when(vocabulary.areasIn("platform")).thenReturn(Set.of("kafka"));
        when(vocabulary.normalized(anyString())).thenAnswer(inv -> inv.<String>getArgument(0).toLowerCase());
        when(personDirectory.resolve(any())).thenReturn(Map.of("max", new MentoringPerson("max", "Max", null, null)));
    }

    private static MemberClocks utc() {

        var clocks = mock(MemberClocks.class);
        when(clocks.zoneOf(anyString(), anyString())).thenReturn(ZoneOffset.UTC);
        return clocks;
    }

    private ProposalService service(PairingRepository pairingRepository) {

        OrganizationContext organizationContext = () -> ORG;
        return new ProposalService(organizationContext, currentUser, communityAccess, proposals, pairingRepository,
                new PairingGuards(availabilities, pairingRepository), new FirstTopics(vocabulary, journal),
                personDirectory, mock(MentoringNotices.class), utc(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private long proposeToMia(Instant at) {

        return proposals.insert(ORG,
                Proposal.builder().communityId(COMMUNITY).mentorUserId("mia").menteeUserId("max")
                        .proposedBy(Role.MENTEE).topics(List.of("Kafka", "Giving feedback")).message("Hello")
                        .status(ProposalStatus.OPEN).createdAt(at).build());
    }

    @Test
    void acceptingStoresThePairingItsTopicsAndWithdrawsTheCrossedProposal() {

        // given
        var accepted = proposeToMia(NOW.minusSeconds(60));
        var crossed = proposals.insert(ORG,
                Proposal.builder().communityId(COMMUNITY).mentorUserId("mia").menteeUserId("max")
                        .proposedBy(Role.MENTOR).topics(List.of("Kafka")).message("Hi").status(ProposalStatus.OPEN)
                        .createdAt(NOW.minusSeconds(30)).build());

        // when
        var actual = service(pairings).accept(accepted);

        // then
        assertThat(pairings.find(ORG, actual.id())).get()
                .extracting(Pairing::mentorUserId, Pairing::menteeUserId, Pairing::startedAt, Pairing::endedAt)
                .containsExactly("mia", "max", NOW, null);
        assertThat(journal.topicsOf(ORG, actual.id())).extracting(Topic::label, Topic::area, Topic::position)
                .containsExactly(tuple("Kafka", "kafka", 0), tuple("Giving feedback", null, 1));
        assertThat(proposals.find(ORG, accepted)).get().extracting(Proposal::status).isEqualTo(ProposalStatus.ACCEPTED);
        assertThat(proposals.find(ORG, crossed)).get().extracting(Proposal::status).isEqualTo(ProposalStatus.WITHDRAWN);
    }

    @Test
    void aSecondAcceptanceHittingTheOngoingPairIndexIsAlreadyPaired() {

        // given: another acceptance paired the two after this one's check read no pairing
        pairings.insert(ORG, Pairing.builder().communityId(COMMUNITY).mentorUserId("mia").menteeUserId("max")
                .startedAt(NOW.minusSeconds(5)).build());
        var proposal = proposeToMia(NOW.minusSeconds(60));
        var racing = mock(PairingRepository.class, delegatesTo(pairings));
        doReturn(List.of()).when(racing).ongoingInvolving(any(), any(), any());

        // when / then
        assertThatThrownBy(() -> service(racing).accept(proposal)).isInstanceOfSatisfying(ConflictException.class,
                e -> assertThat(e.getCode()).isEqualTo("MENTORING_ALREADY_PAIRED"));
    }

    /** Mia declined Max's proposal to mentor him, then proposed to mentor him herself. */
    private long[] declinedThenProposedBack() {

        var declined = proposals.insert(ORG,
                Proposal.builder().communityId(COMMUNITY).mentorUserId("mia").menteeUserId("max")
                        .proposedBy(Role.MENTEE).topics(List.of("Kafka")).message("Hello")
                        .status(ProposalStatus.DECLINED).createdAt(NOW.minusSeconds(600))
                        .answeredAt(NOW.minusSeconds(300)).build());
        var back = proposals.insert(ORG,
                Proposal.builder().communityId(COMMUNITY).mentorUserId("mia").menteeUserId("max")
                        .proposedBy(Role.MENTOR).topics(List.of("Kafka")).message("Hi").status(ProposalStatus.OPEN)
                        .createdAt(NOW.minusSeconds(60)).build());
        when(personDirectory.resolve(any())).thenAnswer(inv -> inv.<Collection<String>>getArgument(0).stream()
                .collect(Collectors.toMap(id -> id, id -> new MentoringPerson(id, id, null, null))));
        return new long[]{declined, back};
    }

    @Test
    void reopenRacesAcceptOfANewProposalToOnePairing() {

        // given: the acceptance of the new proposal commits first
        var ids = declinedThenProposedBack();
        when(currentUser.userId()).thenReturn("max");
        service(pairings).accept(ids[1]);
        when(currentUser.userId()).thenReturn("mia");

        // when / then
        assertThatThrownBy(() -> service(pairings).reopen(ids[0])).isInstanceOfSatisfying(ConflictException.class,
                e -> assertThat(e.getCode()).isEqualTo("MENTORING_ALREADY_PAIRED"));
        assertThat(proposals.find(ORG, ids[0])).get().extracting(Proposal::status).isEqualTo(ProposalStatus.DECLINED);
        assertThat(pairings.ongoingInvolving(ORG, COMMUNITY, "mia")).hasSize(1);
    }

    @Test
    void reopenBeforeTheNewProposalIsAnsweredIsRefusedAndTheAcceptanceStillPairs() {

        // given
        var ids = declinedThenProposedBack();

        // when / then
        assertThatThrownBy(() -> service(pairings).reopen(ids[0])).isInstanceOfSatisfying(ConflictException.class,
                e -> assertThat(e.getCode()).isEqualTo("MENTORING_PROPOSAL_OPEN"));
        when(currentUser.userId()).thenReturn("max");
        service(pairings).accept(ids[1]);
        assertThat(proposals.find(ORG, ids[0])).get().extracting(Proposal::status).isEqualTo(ProposalStatus.DECLINED);
        assertThat(pairings.ongoingInvolving(ORG, COMMUNITY, "mia")).hasSize(1);
    }
}
