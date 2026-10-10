package dev.axonia.communication.adapter.out.persistence;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.context.transaction.BeforeTransaction;

import dev.axonia.communication.api.MessageOutcome;
import dev.axonia.communication.api.MessageType;
import dev.axonia.communication.api.OutboundMessageMother;
import dev.axonia.communication.api.OutboxStatus;
import dev.axonia.communication.api.SuppressionReason;
import dev.axonia.communication.application.OutboxDrain;
import dev.axonia.communication.application.port.out.Outbox;
import dev.axonia.communication.domain.DeliveryChannel;
import dev.axonia.communication.domain.OutboxDelivery;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs as the Testcontainers superuser (table owner), which bypasses RLS by ownership - but since
 * {@link JdbcOutbox#CLAIM_SQL} now also carries an explicit {@code organization_id = current_setting('app.current_org',
 * true)} predicate (defence in depth, layered on top of RLS - see {@link JdbcOutbox}'s own class javadoc), this class
 * DOES need the GUC set correctly on the connection {@code claim} runs against, even though the connecting role itself
 * ignores RLS. {@link #authenticate()} is deliberately {@code @BeforeTransaction}, not {@code @BeforeEach}:
 * {@code @DataJdbcTest}'s test-managed transaction is started by {@code TransactionalTestExecutionListener} BEFORE the
 * class's own {@code @BeforeEach} methods run, and {@code OrganizationAwareDataSource} reads the resolver only once,
 * when that transaction's single connection is acquired - so a JWT set from {@code @BeforeEach} would authenticate too
 * late, after the GUC had already been fixed for the whole test method. {@code @BeforeTransaction} runs before the
 * transaction (and therefore the connection) exists at all, so the GUC is set to "acme" for every query the method
 * runs, matching the organization id written by {@link JdbcOutbox#enqueue}. Cleared after each test so a later test
 * class in the same surefire fork never sees this class's leftover {@code SecurityContext} (which
 * {@code OrganizationAwareDataSource} reads on every connection acquisition).
 */
public class JdbcOutboxTest extends CommunicationJdbcTestSupport {

    private static final Instant NOW = Instant.parse("2026-08-01T12:00:00Z");

    @Autowired
    private JdbcOutbox outbox;
    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @BeforeTransaction
    void authenticate() {

        var jwt = Jwt.withTokenValue("token").header("alg", "none").subject("alice-sub").claim("org", "acme").build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void clearSecurityContext() {

        SecurityContextHolder.clearContext();
    }

    /**
     * 2 recipients x 2 member-addressed channels (mail, in-app - see {@link JdbcOutboxInAppRowTest}) = 4 rows, not 2:
     * each recipient's second row is their in-app one.
     */
    @Test
    void enqueueingTheSameMessageTwiceInsertsOnce() {

        var message = OutboundMessageMother.any().type(MessageType.SESSION_WRAP_UP).subjectRef("42")
                .recipientUserIds(List.of("user-1", "user-2")).build();

        assertThat(outbox.enqueue(message)).isEqualTo(4);
        assertThat(outbox.enqueue(message)).isZero();
        assertThat(countRows()).isEqualTo(4);
    }

    /**
     * The scenario batching specifically has to get right: a single {@code enqueue} call whose recipients are a MIX of
     * already-there and new, in the SAME batch. A driver that reported one blanket count for the whole batch, rather
     * than one count per row, would pass {@link #enqueueingTheSameMessageTwiceInsertsOnce} (all-conflict) and
     * {@link #enqueueingDoesNotConflictAcrossDifferentSubjects} (all-new) while still being wrong here.
     *
     * <p>
     * Every count here is doubled from before the in-app channel: {@code first} writes 2 recipients x 2 channels = 4
     * rows; {@code second}'s one genuinely new recipient (user-3) also writes 2 rows (mail and in-app), while user-2's
     * mail AND in-app rows both already exist and both conflict - so {@code second} still inserts exactly 2, not 1.
     */
    @Test
    void enqueueingOverlappingRecipientsInsertsOnlyTheNewOnes() {

        var first = OutboundMessageMother.any().subjectRef("42").recipientUserIds(List.of("user-1", "user-2")).build();
        var second = OutboundMessageMother.any().subjectRef("42").recipientUserIds(List.of("user-2", "user-3")).build();

        assertThat(outbox.enqueue(first)).isEqualTo(4);
        assertThat(outbox.enqueue(second)).isEqualTo(2);
        assertThat(countRows()).isEqualTo(6);
    }

    /**
     * The bug with no symptom. {@code enqueue} resolves its organization from the security context, so switching the
     * JWT mid-method is enough to make the second call write {@code globex} - the connection's own
     * {@code app.current_org} stays {@code acme} for the whole method (see this class's javadoc), which is harmless
     * here because the connecting role bypasses RLS by ownership. What is left is the uniqueness question this test
     * exists to ask: a subject_ref such as a period key is identical in every tenant, so an idempotency key without
     * {@code organization_id} swallows the second organization's message silently.
     *
     * <p>
     * One recipient x 2 channels (mail, in-app) = 2 rows per organization, not 1: the second row each call writes is
     * the in-app one.
     */
    @Test
    void enqueuesTheSameSubjectSeparatelyForEachOrganization() {

        var message = OutboundMessageMother.any().subjectRef("2026-Q2").recipientUserIds(List.of("shared-user"))
                .build();

        assertThat(outbox.enqueue(message)).isEqualTo(2);

        var otherOrg = Jwt.withTokenValue("token").header("alg", "none").subject("alice-sub").claim("org", "globex")
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(otherOrg));

        assertThat(outbox.enqueue(message))
                .as("a person in two organizations must receive both organizations' messages").isEqualTo(2);
    }

    /**
     * The message assertion is the one the split newly makes necessary: {@code enqueue} now writes a message row before
     * it fans out, so a recipient-less call that fell through to that write would leave an orphan message behind - and
     * that orphan would then freeze the facts for whatever real enqueue came next under the same subject.
     */
    @Test
    void enqueueingWithNoRecipientsIsANoOpThatNeverTouchesTheDatabase() {

        var message = OutboundMessageMother.any().recipientUserIds(List.of()).build();

        assertThat(outbox.enqueue(message)).isZero();
        assertThat(countRows()).isZero();
        assertThat(countMessages()).isZero();
    }

    /**
     * One recipient x 2 channels (mail, in-app) per subject = 2 rows each, not 1: the second row each call writes is
     * the in-app one.
     */
    @Test
    void enqueueingDoesNotConflictAcrossDifferentSubjects() {

        var wrapUpFor42 = OutboundMessageMother.any().subjectRef("42").recipientUserIds(List.of("user-1")).build();
        var wrapUpFor43 = OutboundMessageMother.any().subjectRef("43").recipientUserIds(List.of("user-1")).build();

        assertThat(outbox.enqueue(wrapUpFor42)).isEqualTo(2);
        assertThat(outbox.enqueue(wrapUpFor43)).isEqualTo(2);
        assertThat(countRows()).isEqualTo(4);
    }

    /**
     * 2 rows, not 1: {@code claim} does not distinguish channels, so the recipient's in-app row (see
     * {@link JdbcOutboxInAppRowTest}) is claimed in the same batch as their mail row.
     */
    @Test
    void claimingMarksRowsSendingSoASecondClaimSeesNothing() {

        outbox.enqueue(OutboundMessageMother.any().recipientUserIds(List.of("user-1")).build());

        assertThat(claim(10)).hasSize(2);
        assertThat(claim(10)).isEmpty();
    }

    /**
     * Pins {@link JdbcOutbox#CLAIM_SQL}'s outer {@code ORDER BY c.id} directly - see that constant's own javadoc for
     * why the ordering was added and why nothing pinned it before. Several tests below rely on
     * {@code claim(...).getFirst()} (or {@link #mailRowsOf}) returning the lowest-id row deterministically; this is the
     * one test that would fail, rather than flake, if that ordering were ever removed.
     */
    @Test
    void claimReturnsRowsInAscendingIdOrder() {

        outbox.enqueue(OutboundMessageMother.any().recipientUserIds(List.of("user-1", "user-2", "user-3")).build());

        var claimed = claim(10);

        assertThat(claimed).hasSize(6).extracting(OutboxDelivery::getId).isSorted();
    }

    /**
     * Pins the write half of the {@code scheduledAt} round trip {@link OutboxDrain#rehydrateScheduledAt} depends on: an
     * {@link Instant} fact must be written as the ISO-8601 string form that method's {@code instanceof String} branch
     * expects, through this class's real Spring-configured {@code JsonMapper} (this test runs under
     * {@code CommunicationJdbcTestSupport}'s {@code JacksonAutoConfiguration} import, not a bare
     * {@code JsonMapper.builder().build()}). If {@code write-dates-as-timestamps} were ever enabled, this fact would be
     * written as a number instead and this assertion would fail here, at the write site, rather than leaving that
     * regression to surface only as the read-side rehydration's own defensive throw.
     */
    @Test
    void enqueueingWritesAnInstantFactAsAnIsoStringNotATimestampNumber() {

        var scheduledAt = Instant.parse("2026-08-03T22:49:00Z");
        var message = OutboundMessageMother.any().recipientUserIds(List.of("user-1"))
                .facts(Map.of("scheduledAt", scheduledAt)).build();

        outbox.enqueue(message);

        var claimed = claim(10).getFirst();
        // jsonb round-trips through Postgres's own canonical output format (a space after ':'), not the compact
        // form the JsonMapper wrote - a space this assertion must tolerate rather than pin.
        assertThat(claimed.getFacts()).contains("\"scheduledAt\": \"2026-08-03T22:49:00Z\"");
    }

    @Test
    void claimingReadsBackTheFactsAndOrganization() {

        outbox.enqueue(OutboundMessageMother.any().recipientUserIds(List.of("user-1")).build());

        var claimed = claim(10).getFirst();

        assertThat(claimed.getOrganizationId()).isEqualTo("acme");
        assertThat(claimed.getUserId()).isEqualTo("user-1");
        assertThat(claimed.getFacts()).contains("Frontend Dojo");
        assertThat(claimed.getStatus()).isEqualTo("SENDING");
        assertThat(claimed.getClaimedAt()).isEqualTo(NOW);
    }

    @Test
    void aFailedRowRecordsItsAttempt() {

        outbox.enqueue(OutboundMessageMother.any().recipientUserIds(List.of("user-1")).build());
        var claimed = claim(10).getFirst();

        markFailed(claimed.getId(), claimed.getOrganizationId(), NOW);

        assertThat(reload(claimed.getId())).satisfies(r -> {
            assertThat(r.getStatus()).isEqualTo("FAILED");
            assertThat(r.getAttempts()).isEqualTo(1);
        });
    }

    @Test
    void aSentRowRecordsWhenItWasSent() {

        outbox.enqueue(OutboundMessageMother.any().recipientUserIds(List.of("user-1")).build());
        var claimed = claim(10).getFirst();

        outbox.markSent(claimed.getId(), claimed.getOrganizationId(), NOW);

        assertThat(reload(claimed.getId())).satisfies(r -> {
            assertThat(r.getStatus()).isEqualTo("SENT");
            assertThat(r.getSentAt()).isEqualTo(NOW);
        });
    }

    @Test
    void aSuppressedRowRecordsItsReason() {

        outbox.enqueue(OutboundMessageMother.any().recipientUserIds(List.of("user-1")).build());
        var claimed = claim(10).getFirst();

        outbox.markSuppressed(claimed.getId(), claimed.getOrganizationId(), "member opted out", NOW);

        assertThat(reload(claimed.getId())).satisfies(r -> {
            assertThat(r.getStatus()).isEqualTo("SUPPRESSED");
            assertThat(r.getSuppressedReason()).isEqualTo("member opted out");
        });
    }

    @Test
    void markingIgnoresARowInAnotherOrganization() {

        outbox.enqueue(OutboundMessageMother.any().recipientUserIds(List.of("user-1")).build());
        var claimed = claim(10).getFirst();

        outbox.markSent(claimed.getId(), "some-other-org", NOW);

        assertThat(reload(claimed.getId()).getStatus()).isEqualTo("SENDING");
    }

    /**
     * Filtered to the mail row via {@link #mailRowsOf} - see that method's own javadoc for why a raw {@code claim}
     * result can no longer be asserted {@code singleElement()} for a single recipient.
     */
    @Test
    void aFailedRowUnderMaxAttemptsPastTheBackoffIsClaimedAgain() {

        outbox.enqueue(OutboundMessageMother.any().recipientUserIds(List.of("user-1")).build());
        var claimed = claim(10).getFirst();
        markFailed(claimed.getId(), claimed.getOrganizationId(), NOW);

        var retried = mailRowsOf(
                claim(10, NOW.plus(Duration.ofMinutes(6)), 5, Duration.ofMinutes(5), Duration.ofMinutes(5)));

        assertThat(retried).singleElement().satisfies(r -> {
            assertThat(r.getId()).isEqualTo(claimed.getId());
            assertThat(r.getStatus()).isEqualTo("SENDING");
            assertThat(r.getAttempts()).isEqualTo(1);
        });
    }

    @Test
    void aFailedRowInsideTheBackoffWindowIsNotClaimed() {

        outbox.enqueue(OutboundMessageMother.any().recipientUserIds(List.of("user-1")).build());
        var claimed = claim(10).getFirst();
        markFailed(claimed.getId(), claimed.getOrganizationId(), NOW);

        var tooSoon = claim(10, NOW.plus(Duration.ofMinutes(4)), 5, Duration.ofMinutes(5), Duration.ofMinutes(5));

        assertThat(tooSoon).isEmpty();
    }

    /**
     * Fails the same row {@code maxAttempts} times, each retry claimed only once its own backoff has passed, then
     * proves that once {@code attempts} reaches the maximum no backoff, however long, ever claims it again - and that
     * {@code markFailed} itself moved the row to the terminal {@code ABANDONED}, not left it {@code FAILED} forever
     * eating a permanent slot in {@code communication_delivery_claimable}. Filtered to the mail row via
     * {@link #mailRowsOf} throughout - see that method's own javadoc.
     */
    @Test
    void aFailedRowAtMaxAttemptsIsNeverClaimedAgainWhateverTheBackoff() {

        var maxAttempts = 5;
        var backoff = Duration.ofMinutes(5);
        outbox.enqueue(OutboundMessageMother.any().recipientUserIds(List.of("user-1")).build());

        var now = NOW;
        Long id = null;
        String organizationId = null;
        for (var attempt = 0; attempt < maxAttempts; attempt++) {
            var claimed = mailRowsOf(claim(10, now, maxAttempts, backoff, Duration.ofMinutes(5)));
            assertThat(claimed).hasSize(1);
            id = claimed.getFirst().getId();
            organizationId = claimed.getFirst().getOrganizationId();
            outbox.markFailed(id, organizationId, now, maxAttempts);
            now = now.plus(backoff).plusSeconds(1);
        }

        var afterMaxAttempts = mailRowsOf(
                claim(10, now.plus(Duration.ofDays(365)), maxAttempts, backoff, Duration.ofMinutes(5)));

        assertThat(afterMaxAttempts).isEmpty();
        assertThat(reload(id)).satisfies(r -> {
            assertThat(r.getStatus()).isEqualTo("ABANDONED");
            assertThat(r.getAttempts()).isEqualTo(maxAttempts);
        });
    }

    /** Filtered to the mail row via {@link #mailRowsOf} - see that method's own javadoc. */
    @Test
    void aSendingRowPastTheLeaseIsReclaimedWithAttemptsIncremented() {

        outbox.enqueue(OutboundMessageMother.any().recipientUserIds(List.of("user-1")).build());
        var claimed = claim(10).getFirst();

        var reclaimed = mailRowsOf(
                claim(10, NOW.plus(Duration.ofMinutes(6)), 5, Duration.ofMinutes(5), Duration.ofMinutes(5)));

        assertThat(reclaimed).singleElement().satisfies(r -> {
            assertThat(r.getId()).isEqualTo(claimed.getId());
            assertThat(r.getStatus()).isEqualTo("SENDING");
            assertThat(r.getAttempts()).isEqualTo(1);
        });
    }

    @Test
    void aSendingRowInsideTheLeaseIsLeftAlone() {

        outbox.enqueue(OutboundMessageMother.any().recipientUserIds(List.of("user-1")).build());
        claim(10);

        var tooSoon = claim(10, NOW.plus(Duration.ofMinutes(2)), 5, Duration.ofMinutes(5), Duration.ofMinutes(5));

        assertThat(tooSoon).isEmpty();
    }

    /**
     * A row reclaimed every lease period, never once reaching {@link JdbcOutbox#markFailed} - the crash-loop case,
     * dying deterministically right after being claimed - still hits the same {@code maxAttempts} ceiling as one that
     * fails normally, and becomes {@code ABANDONED} rather than being reclaimed a sixth time. Filtered to the mail row
     * via {@link #mailRowsOf} throughout - see that method's own javadoc.
     */
    @Test
    void aSendingRowPastTheLeaseAtMaxAttemptsIsAbandonedRatherThanReclaimed() {

        var maxAttempts = 5;
        var lease = Duration.ofMinutes(5);
        outbox.enqueue(OutboundMessageMother.any().recipientUserIds(List.of("user-1")).build());
        var id = claim(10).getFirst().getId();

        var now = NOW;
        for (var reclaim = 1; reclaim <= maxAttempts; reclaim++) {
            now = now.plus(lease).plusSeconds(1);
            var reclaimed = mailRowsOf(claim(10, now, maxAttempts, Duration.ofMinutes(5), lease));
            var expectedAttempts = reclaim;
            assertThat(reclaimed).singleElement().satisfies(r -> {
                assertThat(r.getId()).isEqualTo(id);
                assertThat(r.getStatus()).isEqualTo("SENDING");
                assertThat(r.getAttempts()).isEqualTo(expectedAttempts);
            });
        }

        var afterMaxAttempts = mailRowsOf(
                claim(10, now.plus(lease).plusSeconds(1), maxAttempts, Duration.ofMinutes(5), lease));

        assertThat(afterMaxAttempts).isEmpty();
        assertThat(reload(id)).satisfies(r -> {
            assertThat(r.getStatus()).isEqualTo("ABANDONED");
            assertThat(r.getAttempts()).isEqualTo(maxAttempts);
        });
    }

    @Test
    void releaseMovesAnUnprocessedSendingRowBackToPending() {

        outbox.enqueue(OutboundMessageMother.any().recipientUserIds(List.of("user-1")).build());
        var claimed = claim(10).getFirst();

        var released = outbox.release(List.of(claimed.getId()), claimed.getOrganizationId(), NOW.plusSeconds(1));

        assertThat(released).isEqualTo(1);
        assertThat(reload(claimed.getId()).getStatus()).isEqualTo("PENDING");
    }

    @Test
    void releaseLeavesARowNotInSendingAlone() {

        outbox.enqueue(OutboundMessageMother.any().recipientUserIds(List.of("user-1")).build());
        var claimed = claim(10).getFirst();
        outbox.markSent(claimed.getId(), claimed.getOrganizationId(), NOW);

        var released = outbox.release(List.of(claimed.getId()), claimed.getOrganizationId(), NOW.plusSeconds(1));

        assertThat(released).isZero();
        assertThat(reload(claimed.getId()).getStatus()).isEqualTo("SENT");
    }

    @Test
    void releaseWithNoIdsIsANoOp() {

        assertThat(outbox.release(List.of(), "acme", NOW)).isZero();
    }

    /**
     * One row per {@link OutboxStatus}, reached the same way {@link JdbcOutbox} itself reaches each one - enqueue,
     * claim, mark - under a single shared {@code subjectRef} so {@link JdbcOutbox#outcomeFor} counts all seven at once.
     * Each new row is enqueued and claimed on its own, before the next one is enqueued, so a later {@link #claim(int)}
     * call never sweeps up an earlier row still sitting {@code PENDING} - the very last row enqueued, for
     * {@code user-pending}, is deliberately never claimed at all, so it is still {@code PENDING} when the assertion
     * runs. {@link MessageOutcome#pending()} folds {@code PENDING}, {@code SENDING} and {@code FAILED} together (see
     * its own javadoc for why), so this one test proves all three of those statuses land in the same bucket, while
     * {@code SENT}, {@code SUPPRESSED} and {@code ABANDONED} each prove their own.
     *
     * <p>
     * {@code sent}, {@code suppressed} and {@code abandoned} are driven to three DIFFERENT counts (2, 1, 3) on purpose:
     * with all three at the same count, swapping any two of the three mappings inside {@link JdbcOutbox#outcomeFor}
     * would still leave this assertion green. Distinct counts mean a swap changes the expected object and the test goes
     * red - verified by hand: swapping the {@code sent}/{@code abandoned} mappings in {@code outcomeFor} turns this
     * test red, and restoring them turns it green again.
     *
     * <p>
     * Every {@code claim(10)} here also claims the recipient's in-app row alongside their mail row (see
     * {@link JdbcOutboxInAppRowTest}), so each site below reads {@code mailRowsOf(claim(10)).getFirst()} rather than a
     * raw {@code claim(10).getFirst()}: {@link JdbcOutbox#outcomeFor} counts {@code EMAIL} only, so marking the WRONG
     * channel's row would leave the mail row stuck {@code SENDING} (folded into {@code pending}) and silently corrupt
     * the counts this test pins. {@code mailRowsOf} states that intent directly rather than leaning on
     * {@link JdbcOutbox#CLAIM_SQL}'s {@code ORDER BY c.id} implicitly - see {@link #claimReturnsRowsInAscendingIdOrder}
     * for the direct pin of that ordering itself.
     */
    @Test
    void outcomeForCountsEveryRowByItsCoarsenedStatus() {

        var subjectRef = "outcome-subject";

        // SENDING: claimed, never marked again.
        enqueueUnderSubject(subjectRef, "user-sending");
        claim(10);

        // FAILED: claimed, then a failed attempt still under maxAttempts.
        enqueueUnderSubject(subjectRef, "user-failed");
        var failedRow = mailRowsOf(claim(10)).getFirst();
        outbox.markFailed(failedRow.getId(), failedRow.getOrganizationId(), NOW, 5);

        // SENT x2.
        enqueueUnderSubject(subjectRef, "user-sent-1");
        markSent(mailRowsOf(claim(10)).getFirst());
        enqueueUnderSubject(subjectRef, "user-sent-2");
        markSent(mailRowsOf(claim(10)).getFirst());

        // SUPPRESSED x1.
        enqueueUnderSubject(subjectRef, "user-suppressed");
        var suppressedRow = mailRowsOf(claim(10)).getFirst();
        outbox.markSuppressed(suppressedRow.getId(), suppressedRow.getOrganizationId(), "member opted out", NOW);

        // ABANDONED x3: a failure with maxAttempts of 1 abandons on the very first attempt.
        enqueueUnderSubject(subjectRef, "user-abandoned-1");
        abandon(mailRowsOf(claim(10)).getFirst());
        enqueueUnderSubject(subjectRef, "user-abandoned-2");
        abandon(mailRowsOf(claim(10)).getFirst());
        enqueueUnderSubject(subjectRef, "user-abandoned-3");
        abandon(mailRowsOf(claim(10)).getFirst());

        // PENDING: enqueued last, deliberately never claimed.
        enqueueUnderSubject(subjectRef, "user-pending");

        var outcome = outbox.outcomeFor(MessageType.SESSION_WRAP_UP, subjectRef);

        assertThat(outcome).isEqualTo(
                MessageOutcome.builder().pending(3).sent(2).suppressed(1).unreachable(0).abandoned(3).build());
    }

    /**
     * A {@code SUPPRESSED} row's {@code suppressed_reason} decides which of two buckets it lands in, not just its
     * status. A governance suppression ({@code MEMBER_OPTED_OUT}, here - the sweep deliberately withholding a message
     * the recipient opted out of) and a {@code NO_ADDRESS} suppression (identity resolved no usable address - a genuine
     * delivery failure) are driven to distinct counts (2 and 1) so a mapping that swapped {@code suppressed} and
     * {@code unreachable} inside {@link JdbcOutbox#outcomeFor} would flip both fields and this assertion would go red -
     * verified by hand: swapping the two branches turned this test red, restoring them turned it green again.
     *
     * <p>
     * Reads {@code mailRowsOf(claim(10)).getFirst()} at every claim site - see
     * {@link #outcomeForCountsEveryRowByItsCoarsenedStatus}'s own javadoc for why a raw {@code claim(10).getFirst()} is
     * not safe here either.
     */
    @Test
    void outcomeForSplitsSuppressedRowsByReason() {

        var subjectRef = "outcome-suppression-reason-subject";

        enqueueUnderSubject(subjectRef, "user-opted-out-1");
        var opted1 = mailRowsOf(claim(10)).getFirst();
        outbox.markSuppressed(opted1.getId(), opted1.getOrganizationId(), SuppressionReason.MEMBER_OPTED_OUT.name(),
                NOW);
        enqueueUnderSubject(subjectRef, "user-opted-out-2");
        var opted2 = mailRowsOf(claim(10)).getFirst();
        outbox.markSuppressed(opted2.getId(), opted2.getOrganizationId(), SuppressionReason.MEMBER_OPTED_OUT.name(),
                NOW);

        enqueueUnderSubject(subjectRef, "user-no-address");
        var noAddress = mailRowsOf(claim(10)).getFirst();
        outbox.markSuppressed(noAddress.getId(), noAddress.getOrganizationId(), SuppressionReason.NO_ADDRESS.name(),
                NOW);

        var outcome = outbox.outcomeFor(MessageType.SESSION_WRAP_UP, subjectRef);

        assertThat(outcome.suppressed()).isEqualTo(2);
        assertThat(outcome.unreachable()).isEqualTo(1);
    }

    @Test
    void outcomeForCountsAMailRowThatMetNoMailServerAsUnreachable() {

        var subjectRef = "outcome-no-mail-server-subject";

        enqueueUnderSubject(subjectRef, "user-opted-out");
        var opted = mailRowsOf(claim(10)).getFirst();
        outbox.markSuppressed(opted.getId(), opted.getOrganizationId(), SuppressionReason.MEMBER_OPTED_OUT.name(), NOW);
        enqueueUnderSubject(subjectRef, "user-no-server-1");
        var first = mailRowsOf(claim(10)).getFirst();
        outbox.markSuppressed(first.getId(), first.getOrganizationId(), SuppressionReason.MAIL_NOT_CONFIGURED.name(),
                NOW);
        enqueueUnderSubject(subjectRef, "user-no-server-2");
        var second = mailRowsOf(claim(10)).getFirst();
        outbox.markSuppressed(second.getId(), second.getOrganizationId(), SuppressionReason.MAIL_NOT_CONFIGURED.name(),
                NOW);

        var outcome = outbox.outcomeFor(MessageType.SESSION_WRAP_UP, subjectRef);

        assertThat(outcome.suppressed()).isEqualTo(1);
        assertThat(outcome.unreachable()).isEqualTo(2);
    }

    /** No writer leaves a reason out today; if one ever does, the card counts the row instead of failing to load. */
    @Test
    void outcomeForCountsASuppressedRowWithNoReasonAsSuppressed() {

        var subjectRef = "outcome-no-reason-subject";

        enqueueUnderSubject(subjectRef, "user-with-no-reason");
        var row = mailRowsOf(claim(10)).getFirst();
        outbox.markSuppressed(row.getId(), row.getOrganizationId(), null, NOW);

        var outcome = outbox.outcomeFor(MessageType.SESSION_WRAP_UP, subjectRef);

        assertThat(outcome.suppressed()).isEqualTo(1);
        assertThat(outcome.unreachable()).isZero();
    }

    private void markSent(OutboxDelivery row) {

        outbox.markSent(row.getId(), row.getOrganizationId(), NOW);
    }

    /** {@code maxAttempts} of 1 abandons on the very first failed attempt. */
    private void abandon(OutboxDelivery row) {

        outbox.markFailed(row.getId(), row.getOrganizationId(), NOW, 1);
    }

    @Test
    void outcomeForAnUnknownSubjectRefIsAllZerosNotEmptyOrNull() {

        outbox.enqueue(OutboundMessageMother.any().subjectRef("a-known-subject").build());
        claim(10);
        // Positive control: proves this method's own outcomeFor call genuinely counts a real row rather than
        // returning all zeros unconditionally, before asserting the unknown subject below is all zeros too.
        assertThat(outbox.outcomeFor(MessageType.SESSION_WRAP_UP, "a-known-subject").pending()).isEqualTo(1);

        var outcome = outbox.outcomeFor(MessageType.SESSION_WRAP_UP, "no-such-subject");

        assertThat(outcome).isNotNull().isEqualTo(
                MessageOutcome.builder().pending(0).sent(0).suppressed(0).unreachable(0).abandoned(0).build());
    }

    private void enqueueUnderSubject(String subjectRef, String userId) {

        outbox.enqueue(OutboundMessageMother.any().subjectRef(subjectRef).recipientUserIds(List.of(userId)).build());
    }

    @Test
    void findMessageForSubjectReturnsTheMessageOnceOneIsEnqueued() {

        outbox.enqueue(
                OutboundMessageMother.any().subjectRef("preview-subject").recipientUserIds(List.of("user-1")).build());

        var found = outbox.findMessageForSubject(MessageType.SESSION_WRAP_UP, "preview-subject");

        assertThat(found).isPresent();
        assertThat(found.get().getTemplate()).isEqualTo("session-wrap-up");
        assertThat(found.get().getFacts()).contains("Frontend Dojo");
    }

    @Test
    void findMessageForSubjectIsEmptyWhenNothingIsEnqueuedYet() {

        var found = outbox.findMessageForSubject(MessageType.SESSION_WRAP_UP, "no-such-subject");

        assertThat(found).isEmpty();
    }

    /**
     * The reason this lookup no longer needs a "which recipient's row did we happen to get" caveat: however many
     * recipients a sender fans out to, there is exactly one message row to preview from, so {@code LIMIT 1} is
     * returning the only candidate rather than an arbitrary one of many.
     */
    @Test
    void findMessageForSubjectReturnsOneMessageHoweverManyRecipientsThereAre() {

        outbox.enqueue(OutboundMessageMother.any().subjectRef("preview-subject")
                .recipientUserIds(List.of("user-1", "user-2", "user-3")).build());

        var found = outbox.findMessageForSubject(MessageType.SESSION_WRAP_UP, "preview-subject");

        assertThat(found).isPresent();
        assertThat(found.get().getSubjectRef()).isEqualTo("preview-subject");
        assertThat(countMessages()).isEqualTo(1);
    }

    /**
     * The message outlives its deliveries' statuses: a preview taken after the mail has already gone out must still
     * render the frozen facts that were actually mailed. Nothing in the lookup filters on delivery status, and this is
     * what keeps a future reader from re-introducing such a filter - a filter such as "only subjects with a
     * pending/sending delivery" would need EVERY delivery terminal to be caught here, not just one, since the
     * recipient's mail row and their in-app row (see {@link JdbcOutboxInAppRowTest}) are both claimed together and both
     * need marking, or the untouched one would silently keep such a filter passing.
     */
    @Test
    void findMessageForSubjectStillMatchesOnceEveryDeliveryIsTerminal() {

        enqueueUnderSubject("preview-subject", "user-1");
        var deliveries = claim(10);
        deliveries.forEach(this::markSent);

        var found = outbox.findMessageForSubject(MessageType.SESSION_WRAP_UP, "preview-subject");

        assertThat(deliveries).hasSize(2)
                .allSatisfy(d -> assertThat(reload(d.getId()).getStatus()).isEqualTo(OutboxStatus.SENT.name()));
        assertThat(found).isPresent();
        assertThat(found.get().getFacts()).contains("Frontend Dojo");
    }

    /**
     * The message row is the idempotency anchor, so the facts a second sender assembles never overwrite the ones the
     * first froze. Without that, two producers racing on one subject could mail two different versions of the same
     * logical message to different halves of the recipient list.
     */
    @Test
    void reEnqueueingTheSameSubjectKeepsTheFactsFrozenAtTheFirstEnqueue() {

        outbox.enqueue(OutboundMessageMother.any().recipientUserIds(List.of("user-1"))
                .facts(Map.of("sessionTitle", "Frontend Dojo")).build());
        outbox.enqueue(OutboundMessageMother.any().recipientUserIds(List.of("user-2"))
                .facts(Map.of("sessionTitle", "Renamed Later")).build());

        var stored = jdbc.queryForList(
                "SELECT facts::text AS facts FROM communication_message WHERE organization_id = 'acme'",
                new MapSqlParameterSource());

        assertThat(stored).singleElement().extracting(row -> row.get("facts").toString())
                .as("both recipients read one message, so its facts are the first sender's").asString()
                .contains("Frontend Dojo").doesNotContain("Renamed Later");
    }

    /**
     * The shape change this split exists for: the facts blob is written once, not copied once per employee, which is
     * what makes an organization-wide fan-out affordable at all. {@code countRows()} is 6, not 3: each of the 3
     * recipients gets a mail row AND an in-app row (see {@link JdbcOutboxInAppRowTest}).
     */
    @Test
    void aFanOutWritesOneMessageAndOneDeliveryPerRecipient() {

        outbox.enqueue(OutboundMessageMother.any().recipientUserIds(List.of("user-1", "user-2", "user-3")).build());

        assertThat(countMessages()).isEqualTo(1);
        assertThat(countRows()).isEqualTo(6);
    }

    private long countMessages() {

        var count = jdbc.queryForObject("SELECT count(*) FROM communication_message WHERE organization_id = 'acme'",
                Map.of(), Long.class);
        return count == null ? 0 : count;
    }

    /**
     * {@code enable_seqscan = off} is the standard way to prove an index CAN serve a query on a table too small for the
     * planner to ever prefer it on cost alone - the shared Testcontainers table here holds one row. Forcing the seq
     * scan off leaves the planner with a scan of {@code communication_delivery_claimable} as its only option when the
     * index genuinely matches the predicate; if the widened, three-branch {@code WHERE} ever stopped matching the
     * partial index (the exact regression a bound {@code :status} parameter would cause - see
     * {@link JdbcOutbox#claim}'s javadoc), the plan would fall back to a sequential scan even with seq scan disabled,
     * since that is Postgres's only remaining option, and this assertion would catch it.
     */
    @Test
    void theClaimQueryUsesTheReworkedPartialIndexNotASequentialScan() {

        outbox.enqueue(OutboundMessageMother.any().recipientUserIds(List.of("user-1")).build());
        jdbc.getJdbcTemplate().execute("SET LOCAL enable_seqscan = off");

        var retryBefore = Timestamp.from(NOW.minus(Duration.ofMinutes(5)));
        var leaseExpiredBefore = Timestamp.from(NOW.minus(Duration.ofMinutes(5)));
        var params = new MapSqlParameterSource().addValue("sending", OutboxStatus.SENDING.name())
                .addValue("abandoned", OutboxStatus.ABANDONED.name()).addValue("now", Timestamp.from(NOW))
                .addValue("maxAttempts", 5).addValue("retryBefore", retryBefore)
                .addValue("leaseExpiredBefore", leaseExpiredBefore).addValue("batch", 10);

        for (var mailHeld : List.of(false, true)) {
            var plan = String.join("\n", jdbc.query("EXPLAIN " + JdbcOutbox.CLAIM_SQL,
                    params.addValue("mailHeld", mailHeld), (rs, rowNum) -> rs.getString(1)));

            assertThat(plan).contains("communication_delivery_claimable").doesNotContain("Seq Scan");
        }
    }

    /**
     * The rest of a tick whose mail the server refused: the claim takes the recipient's in-app row and leaves the mail
     * row pending, untouched, for the next tick's claim to take with no attempt spent on it.
     */
    @Test
    void aClaimWithTheMailHeldLeavesTheMailRowForTheNextTick() {

        outbox.enqueue(OutboundMessageMother.any().recipientUserIds(List.of("user-whose-mail-is-held")).build());

        var held = outbox.claim(Outbox.ClaimQuery.builder().batchSize(10).now(NOW).maxAttempts(5)
                .retryBackoff(Duration.ofMinutes(5)).claimLease(Duration.ofMinutes(5)).mailHeld(true).build());

        assertThat(held).extracting(OutboxDelivery::getChannel).isNotEmpty()
                .doesNotContain(DeliveryChannel.EMAIL.name());
        assertThat(claim(10)).singleElement().satisfies(row -> {
            assertThat(row.getChannel()).isEqualTo(DeliveryChannel.EMAIL.name());
            assertThat(row.getAttempts()).isZero();
        });
    }

    /** Defaults {@code maxAttempts} to 5, the same default every {@code claim} helper below uses. */
    private void markFailed(Long id, String organizationId, Instant now) {

        outbox.markFailed(id, organizationId, now, 5);
    }

    private List<OutboxDelivery> claim(int batchSize) {

        return claim(batchSize, NOW, 5, Duration.ofMinutes(5), Duration.ofMinutes(5));
    }

    private List<OutboxDelivery> claim(int batchSize, Instant now, int maxAttempts, Duration retryBackoff,
            Duration claimLease) {

        return outbox.claim(Outbox.ClaimQuery.builder().batchSize(batchSize).now(now).maxAttempts(maxAttempts)
                .retryBackoff(retryBackoff).claimLease(claimLease).build());
    }

    /**
     * {@code claim} does not distinguish channels unless the mail is held, which none of these claims is, so a claim
     * batch for a single recipient now always also contains their in-app row (see {@link JdbcOutboxInAppRowTest}), not
     * just their mail row. The tests that use this method track ONE row through several claim/fail/reclaim cycles;
     * nothing in them ever marks the in-app row sent or failed, so left unfiltered it would independently churn through
     * its own SENDING-past-lease reclaim on the identical clock those tests advance, and corrupt a
     * {@code singleElement()}/{@code hasSize(1)} assertion built to track the mail row alone. Filtering here, rather
     * than asserting on the pair, is what keeps those tests pinned to the one row they are actually about.
     */
    private static List<OutboxDelivery> mailRowsOf(List<OutboxDelivery> claimed) {

        return claimed.stream().filter(row -> DeliveryChannel.EMAIL.name().equals(row.getChannel())).toList();
    }

    /**
     * Scoped to {@code acme} - the organization {@link #authenticate()} fixes for this whole class - rather than an
     * unscoped {@code count(*)}: this class's {@code @DataJdbcTest} transaction rolls back its own rows at the end of
     * each method, but the shared {@code jdbc:tc} container also hosts plain {@code @SpringBootTest} classes elsewhere
     * in this package ({@code SweepUnitScopeBoundaryTest}, {@code OutboxDrainTransactionIsolationTest}) that commit
     * real rows of their own, under their own organization ids, and never roll back. An unscoped count would see those
     * too, turning this method's result into a function of test run order rather than of what THIS test wrote.
     */
    private long countRows() {

        var count = jdbc.queryForObject("SELECT count(*) FROM communication_delivery WHERE organization_id = 'acme'",
                Map.of(), Long.class);
        return count == null ? 0 : count;
    }

    /** Joined the same way {@link JdbcOutbox#CLAIM_SQL} joins, so {@link JdbcOutbox#ROW_MAPPER} finds every column. */
    private OutboxDelivery reload(Long id) {

        return jdbc.queryForObject("""
                SELECT d.*, m.message_type, m.subject_ref, m.template, m.facts
                  FROM communication_delivery d
                  JOIN communication_message m ON m.id = d.message_id
                 WHERE d.id = :id
                """, Map.of("id", id), JdbcOutbox.ROW_MAPPER);
    }
}
