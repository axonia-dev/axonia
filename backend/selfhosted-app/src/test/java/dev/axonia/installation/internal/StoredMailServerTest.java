package dev.axonia.installation.internal;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;

import dev.axonia.SteppableClock;
import dev.axonia.communication.DeliveryOutcome;
import dev.axonia.communication.MailOutcome;
import dev.axonia.communication.MailRefusal;
import dev.axonia.communication.MailSecurity;
import dev.axonia.communication.MailServerSettings;
import dev.axonia.communication.MailServerState;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willAnswer;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/** {@code current()} runs no SQL on the caller's thread; only the start, the check and a save read. */
class StoredMailServerTest {

    private static final Instant NOW = Instant.parse("2026-10-04T10:00:00.123456Z");

    private static final Instant NOW_IN_MILLIS = Instant.parse("2026-10-04T10:00:00.123Z");

    private static final MailServerSettings HELD = MailValuesTest.COMPLETE.settings().orElseThrow();

    private final MailServerSettingsTable table = mock(MailServerSettingsTable.class);

    private final MailServerResolver resolver = mock(MailServerResolver.class);

    private final SettingsKeyRotation rotation = mock(SettingsKeyRotation.class);

    private final StoredMailServer mailServer = new StoredMailServer(resolver, table, rotation,
            new SteppableClock(NOW));

    private final ExecutorService threads = Executors.newVirtualThreadPerTaskExecutor();

    @BeforeEach
    void startedOnRevisionThree() {

        given(resolver.resolve()).willReturn(configuredAt(3, null));
        mailServer.afterSingletonsInstantiated();
        clearInvocations(resolver);
    }

    @AfterEach
    void stopTheThreads() {

        threads.shutdownNow();
    }

    @Test
    void answersFromWhatItReadAtTheStartAndNeverQueriesOnTheCallersThread() {

        // when
        var first = mailServer.current();
        var actual = mailServer.current();

        // then
        assertThat(first).isInstanceOf(MailServerState.Configured.class);
        assertThat(actual).isEqualTo(first);
        verifyNoInteractions(resolver, table);
    }

    @Test
    void theRevisionCheckReadsTheSettingsAgainOnlyOnceTheRevisionMoved() {

        // given
        given(resolver.resolve()).willReturn(notConfiguredAt(4));
        given(table.watermark()).willReturn(watermark(3, null), watermark(4, null));

        // when
        mailServer.checkRevision();
        var unchanged = mailServer.current();
        mailServer.checkRevision();
        var actual = mailServer.current();

        // then
        assertThat(unchanged).isInstanceOf(MailServerState.Configured.class);
        assertThat(actual).isInstanceOf(MailServerState.NotConfigured.class);
        verify(resolver, times(1)).resolve();
    }

    @Test
    void aRevisionCheckThatCannotReachTheDatabaseKeepsTheLastAnswer() {

        // given
        given(table.watermark()).willThrow(new DataAccessResourceFailureException("database unreachable"));

        // when
        mailServer.checkRevision();

        // then
        assertThat(mailServer.current()).isInstanceOf(MailServerState.Configured.class);
        verify(resolver, never()).resolve();
    }

    /** A save here published revision 5 while the check, begun on 3, was reading revision 4. */
    @Test
    void aReadOfAnOlderRevisionNeverReplacesANewerOne() {

        // given
        var reads = new AtomicInteger();
        given(resolver.resolve()).willAnswer(invocation -> {
            if (reads.getAndIncrement() == 0) {
                mailServer.reload();
                return configuredAt(4, null);
            }
            return notConfiguredAt(5);
        });
        given(table.watermark()).willReturn(watermark(4, null));

        // when
        mailServer.checkRevision();

        // then
        assertThat(mailServer.current()).isInstanceOf(MailServerState.NotConfigured.class);
        verify(resolver, times(2)).resolve();
    }

    /**
     * A save's reload read revision 4 while a check published another instance's revision 5: revision 5 is held, and so
     * is its delivery state rather than revision 4's.
     */
    @Test
    void aReloadThatReadAnOlderRevisionThanACheckPublishedKeepsTheNewer() {

        // given
        given(table.watermark()).willReturn(watermark(5, null));
        given(resolver.resolve()).willReturn(configuredAt(5, null), configuredAt(4, "timeout"));
        mailServer.checkRevision();

        // when
        mailServer.reload();

        // then
        mailServer.recordOutcome(aboutTheHeldSettings(MailOutcome.accepted()));
        mailServer.recordOutcome(aboutTheHeldSettings(MailOutcome.refused(MailRefusal.TIMEOUT)));
        verify(table, never()).clearFailure(anyLong());
        verify(table).recordFailure("timeout", NOW_IN_MILLIS, 5);
    }

    /** A database restored while the instance runs lowers the revision for good. */
    @Test
    void aDatabaseRestoredToAnOlderRevisionIsTakenAtTheNextCheck() {

        // given
        given(resolver.resolve()).willReturn(notConfiguredAt(5), configuredAt(2, null));
        mailServer.reload();
        given(table.watermark()).willReturn(watermark(2, null));

        // when
        mailServer.checkRevision();

        // then
        assertThat(mailServer.current()).isInstanceOf(MailServerState.Configured.class);
    }

    /**
     * Every check, moved revision or not, hands the rotation the key identifiers it read, so the row settles under the
     * key of the instances that remain.
     */
    @Test
    void handsEveryCheckTheKeysItReadToTheRotation() {

        // given
        var unchanged = new MailServerSettingsTable.Watermark(3, null, "e4caaa0c", null);
        var moved = new MailServerSettingsTable.Watermark(4, null, "e4caaa0c", "e4caaa0c");
        given(resolver.resolve()).willReturn(configuredAt(4, null));
        given(table.watermark()).willReturn(unchanged, moved);

        // when
        mailServer.checkRevision();
        mailServer.checkRevision();

        // then
        verify(rotation).sealUnderTheCurrentKey(unchanged);
        verify(rotation).sealUnderTheCurrentKey(moved);
    }

    /** Settings delivery would refuse fail one read; delivery keeps what it had. */
    @Test
    void settingsDeliveryWouldRefuseKeepTheLastStateRead() {

        // given
        given(resolver.resolve()).willReturn(refusedAt(4));

        // when
        mailServer.reload();

        // then
        assertThat(mailServer.current()).isInstanceOf(MailServerState.Configured.class);
    }

    @Test
    void settingsDeliveryWouldRefuseAtTheStartAreNotConfigured() {

        // given
        given(resolver.resolve()).willReturn(refusedAt(1));
        var starting = new StoredMailServer(resolver, table, rotation, new SteppableClock(NOW));

        // when
        starting.afterSingletonsInstantiated();

        // then
        assertThat(starting.current()).isInstanceOf(MailServerState.NotConfigured.class);
    }

    @Test
    void aReloadNeverWaitsOnADeliveryStateBeingWritten() throws Exception {

        // given
        var writing = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        willAnswer(invocation -> {
            writing.countDown();
            return release.await(10, TimeUnit.SECONDS);
        }).given(table).recordFailure(anyString(), any(), anyLong());
        given(resolver.resolve()).willReturn(notConfiguredAt(4));
        var recording = CompletableFuture.runAsync(
                () -> mailServer.recordOutcome(aboutTheHeldSettings(MailOutcome.refused(MailRefusal.TIMEOUT))),
                threads);
        assertThat(writing.await(5, TimeUnit.SECONDS)).isTrue();

        // when
        var reloaded = CompletableFuture.runAsync(mailServer::reload, threads);

        // then
        reloaded.get(5, TimeUnit.SECONDS);
        assertThat(mailServer.current()).isInstanceOf(MailServerState.NotConfigured.class);
        release.countDown();
        recording.get(5, TimeUnit.SECONDS);
    }

    @Test
    void readsTheSettingsAgainAtOnceWhenTheyWereSavedHere() {

        // given
        given(resolver.resolve()).willReturn(notConfiguredAt(4));

        // when
        mailServer.reload();
        var actual = mailServer.current();

        // then
        assertThat(actual).isInstanceOf(MailServerState.NotConfigured.class);
        verify(resolver, times(1)).resolve();
    }

    @Test
    void recordsARefusalOnceAndItsEndOnce() {

        // given
        var refused = aboutTheHeldSettings(MailOutcome.refused(MailRefusal.AUTHENTICATION_FAILED,
                Optional.of("535 5.7.8 Authentication credentials invalid")));
        var accepted = aboutTheHeldSettings(MailOutcome.accepted());

        // when
        mailServer.recordOutcome(refused);
        mailServer.recordOutcome(refused);
        mailServer.recordOutcome(accepted);
        mailServer.recordOutcome(accepted);

        // then
        verify(table, times(1)).recordFailure("authentication_failed", NOW_IN_MILLIS, 3);
        verify(table, times(1)).clearFailure(3);
        verify(resolver, never()).resolve();
    }

    /**
     * A batch that connected with the password a save has since replaced is refused after the save: the refusal is
     * about the old values, so the new ones are not marked failing, nor is an acceptance of the old ones taken for the
     * new ones sending.
     */
    @Test
    void anOutcomeAboutSettingsReplacedSinceSaysNothingAboutTheHeldOnes() {

        // given
        given(resolver.resolve()).willReturn(configuredAt(4, "authentication_failed"));
        mailServer.reload();
        var replaced = MailValuesTest.COMPLETE.toBuilder().password("the-old-password").build().settings()
                .orElseThrow();

        // when
        mailServer.recordOutcome(DeliveryOutcome.of(replaced, MailOutcome.refused(MailRefusal.TIMEOUT)));
        mailServer.recordOutcome(DeliveryOutcome.of(replaced, MailOutcome.accepted()));

        // then
        verify(table, never()).recordFailure(anyString(), any(), anyLong());
        verify(table, never()).clearFailure(anyLong());
    }

    /** A batch that connected before a Clear: no settings are held for its outcome to be about. */
    @Test
    void anOutcomeWhileNoMailServerIsHeldSaysNothing() {

        // given
        given(resolver.resolve()).willReturn(notConfiguredAt(4));
        mailServer.reload();

        // when
        mailServer.recordOutcome(aboutTheHeldSettings(MailOutcome.refused(MailRefusal.TIMEOUT)));

        // then
        verify(table, never()).recordFailure(anyString(), any(), anyLong());
    }

    /** Not even a failure recorded before, which an acceptance would clear. */
    @Test
    void aRecipientRefusalSaysNothingAboutTheServer() {

        // given
        given(resolver.resolve()).willReturn(configuredAt(4, "timeout"));
        mailServer.reload();

        // when
        mailServer.recordOutcome(aboutTheHeldSettings(MailOutcome.refused(MailRefusal.RECIPIENT_REFUSED,
                Optional.of("550 5.1.0 Requested action not taken: mailbox unavailable"))));

        // then
        verify(table, never()).recordFailure(anyString(), any(), anyLong());
        verify(table, never()).clearFailure(anyLong());
    }

    @Test
    void clearsAFailureStoredBeforeOnceItSendsAgain() {

        // given
        given(resolver.resolve()).willReturn(configuredAt(4, "connection_failed"));
        mailServer.reload();

        // when
        mailServer.recordOutcome(aboutTheHeldSettings(MailOutcome.accepted()));

        // then
        verify(table).clearFailure(4);
    }

    @Test
    void clearsAFailureAnotherInstanceRecordedOnceItSendsAgain() {

        // given
        given(table.watermark()).willReturn(watermark(3, "connection_failed"));
        mailServer.checkRevision();

        // when
        mailServer.recordOutcome(aboutTheHeldSettings(MailOutcome.accepted()));

        // then
        verify(table).clearFailure(3);
    }

    @Test
    void aStateItCannotWriteNeverStopsDeliveryAndIsTriedAgain() {

        // given
        willThrow(new DataAccessResourceFailureException("database unreachable")).given(table)
                .recordFailure(anyString(), any(), anyLong());
        var refused = aboutTheHeldSettings(MailOutcome.refused(MailRefusal.TIMEOUT));

        // when
        mailServer.recordOutcome(refused);
        mailServer.recordOutcome(refused);

        // then
        verify(table, times(2)).recordFailure("timeout", NOW_IN_MILLIS, 3);
    }

    private static DeliveryOutcome aboutTheHeldSettings(MailOutcome outcome) {

        return DeliveryOutcome.of(HELD, outcome);
    }

    private static ResolvedMailServer configuredAt(long revision, String lastFailureReason) {

        var stored = MailValuesTest.COMPLETE;
        return new ResolvedMailServer(MailValues.NONE, stored, false,
                MailServerRow.builder().host(stored.host()).port(stored.port()).security(MailSecurity.STARTTLS)
                        .fromAddress(stored.fromAddress()).revision(revision).lastFailureReason(lastFailureReason)
                        .build());
    }

    private static MailServerSettingsTable.Watermark watermark(long revision, String lastFailureReason) {

        return new MailServerSettingsTable.Watermark(revision, lastFailureReason, null, null);
    }

    /** A host with an ideographic space, which only a direct edit of the row could store. */
    private static ResolvedMailServer refusedAt(long revision) {

        var stored = MailValuesTest.COMPLETE.toBuilder().host("smtp\u3000acme.test").build();
        return new ResolvedMailServer(MailValues.NONE, stored, false,
                MailServerRow.builder().revision(revision).build());
    }

    private static ResolvedMailServer notConfiguredAt(long revision) {

        return new ResolvedMailServer(MailValues.NONE, MailValues.NONE, false,
                MailServerRow.builder().revision(revision).build());
    }
}
