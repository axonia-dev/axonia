package dev.axonia.expertise.adapter.out.persistence;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import dev.axonia.community.api.CommunityMemberLeft;
import dev.axonia.expertise.adapter.in.event.DeclarationMembershipListener;
import dev.axonia.expertise.application.CurationScope;
import dev.axonia.expertise.application.ExpertiseCurationService;
import dev.axonia.expertise.application.ExpertiseDeclarationService;
import dev.axonia.expertise.application.port.out.CommunityAccess;
import dev.axonia.expertise.application.port.out.FakeNamedAreaRepository;
import dev.axonia.expertise.application.port.out.FakeTagAliasRepository;
import dev.axonia.expertise.domain.AliasResolver;
import dev.axonia.expertise.domain.CommunityRef;
import dev.axonia.expertise.domain.Declaration;
import dev.axonia.expertise.domain.SignalRow;
import dev.axonia.expertise.domain.SignalSource;
import dev.axonia.expertise.domain.SignalType;
import dev.axonia.expertise.domain.SourceType;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.ForbiddenException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Parallel writes by one member, each in its own committed transaction, as parallel requests would run. Rows are
 * committed, so the test is not transactional and deletes what it wrote.
 */
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DeclarationConcurrencyTest extends ExpertiseJdbcTestSupport {

    private static final String ORG = "default";
    private static final String SLUG = "concurrency";
    private static final long COMMUNITY = 880_001L;
    private static final String DECLARER = "concurrent-declarer";
    private static final String CURATOR = "concurrent-curator";
    private static final String DECLARER_LOCK = "expertise_declaration:" + ORG + ":" + COMMUNITY + ":" + DECLARER;
    private static final String COMMUNITY_LOCK = "expertise_vocabulary:" + ORG + ":" + COMMUNITY;
    private static final int PARALLEL_REQUESTS = 8;
    private static final Instant BASE = Instant.parse("2026-09-12T12:00:00Z");

    @Autowired
    private DeclarationRepositoryImpl repo;

    @Autowired
    private VocabularyLockImpl vocabularyLock;

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private SignalWriter signalWriter;

    @Autowired
    private ExpertiseQueryRepositoryImpl queries;

    @Autowired
    private TagAliasRepositoryImpl aliases;

    @Autowired
    private TagDismissalRepositoryImpl dismissals;

    private CommunityAccess communityAccess;
    private ExpertiseDeclarationService service;

    @BeforeEach
    void setUp() {

        communityAccess = mock(CommunityAccess.class);
        when(communityAccess.communityIdForSlug(SLUG)).thenReturn(COMMUNITY);
        when(communityAccess.communityForSlug(SLUG)).thenReturn(new CommunityRef(COMMUNITY, false));
        when(communityAccess.isMember(COMMUNITY, DECLARER)).thenReturn(true);
        service = new ExpertiseDeclarationService(() -> ORG, () -> DECLARER, communityAccess, repo,
                new FakeTagAliasRepository(), new TickingClock(), vocabularyLock);
    }

    @AfterEach
    void deleteDeclarations() {

        var scope = Map.of("org", ORG, "community", COMMUNITY);
        jdbc.update("DELETE FROM expertise_declaration WHERE organization_id = :org AND community_id = :community",
                scope);
        jdbc.update("DELETE FROM expertise_signal WHERE organization_id = :org AND community_id = :community", scope);
        jdbc.update("DELETE FROM expertise_tag_alias WHERE organization_id = :org AND community_id = :community",
                scope);
    }

    @Test
    void parallelDeclaresOfDistinctTagsStopAtTheCap() throws Exception {

        // given
        for (var i = 0; i < 19; i++) {
            repo.insert(ORG, Declaration.builder().communityId(COMMUNITY).userId(DECLARER).tag("seeded-" + i)
                    .declaredAt(BASE).build());
        }
        var requests = new ArrayList<Supplier<Declaration>>();
        for (var i = 0; i < PARALLEL_REQUESTS; i++) {
            var tag = "parallel-" + i;
            requests.add(() -> service.declare(SLUG, tag));
        }

        // when
        var outcomes = runTogether(requests);

        // then
        assertThat(repo.inCommunity(ORG, COMMUNITY, DECLARER)).hasSize(20);
        assertThat(outcomes.stream().filter(Declaration.class::isInstance)).hasSize(1);
        assertThat(outcomes.stream().filter(ConflictException.class::isInstance).map(ConflictException.class::cast)
                .map(ConflictException::getCode)).hasSize(PARALLEL_REQUESTS - 1).containsOnly("DECLARATION_LIMIT");
    }

    @Test
    void parallelDeclaresOfOneTagAllReturnTheStoredDeclaration() throws Exception {

        // given
        var requests = new ArrayList<Supplier<Declaration>>();
        for (var i = 0; i < PARALLEL_REQUESTS; i++) {
            requests.add(() -> service.declare(SLUG, "kafka"));
        }

        // when
        var outcomes = runTogether(requests);

        // then
        var stored = repo.inCommunity(ORG, COMMUNITY, DECLARER);
        assertThat(stored).hasSize(1);
        assertThat(outcomes).hasSize(PARALLEL_REQUESTS).containsOnly(stored.getFirst());
    }

    @Test
    void aDeclareRacingALeaveLeavesNoDeclarationBehind() throws Exception {

        // given - the declare pauses right after reading the membership, until the leave has run or is blocked
        var member = new AtomicBoolean(true);
        var leaveDone = new CountDownLatch(1);
        var membershipRead = new CountDownLatch(1);
        when(communityAccess.isMember(COMMUNITY, DECLARER)).thenAnswer(invocation -> {
            var isMember = member.get();
            membershipRead.countDown();
            awaitDoneOrBlocked(leaveDone, DECLARER_LOCK);
            return isMember;
        });
        var listener = new DeclarationMembershipListener(service);
        var transactions = new TransactionTemplate(transactionManager);
        var pool = Executors.newFixedThreadPool(2);
        try {
            // when
            var declare = pool.submit(() -> transactions.execute(status -> service.declare(SLUG, "kafka")));
            var leave = pool.submit(() -> {
                membershipRead.await();
                transactions.executeWithoutResult(status -> {
                    member.set(false);
                    listener.on(new CommunityMemberLeft(ORG, COMMUNITY, DECLARER));
                });
                leaveDone.countDown();
                return null;
            });
            declare.get(30, TimeUnit.SECONDS);
            leave.get(30, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
        }

        // then
        assertThat(repo.inCommunity(ORG, COMMUNITY, DECLARER)).isEmpty();
    }

    @Test
    void aDeclareBehindALeaveThatHoldsTheLockFindsTheMembershipGone() throws Exception {

        // given - the leave takes the declarer's lock first and commits only once the declare waits on it
        var member = new AtomicBoolean(true);
        when(communityAccess.isMember(COMMUNITY, DECLARER)).thenAnswer(invocation -> member.get());
        var listener = new DeclarationMembershipListener(service);
        var transactions = new TransactionTemplate(transactionManager);
        var leaveHoldsTheLock = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            // when
            var leave = pool.submit(() -> {
                transactions.executeWithoutResult(status -> {
                    listener.on(new CommunityMemberLeft(ORG, COMMUNITY, DECLARER));
                    leaveHoldsTheLock.countDown();
                    awaitBlocked(DECLARER_LOCK);
                    member.set(false);
                });
                return null;
            });
            var declare = pool.submit(() -> {
                leaveHoldsTheLock.await();
                try {
                    return transactions.execute(status -> service.declare(SLUG, "kafka"));
                } catch (ForbiddenException refused) {
                    return refused;
                }
            });
            leave.get(30, TimeUnit.SECONDS);

            // then
            assertThat(declare.get(30, TimeUnit.SECONDS)).isInstanceOf(ForbiddenException.class);
            assertThat(repo.inCommunity(ORG, COMMUNITY, DECLARER)).isEmpty();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void aDeclareRacingACuratorsRenameReturnsTheDeclarationItStored() throws Exception {

        // given - the declarer holds "k8s"; a curator renames it onto "kubernetes" while they declare "kubernetes"
        curatorSignals("k8s");
        declared("k8s");

        // when
        var outcome = raceDeclare("kubernetes", curation -> curation.rename(SLUG, "k8s", "kubernetes"));

        // then
        assertThat(outcome.getValue()).contains(outcome.getKey());
    }

    @Test
    void aDeclareRacingACuratorsMergeReturnsTheAreaAsTheMemberSeesIt() throws Exception {

        // given - the declarer holds "k8s"; a curator merges it into "kubernetes" while they declare "kubernetes"
        curatorSignals("k8s", "kubernetes");
        declared("k8s");

        // when
        var outcome = raceDeclare("kubernetes", curation -> curation.merge(SLUG, "k8s", "kubernetes"));

        // then
        assertThat(outcome.getValue()).contains(outcome.getKey());
    }

    @Test
    void aDeclareRacingACuratorsSplitReturnsTheAreaAsTheMemberSeesIt() throws Exception {

        // given - "k8s" reads as "kubernetes"; a curator splits it while the member declares "k8s"
        curatorSignals("k8s", "kubernetes");
        aliases.addAlias(ORG, COMMUNITY, "k8s", "kubernetes");

        // when
        var outcome = raceDeclare("k8s", curation -> curation.split(SLUG, "k8s"));

        // then
        assertThat(outcome.getValue()).contains(outcome.getKey());
    }

    private void curatorSignals(String... tags) {

        var rows = Stream.of(tags).map(tag -> new SignalRow(CURATOR, tag, SignalType.CONTRIBUTED, BASE)).toList();
        signalWriter.replaceSource(new SignalSource(ORG, COMMUNITY, SourceType.ARTIFACT, 880_001L), rows);
    }

    private void declared(String tag) {

        repo.insert(ORG,
                Declaration.builder().communityId(COMMUNITY).userId(DECLARER).tag(tag).declaredAt(BASE).build());
    }

    /**
     * Runs the curation while the declare is paused just before it inserts, and returns what the declare answered with
     * the member's declared areas as its own transaction reads them once it is done: each area once, at its earliest.
     */
    private Map.Entry<Declaration, List<Declaration>> raceDeclare(String tag, Consumer<ExpertiseCurationService> curate)
            throws Exception {

        when(communityAccess.canCurateTags(COMMUNITY, CURATOR)).thenReturn(true);
        var curation = new ExpertiseCurationService(() -> ORG,
                new CurationScope(() -> CURATOR, communityAccess, queries, repo, aliases, List.of()), queries, aliases,
                dismissals, List.of(), signalWriter, repo, new FakeNamedAreaRepository(), vocabularyLock);
        var aboutToInsert = new CountDownLatch(1);
        var curationDone = new CountDownLatch(1);
        var declaring = new ExpertiseDeclarationService(() -> ORG, () -> DECLARER, communityAccess, repo, aliases,
                new PausingClock(aboutToInsert, curationDone, COMMUNITY_LOCK), vocabularyLock);
        var transactions = new TransactionTemplate(transactionManager);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var declare = pool.submit(
                    () -> transactions.execute(status -> Map.entry(declaring.declare(SLUG, tag), declaredAreas())));
            var curating = pool.submit(() -> {
                aboutToInsert.await();
                transactions.executeWithoutResult(status -> curate.accept(curation));
                curationDone.countDown();
                return null;
            });
            var outcome = declare.get(30, TimeUnit.SECONDS);
            curating.get(30, TimeUnit.SECONDS);
            return outcome;
        } finally {
            pool.shutdownNow();
        }
    }

    private List<Declaration> declaredAreas() {

        var aliasMap = aliases.aliasMap(ORG, COMMUNITY);
        var earliest = new LinkedHashMap<String, Declaration>();
        repo.inCommunity(ORG, COMMUNITY, DECLARER).stream().sorted(Comparator.comparing(Declaration::declaredAt))
                .forEach(declaration -> {
                    var area = AliasResolver.canonicalOf(declaration.tag(), aliasMap);
                    earliest.putIfAbsent(area, new Declaration(COMMUNITY, DECLARER, area, declaration.declaredAt()));
                });
        return List.copyOf(earliest.values());
    }

    /** Waits until the other transaction is done, or some transaction is waiting on the advisory lock of the key. */
    private void awaitDoneOrBlocked(CountDownLatch done, String lockKey) {

        try {
            var blocked = false;
            while (!blocked && !done.await(20, TimeUnit.MILLISECONDS)) {
                blocked = isAwaited(lockKey);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /** Waits until some transaction is waiting on the advisory lock of the key. */
    private void awaitBlocked(String lockKey) {

        try {
            while (!isAwaited(lockKey)) {
                TimeUnit.MILLISECONDS.sleep(20);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    /** A bigint advisory key shows in pg_locks split into classid (high half) and objid (low half). */
    private boolean isAwaited(String lockKey) {

        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM pg_locks
                                WHERE locktype = 'advisory' AND NOT granted AND objsubid = 1
                                  AND ((classid::bigint << 32) | objid::bigint) = hashtextextended(:key, 0))
                """, Map.of("key", lockKey), Boolean.class));
    }

    /** Each outcome is the returned declaration or the exception the request ended in. */
    private List<Object> runTogether(List<Supplier<Declaration>> requests) throws Exception {

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

    /**
     * Pauses the declare at its clock read, just before it inserts, until the other transaction is done or waits on the
     * advisory lock of the key.
     */
    private final class PausingClock extends Clock {

        private final CountDownLatch paused;
        private final CountDownLatch otherDone;
        private final String lockKey;

        private PausingClock(CountDownLatch paused, CountDownLatch otherDone, String lockKey) {

            this.paused = paused;
            this.otherDone = otherDone;
            this.lockKey = lockKey;
        }

        @Override
        public ZoneId getZone() {

            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {

            return this;
        }

        @Override
        public Instant instant() {

            paused.countDown();
            awaitDoneOrBlocked(otherDone, lockKey);
            return BASE.plusSeconds(60);
        }
    }

    /** Hands every declare a later instant, so a declaration that was never stored cannot pass for the stored one. */
    private static final class TickingClock extends Clock {

        private final AtomicLong ticks = new AtomicLong();

        @Override
        public ZoneId getZone() {

            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {

            return this;
        }

        @Override
        public Instant instant() {

            return BASE.plusSeconds(ticks.incrementAndGet());
        }
    }
}
