package dev.axonia.mentoring.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import dev.axonia.CoreJdbcTestSupport;
import dev.axonia.expertise.api.CommunityAreas;
import dev.axonia.mentoring.adapter.out.persistence.JournalRepositoryImpl;
import dev.axonia.mentoring.adapter.out.persistence.PairingRepositoryImpl;
import dev.axonia.mentoring.adapter.out.persistence.ProposalRepositoryImpl;
import dev.axonia.mentoring.api.NoteRequest;
import dev.axonia.mentoring.api.ProposeRequest;
import dev.axonia.mentoring.api.TopicRequest;
import dev.axonia.mentoring.application.port.out.AvailabilityRepository;
import dev.axonia.mentoring.application.port.out.CommunityAccess;
import dev.axonia.mentoring.application.port.out.CurrentUser;
import dev.axonia.mentoring.application.port.out.JournalRepository;
import dev.axonia.mentoring.application.port.out.MeetingRepository;
import dev.axonia.mentoring.application.port.out.MemberClocks;
import dev.axonia.mentoring.application.port.out.MentoringNotices;
import dev.axonia.mentoring.application.port.out.OrganizationContext;
import dev.axonia.mentoring.application.port.out.PairingRepository;
import dev.axonia.mentoring.application.port.out.PersonDirectory;
import dev.axonia.mentoring.application.port.out.PointerTitles;
import dev.axonia.mentoring.application.port.out.ProposalRepository;
import dev.axonia.mentoring.application.port.out.Vocabulary;
import dev.axonia.mentoring.application.port.out.WordsRepository;
import dev.axonia.mentoring.domain.Availability;
import dev.axonia.mentoring.domain.EndReason;
import dev.axonia.mentoring.domain.MentoringCommunity;
import dev.axonia.mentoring.domain.MentoringPerson;
import dev.axonia.mentoring.domain.Pairing;
import dev.axonia.mentoring.domain.Proposal;
import dev.axonia.mentoring.domain.ProposalStatus;
import dev.axonia.mentoring.domain.Role;
import dev.axonia.mentoring.domain.Topic;
import dev.axonia.mentoring.domain.TopicState;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.web.PublicUrls;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Racing requests, each in its own committed transaction, as parallel requests would run. Rows are committed, so the
 * test is not transactional and deletes what it wrote.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({ProposalRepositoryImpl.class, PairingRepositoryImpl.class, JournalRepositoryImpl.class})
class MentoringConcurrencyTest extends CoreJdbcTestSupport {

    private static final String ORG = "default";
    private static final long COMMUNITY = 880_101L;
    private static final String MENTOR = "race-mia";
    private static final String MENTEE = "race-max";
    private static final String MENTOR_LOCK = "mentoring_member:" + ORG + ":" + COMMUNITY + ":" + MENTOR;
    private static final int PARALLEL_REQUESTS = 8;
    private static final Instant NOW = Instant.parse("2026-11-16T10:00:00Z");

    @Autowired
    private ProposalRepository proposals;
    @Autowired
    private PairingRepository pairings;
    @Autowired
    private JournalRepository journal;
    @Autowired
    private NamedParameterJdbcTemplate jdbc;
    @Autowired
    private PlatformTransactionManager transactionManager;

    private final OrganizationContext organizationContext = () -> ORG;
    private final CurrentUser currentUser = mock(CurrentUser.class);
    private final CommunityAccess communityAccess = mock(CommunityAccess.class);
    private final AvailabilityRepository availabilities = mock(AvailabilityRepository.class);
    private final Vocabulary vocabulary = mock(Vocabulary.class);
    private final PersonDirectory personDirectory = mock(PersonDirectory.class);
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @BeforeEach
    void setUp() {

        var community = new MentoringCommunity(COMMUNITY, "race", "Race", false, true);
        when(communityAccess.communityForSlug("race")).thenReturn(community);
        when(communityAccess.communityById(COMMUNITY)).thenReturn(community);
        when(communityAccess.isMember(anyLong(), anyString())).thenReturn(true);
        when(communityAccess.communityIdsOf(ORG, MENTOR)).thenReturn(List.of(COMMUNITY));
        when(personDirectory.isPresent(anyString(), anyString())).thenReturn(true);
        when(personDirectory.resolve(anyCollection()))
                .thenReturn(Map.of(MENTOR, new MentoringPerson(MENTOR, "Mia", null, null), MENTEE,
                        new MentoringPerson(MENTEE, "Max", null, null)));
        when(availabilities.findListed(ORG, COMMUNITY, MENTOR)).thenReturn(Optional.of(Availability.builder()
                .communityId(COMMUNITY).userId(MENTOR).takingMentees(true).updatedAt(NOW).build()));
        when(availabilities.find(any(), anyLong(), any())).thenReturn(Optional.empty());
        when(vocabulary.areasOf(COMMUNITY)).thenReturn(new CommunityAreas(Map.of()));
        when(vocabulary.areasIn("race")).thenReturn(Set.of());
        when(vocabulary.normalized(anyString())).thenAnswer(inv -> inv.getArgument(0));
    }

    @AfterEach
    void deleteWhatWasWritten() {

        var scope = Map.of("org", ORG, "community", COMMUNITY);
        jdbc.update("DELETE FROM mentoring_pairing WHERE organization_id = :org AND community_id = :community", scope);
        jdbc.update("DELETE FROM mentoring_proposal WHERE organization_id = :org AND community_id = :community", scope);
    }

    private static MemberClocks utc() {

        var clocks = mock(MemberClocks.class);
        when(clocks.zoneOf(anyString(), anyString())).thenReturn(ZoneOffset.UTC);
        return clocks;
    }

    private JournalService journalService() {

        var clocks = utc();
        var reads = new JournalReads(communityAccess, journal, mock(MeetingRepository.class),
                mock(WordsRepository.class), mock(PointerTitles.class), personDirectory, clocks, clock);
        var publicUrls = mock(PublicUrls.class);
        when(publicUrls.publicBaseUrl()).thenReturn("https://acme.example.test");
        return new JournalService(new PairingParties(organizationContext, currentUser, pairings), communityAccess,
                journal, vocabulary, reads, clocks, publicUrls, clock);
    }

    private ProposalService proposalService() {

        return new ProposalService(organizationContext, currentUser, communityAccess, proposals, pairings,
                new PairingGuards(availabilities, pairings), new FirstTopics(vocabulary, journal), personDirectory,
                mock(MentoringNotices.class), utc(), clock);
    }

    private PairingService pairingService() {

        return new PairingService(organizationContext, currentUser, communityAccess, pairings, proposals,
                mock(JournalRepository.class), mock(MeetingRepository.class), availabilities, personDirectory,
                mock(MentoringNotices.class), utc(), clock);
    }

    private long ongoingPairing() {

        return pairings.insert(ORG, Pairing.builder().communityId(COMMUNITY).mentorUserId(MENTOR).menteeUserId(MENTEE)
                .startedAt(NOW.minusSeconds(3600)).build());
    }

    @Test
    void parallelTopicsStopAtTheJournalsCap() throws Exception {

        // given
        when(currentUser.userId()).thenReturn(MENTEE);
        var pairing = ongoingPairing();
        for (var i = 0; i < 26; i++) {
            journal.insertTopic(ORG, Topic.builder().pairingId(pairing).label("Seeded " + i).state(TopicState.TODO)
                    .position(i).createdAt(NOW).build());
        }
        var service = journalService();
        var requests = new ArrayList<Supplier<Object>>();
        for (var i = 0; i < PARALLEL_REQUESTS; i++) {
            var label = "Parallel " + i;
            requests.add(() -> {
                service.addTopic(pairing, new TopicRequest(label, null));
                return label;
            });
        }

        // when
        var outcomes = runTogether(requests);

        // then
        assertThat(journal.topicsOf(ORG, pairing)).hasSize(30);
        assertThat(outcomes.stream().filter(String.class::isInstance)).hasSize(4);
        assertThat(outcomes.stream().filter(ConflictException.class::isInstance).map(ConflictException.class::cast)
                .map(ConflictException::getCode)).hasSize(PARALLEL_REQUESTS - 4).containsOnly("MENTORING_JOURNAL_FULL");
    }

    @Test
    void aNoteRacingTheEndIsRefusedOnceTheEndCommits() throws Exception {

        // given - the end holds the pairing's row until the note waits on it
        when(currentUser.userId()).thenReturn(MENTEE);
        var pairing = ongoingPairing();
        var service = journalService();
        var transactions = new TransactionTemplate(transactionManager);
        var endHoldsTheRow = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            // when
            var end = pool.submit(() -> {
                transactions.executeWithoutResult(status -> {
                    pairings.end(ORG, pairing, EndReason.MENTOR, NOW);
                    endHoldsTheRow.countDown();
                    awaitSomeoneWaitingOnARow();
                });
                return null;
            });
            var note = pool.submit(() -> {
                endHoldsTheRow.await();
                try {
                    transactions.executeWithoutResult(
                            status -> service.addNote(pairing, new NoteRequest("Written as it ended", null, null)));
                    return null;
                } catch (ConflictException refused) {
                    return refused.getCode();
                }
            });
            end.get(30, TimeUnit.SECONDS);

            // then
            assertThat(note.get(30, TimeUnit.SECONDS)).isEqualTo("MENTORING_PAIRING_ENDED");
            assertThat(journal.notesOf(ORG, pairing)).isEmpty();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void parallelProposalsBetweenTheSameTwoLandOnce() throws Exception {

        // given
        when(currentUser.userId()).thenReturn(MENTEE);
        var service = proposalService();
        var requests = new ArrayList<Supplier<Object>>();
        for (var i = 0; i < PARALLEL_REQUESTS; i++) {
            requests.add(() -> service.propose("race", new ProposeRequest(MENTOR, "MENTEE", List.of("Kafka"), "Hi")));
        }

        // when
        var outcomes = runTogether(requests);

        // then
        assertThat(proposals.between(ORG, COMMUNITY, MENTEE, MENTOR)).singleElement().extracting(Proposal::status)
                .isEqualTo(ProposalStatus.OPEN);
        assertThat(outcomes.stream().filter(ConflictException.class::isInstance).map(ConflictException.class::cast)
                .map(ConflictException::getCode)).hasSize(PARALLEL_REQUESTS - 1)
                .containsOnly("MENTORING_PROPOSAL_OPEN");
    }

    @Test
    void aDeactivationRacingAnAcceptanceLeavesNoOngoingPairing() throws Exception {

        // given - the acceptance pauses at the sender's presence until the deactivation waits on the sender's lock
        when(currentUser.userId()).thenReturn(MENTEE);
        var proposal = proposals.insert(ORG,
                Proposal.builder().communityId(COMMUNITY).mentorUserId(MENTOR).menteeUserId(MENTEE)
                        .proposedBy(Role.MENTOR).topics(List.of("Kafka")).message("Hi").status(ProposalStatus.OPEN)
                        .createdAt(NOW.minusSeconds(60)).build());
        var presenceRead = new CountDownLatch(1);
        when(personDirectory.isPresent(ORG, MENTOR)).thenAnswer(invocation -> {
            presenceRead.countDown();
            awaitBlocked(MENTOR_LOCK);
            return true;
        });
        var accepting = proposalService();
        var deactivating = pairingService();
        var transactions = new TransactionTemplate(transactionManager);
        var pool = Executors.newFixedThreadPool(2);
        try {
            // when
            var accept = pool.submit(() -> transactions.execute(status -> accepting.accept(proposal)));
            var deactivate = pool.submit(() -> {
                presenceRead.await();
                transactions.executeWithoutResult(status -> deactivating.deactivated(ORG, MENTOR));
                return null;
            });
            accept.get(30, TimeUnit.SECONDS);
            deactivate.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        // then
        assertThat(pairings.ongoingInvolving(ORG, COMMUNITY, MENTOR)).isEmpty();
        assertThat(pairings.involving(ORG, MENTOR)).singleElement().extracting(Pairing::endReason)
                .isEqualTo(EndReason.DEACTIVATED);
    }

    /** Waits, for a few seconds at most, until some transaction waits on the advisory lock of the key. */
    private void awaitBlocked(String lockKey) {

        awaitUpToFiveSeconds(() -> Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM pg_locks
                                WHERE locktype = 'advisory' AND NOT granted AND objsubid = 1
                                  AND ((classid::bigint << 32) | objid::bigint) = hashtextextended(:key, 0))
                """, Map.of("key", lockKey), Boolean.class)));
    }

    /** Waits, for a few seconds at most, until some transaction waits on a row another one holds. */
    private void awaitSomeoneWaitingOnARow() {

        awaitUpToFiveSeconds(() -> Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM pg_locks WHERE locktype IN ('transactionid', 'tuple') AND NOT granted)",
                Map.of(), Boolean.class)));
    }

    /** Bounded, so a missing lock shows as a failed assertion rather than a hung build. */
    private static void awaitUpToFiveSeconds(Supplier<Boolean> condition) {

        var deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        try {
            while (!condition.get() && System.nanoTime() < deadline) {
                TimeUnit.MILLISECONDS.sleep(20);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /** Each outcome is what the request returned, or the conflict it ended in. */
    private List<Object> runTogether(List<Supplier<Object>> requests) throws Exception {

        var transactions = new TransactionTemplate(transactionManager);
        var start = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(requests.size());
        try {
            var futures = new ArrayList<Future<Object>>();
            for (var request : requests) {
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        return transactions.execute(status -> request.get());
                    } catch (ConflictException refused) {
                        return refused;
                    }
                }));
            }
            start.countDown();
            var outcomes = new ArrayList<>();
            for (var future : futures) {
                outcomes.add(future.get(30, TimeUnit.SECONDS));
            }
            return outcomes;
        } finally {
            pool.shutdownNow();
        }
    }
}
