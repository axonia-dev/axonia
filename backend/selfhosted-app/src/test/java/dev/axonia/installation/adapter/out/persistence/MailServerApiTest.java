package dev.axonia.installation.adapter.out.persistence;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import dev.axonia.Mailpit;
import dev.axonia.communication.api.DeliveryOutcome;
import dev.axonia.communication.api.MailOutcome;
import dev.axonia.communication.api.MailRefusal;
import dev.axonia.communication.api.MailServerState;
import dev.axonia.installation.adapter.out.communication.StoredMailServer;
import dev.axonia.installation.application.SettingsCipher;
import dev.axonia.installation.domain.SecretField;
import dev.axonia.installation.domain.TestKeys;
import dev.axonia.selfhosted.MockOidcIssuer;
import dev.axonia.selfhosted.SelfHostedDatabase;
import dev.axonia.selfhosted.SelfHostedRequests;
import dev.axonia.selfhosted.SelfHostedSpringBootTest;
import dev.axonia.selfhosted.SelfHostedTestInfrastructure;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * The mail server API through the edge, with real tokens, against Mailpit. Every test signs in as people of its own.
 */
@SelfHostedSpringBootTest
class MailServerApiTest {

    private static final String PATH = "/axonia/mail-server";

    private static final String PASSWORD = "never-in-a-response-9f2c";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private MockOidcIssuer issuer;

    @Autowired
    private JsonMapper json;

    @Autowired
    private StoredMailServer mailServer;

    private final ExecutorService threads = Executors.newVirtualThreadPerTaskExecutor();

    @BeforeEach
    void forgetTheMailServer() throws SQLException {

        MailServerRows.reset();
        mailServer.reload();
    }

    @AfterEach
    void stopTheThreads() {

        threads.shutdownNow();
    }

    @Test
    void anOwnerReadsAMailServerNobodySetUp() {

        // when
        var actual = get(owner(), PATH);

        // then
        assertThat(actual).hasStatusOk().bodyJson().extractingPath("$.state").isEqualTo("not_configured");
        assertThat(actual).bodyJson().hasPath("$.host.value").hasPath("$.host.lockedBy");
        assertThat(actual).bodyJson().extractingPath("$.host.value").isNull();
        assertThat(actual).bodyJson().extractingPath("$.password.set").isEqualTo(false);
        assertThat(actual).bodyJson().extractingPath("$.password.readable").isEqualTo(true);
        assertThat(actual).bodyJson().extractingPath("$.lastFailure").isNull();
        assertThat(actual).bodyJson().extractingPath("$.lastChange").isNull();
        assertThat(get(member(), PATH + "/status")).bodyJson().extractingPath("$.configured").isEqualTo(false);
    }

    @Test
    void anOwnerTestsSavesAndReadsWhoChangedWhat() {

        // given
        var owner = owner();
        var recipient = unique("ana") + "@acme.test";

        // when
        var tested = test(owner, openRelay(), recipient);
        var saved = save(owner, openRelay(), tokenOf(tested), revision(owner));

        // then
        assertThat(tested).hasStatusOk().bodyJson().extractingPath("$.accepted").isEqualTo(true);
        assertThat(tested).bodyJson().extractingPath("$.host").isEqualTo(Mailpit.open().host());
        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(Mailpit.open().messages())
                .filteredOn(message -> message.to().contains(recipient)).singleElement().satisfies(message -> {
                    assertThat(message.subject()).isEqualTo("Test mail from axonia");
                    assertThat(message.from()).isEqualTo("axonia@acme.test");
                    assertThat(message.fromName()).isEqualTo("Acme Communities");
                }));
        assertThat(saved).hasStatusOk().bodyJson().extractingPath("$.state").isEqualTo("configured");
        assertThat(saved).bodyJson().extractingPath("$.lastChange.actorName").isEqualTo("Ana Ruiz");
        var changes = get(owner, PATH + "/changes");
        assertThat(changes).hasStatusOk().bodyJson().extractingPath("$.page.totalElements").isEqualTo(1);
        assertThat(changes).bodyJson().hasPath("$.content[0].changes[0].from");
        assertThat(changes).bodyJson().extractingPath("$.content[0].changes[0].field").isEqualTo("host");
        assertThat(changes).bodyJson().extractingPath("$.content[0].changes[0].kind").isEqualTo("set");
        assertThat(changes).bodyJson().extractingPath("$.content[0].changes[0].from").isNull();
        assertThat(changes).bodyJson().extractingPath("$.content[0].changes[0].to").isEqualTo(Mailpit.open().host());
        assertThat(get(member(), PATH + "/status")).bodyJson().extractingPath("$.configured").isEqualTo(true);
    }

    @Test
    void aSaveIsUntestedWithoutATokenOfTheseValuesThisRevisionAndThisPerson() {

        // given
        var owner = owner();
        var token = tokenOf(test(owner, openRelay(), unique("ana") + "@acme.test"));
        var revision = revision(owner);
        var otherValues = openRelay();
        otherValues.put("fromName", "Somebody Else");

        // then
        assertThat(save(owner, otherValues, token, revision)).hasStatus(HttpStatus.UNPROCESSABLE_ENTITY).bodyJson()
                .extractingPath("$.code").isEqualTo("untested");
        assertThat(save(owner(), openRelay(), token, revision)).hasStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(save(owner, openRelay(), "v1.1.forged", revision)).hasStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(save(owner, openRelay(), token, revision)).hasStatusOk();
        assertThat(save(owner, openRelay(), token, revision(owner))).as("replayed on the revision its save moved")
                .hasStatus(HttpStatus.UNPROCESSABLE_ENTITY).bodyJson().extractingPath("$.code").isEqualTo("untested");
    }

    /** A save that changes no stored value records nothing in the history. */
    @Test
    void aSaveOfTheValuesAlreadyStoredRecordsNoChange() {

        // given
        var owner = owner();
        save(owner, openRelay(), tokenOf(test(owner, openRelay(), unique("ana") + "@acme.test")), revision(owner));

        // when
        var again = save(owner, openRelay(), tokenOf(test(owner, openRelay(), unique("ana") + "@acme.test")),
                revision(owner));

        // then
        assertThat(again).hasStatusOk();
        assertThat(get(owner, PATH + "/changes")).bodyJson().extractingPath("$.page.totalElements").isEqualTo(1);
    }

    @Test
    void aSaveOnARevisionAnotherSaveMovedIsAConflict() {

        // given
        var owner = owner();
        var token = tokenOf(test(owner, openRelay(), unique("ana") + "@acme.test"));

        // when
        var actual = save(owner, openRelay(), token, revision(owner) - 1);
        var forged = save(owner, openRelay(), "v1.bff04208.1.forged", revision(owner) - 1);

        // then
        assertThat(actual).hasStatus(HttpStatus.CONFLICT).bodyJson().extractingPath("$.code")
                .isEqualTo("stale_revision");
        assertThat(forged).as("the revision is checked before the token").hasStatus(HttpStatus.CONFLICT);
    }

    /**
     * The revision moved between the save's read and its write, as when two Owners save at once. The row is held from
     * outside until the save waits on it, then moved and released.
     */
    @Test
    void aSaveWhoseRowMovedBeforeItWroteIsAConflictAndRecordsNothing() throws Exception {

        // given
        var owner = owner();
        var token = tokenOf(test(owner, openRelay(), unique("ana") + "@acme.test"));
        var revision = revision(owner);
        var database = SelfHostedDatabase.shared();
        try (var holder = DriverManager.getConnection(database.getJdbcUrl(), database.getUsername(),
                database.getPassword()); var statement = holder.createStatement()) {
            holder.setAutoCommit(false);
            statement.execute("SELECT revision FROM mail_server_settings FOR UPDATE");
            var saving = CompletableFuture.supplyAsync(() -> save(owner, openRelay(), token, revision), threads);
            awaitUpdatesWaitingOnTheRow(1);
            statement.execute("UPDATE mail_server_settings SET revision = revision + 1");

            // when
            holder.commit();

            // then
            assertThat(saving.get(5, TimeUnit.SECONDS)).hasStatus(HttpStatus.CONFLICT).bodyJson()
                    .extractingPath("$.code").isEqualTo("stale_revision");
        }
        assertThat(get(owner, PATH + "/changes")).bodyJson().extractingPath("$.page.totalElements").isEqualTo(0);
    }

    /** A test or a save never sends the stored password to a server the request names instead. */
    @Test
    void aStoredPasswordNeverFollowsTheServerToAnotherHost() throws SQLException {

        // given
        MailServerRows.set("host = 'smtp.stored.test', port = 587, security = 'STARTTLS', username = 'relay',"
                + " from_address = 'axonia@acme.test', password_secret = '"
                + new SettingsCipher(TestKeys.current()).seal(SecretField.MAIL_PASSWORD, PASSWORD) + "'");
        mailServer.reload();
        var owner = owner();
        var recipient = unique("ana") + "@acme.test";
        var elsewhere = openRelay();
        elsewhere.put("username", "relay");

        // when
        var tested = test(owner, elsewhere, recipient);
        var saved = save(owner, elsewhere, "v1.bff04208.1.forged", revision(owner));

        // then
        assertThat(List.of(tested, saved)).allSatisfy(result -> {
            assertThat(result).hasStatus(HttpStatus.BAD_REQUEST).bodyJson().extractingPath("$.code")
                    .isEqualTo("field_invalid");
            assertThat(result).bodyJson().extractingPath("$.field").isEqualTo("values.password");
            assertThat(result).bodyJson().extractingPath("$.detail")
                    .isEqualTo("values.password: enter the password again for another server.");
        });
        assertThat(Mailpit.open().messages()).noneMatch(message -> message.to().contains(recipient));
    }

    @Test
    void aWrongPasswordIsAnAuthenticationFailureWithTheServersReply() {

        // when
        var actual = test(owner(), authenticatingRelay("wrong-password"), unique("ana") + "@acme.test");

        // then
        assertThat(actual).hasStatus(HttpStatus.UNPROCESSABLE_ENTITY).bodyJson().extractingPath("$.code")
                .isEqualTo("authentication_failed");
        assertThat(actual).bodyJson().extractingPath("$.detail").asString().startsWith("535");
    }

    @Test
    void aRecipientTheServerRefusesIsRecipientRefused() {

        // when
        var actual = test(owner(), authenticatingRelay(Mailpit.AUTH_PASSWORD), unique("ana") + "@elsewhere.test");

        // then
        assertThat(actual).hasStatus(HttpStatus.UNPROCESSABLE_ENTITY).bodyJson().extractingPath("$.code")
                .isEqualTo("recipient_refused");
        assertThat(actual).bodyJson().extractingPath("$.detail").asString().startsWith("550");
    }

    @Test
    void aPlainSessionWhereTheServerRequiresStartTlsIsTlsRequired() {

        // when
        var actual = test(owner(), relayAt(Mailpit.requiringStartTls(), "NONE"), unique("ana") + "@acme.test");

        // then
        assertThat(actual).hasStatus(HttpStatus.UNPROCESSABLE_ENTITY).bodyJson().extractingPath("$.code")
                .isEqualTo("tls_required");
        assertThat(actual).bodyJson().extractingPath("$.detail").asString().contains("STARTTLS");
    }

    @Test
    void aCertificateTheInstallationDoesNotTrustIsTlsFailed() {

        // when
        var actual = test(owner(), relayAt(Mailpit.requiringStartTls(), "STARTTLS"), unique("ana") + "@acme.test");

        // then
        assertThat(actual).hasStatus(HttpStatus.UNPROCESSABLE_ENTITY).bodyJson().extractingPath("$.code")
                .isEqualTo("tls_failed");
    }

    @Test
    void anUnreachableHostIsConnectionFailedWithNoReply() throws IOException {

        // when
        var actual = test(owner(), unreachableRelay(), unique("ana") + "@acme.test");

        // then
        assertThat(actual).hasStatus(HttpStatus.UNPROCESSABLE_ENTITY).bodyJson().extractingPath("$.code")
                .isEqualTo("connection_failed");
        assertThat(actual).bodyJson().doesNotHavePath("$.detail");
    }

    /** No login needs no stored password, readable or not. */
    @Test
    void aRelayWithNoLoginIsTestedAndSavedWhileTheStoredPasswordIsUnreadableAndClearsIt() throws SQLException {

        // given
        storeAPasswordNoKeyOpens();
        var owner = owner();
        assertThat(get(owner, PATH)).bodyJson().extractingPath("$.state").isEqualTo("secret_unreadable");

        // when
        var tested = test(owner, openRelay(), unique("ana") + "@acme.test");
        var saved = save(owner, openRelay(), tokenOf(tested), revision(owner));

        // then
        assertThat(tested).hasStatusOk();
        assertThat(saved).hasStatusOk().bodyJson().extractingPath("$.state").isEqualTo("configured");
        assertThat(saved).bodyJson().extractingPath("$.password.set").isEqualTo(false);
        assertThat(SelfHostedDatabase.countAsOwner(
                "SELECT count(*) FROM mail_server_settings WHERE password_secret IS NULL AND username IS NULL"))
                .isOne();
        assertThat(get(owner, PATH + "/changes")).bodyJson()
                .extractingPath("$.content[0].changes[?(@.field == 'password')].kind").asArray()
                .containsExactly("cleared");
    }

    @Test
    void aLoginWithAnUnreadableStoredPasswordMustBeEnteredAgain() throws SQLException {

        // given
        storeAPasswordNoKeyOpens();
        var owner = owner();
        var login = openRelay();
        login.put("username", "relay");

        // when
        var kept = test(owner, login, unique("ana") + "@acme.test");
        login.put("password", PASSWORD);
        var entered = test(owner, login, unique("ana") + "@acme.test");

        // then
        assertThat(kept).hasStatus(HttpStatus.BAD_REQUEST).bodyJson().extractingPath("$.field")
                .isEqualTo("values.password");
        assertThat(entered).hasStatusOk();
    }

    @Test
    void noResponseAndNoStoredRowCarriesThePassword() throws SQLException {

        // given
        var owner = owner();
        var login = openRelay();
        login.put("username", "relay");
        login.put("password", PASSWORD);

        // when
        var tested = test(owner, login, unique("ana") + "@acme.test");
        var saved = save(owner, login, tokenOf(tested), revision(owner));
        var read = get(owner, PATH);
        var changes = get(owner, PATH + "/changes");

        // then
        assertThat(saved).hasStatusOk().bodyJson().extractingPath("$.password.set").isEqualTo(true);
        assertThat(List.of(tested, saved, read, changes))
                .allSatisfy(result -> assertThat(result.getResponse().getContentAsString()).doesNotContain(PASSWORD));
        assertThat(SelfHostedDatabase.countAsOwner(
                "SELECT count(*) FROM installation_settings_change" + " WHERE changes::text LIKE '%" + PASSWORD + "%'"))
                .isZero();
        assertThat(SelfHostedDatabase.countAsOwner("SELECT count(*) FROM mail_server_settings"
                + " WHERE password_secret LIKE 'v1:%' AND password_secret NOT LIKE '%" + PASSWORD + "%'")).isOne();
    }

    @Test
    void theEleventhTestByOnePersonInTenMinutesIsRefused() throws IOException {

        // given
        var owner = owner();
        var relay = unreachableRelay();
        for (var attempt = 0; attempt < 10; attempt++) {
            assertThat(test(owner, relay, unique("ana") + "@acme.test")).hasStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        }

        // when
        var actual = test(owner, relay, unique("ana") + "@acme.test");

        // then
        assertThat(actual).hasStatus(HttpStatus.TOO_MANY_REQUESTS).bodyJson().extractingPath("$.code")
                .isEqualTo("mail_test_rate_limited");
        assertThat(actual.getResponse().getHeader(HttpHeaders.RETRY_AFTER)).isNotBlank();
        assertThat(test(owner(), relay, unique("bob") + "@acme.test")).hasStatus(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void refusesMalformedAndMissingValuesNamingTheField() {

        // given
        var owner = owner();
        var badPort = openRelay();
        badPort.put("port", 0);
        var noHost = openRelay();
        noHost.put("host", " ");
        var ideographicSpace = openRelay();
        ideographicSpace.put("host", "smtp\u3000acme.test");
        var nextLine = openRelay();
        nextLine.put("username", "re\u0085lay");
        nextLine.put("password", PASSWORD);

        // then
        assertThat(test(owner, badPort, "ana@acme.test")).hasStatus(HttpStatus.BAD_REQUEST).bodyJson()
                .extractingPath("$.field").isEqualTo("values.port");
        assertThat(test(owner, noHost, "ana@acme.test")).hasStatus(HttpStatus.BAD_REQUEST).bodyJson()
                .extractingPath("$.detail").isEqualTo("values.host: required.");
        assertThat(test(owner, ideographicSpace, "ana@acme.test")).hasStatus(HttpStatus.BAD_REQUEST).bodyJson()
                .extractingPath("$.field").isEqualTo("values.host");
        assertThat(test(owner, nextLine, "ana@acme.test")).hasStatus(HttpStatus.BAD_REQUEST).bodyJson()
                .extractingPath("$.field").isEqualTo("values.username");
        assertThat(test(owner, openRelay(), "Ana <ana@acme.test>")).hasStatus(HttpStatus.BAD_REQUEST).bodyJson()
                .extractingPath("$.field").isEqualTo("recipient");
        assertThat(get(owner, PATH + "/changes?sort=occurredAt")).hasStatus(HttpStatus.BAD_REQUEST).bodyJson()
                .extractingPath("$.code").isEqualTo("unsupported_sort");
        assertThat(mvc.delete().uri(PATH).with(SelfHostedRequests.signedIn(owner)).exchange())
                .hasStatus(HttpStatus.BAD_REQUEST).bodyJson().extractingPath("$.field").isEqualTo("baseRevision");
    }

    @Test
    void clearingRemovesWhatWasStoredOnceAndRecordsIt() {

        // given
        var owner = owner();
        save(owner, openRelay(), tokenOf(test(owner, openRelay(), unique("ana") + "@acme.test")), revision(owner));

        // when
        var cleared = delete(owner, revision(owner));
        var statusRightAfter = get(member(), PATH + "/status");
        var clearedAgain = delete(owner, revision(owner));

        // then
        assertThat(cleared).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(statusRightAfter).as("read again at once, not at the next check").bodyJson()
                .extractingPath("$.configured").isEqualTo(false);
        assertThat(clearedAgain).hasStatus(HttpStatus.NO_CONTENT);
        assertThat(get(owner, PATH)).bodyJson().extractingPath("$.state").isEqualTo("not_configured");
        var changes = get(owner, PATH + "/changes");
        assertThat(changes).bodyJson().extractingPath("$.page.totalElements").isEqualTo(2);
        assertThat(changes).bodyJson().extractingPath("$.content[0].changes[0].kind").isEqualTo("cleared");
    }

    /** A Clear confirmed on a form another Owner's save has overtaken erases nothing. */
    @Test
    void aClearOnARevisionAnotherSaveMovedIsAConflictAndClearsNothing() {

        // given
        var owner = owner();
        var opened = revision(owner);
        save(owner, openRelay(), tokenOf(test(owner, openRelay(), unique("ana") + "@acme.test")), opened);

        // when
        var actual = delete(owner, opened);

        // then
        assertThat(actual).hasStatus(HttpStatus.CONFLICT).bodyJson().extractingPath("$.code")
                .isEqualTo("stale_revision");
        assertThat(get(owner, PATH)).bodyJson().extractingPath("$.state").isEqualTo("configured");
        assertThat(get(owner, PATH + "/changes")).bodyJson().extractingPath("$.page.totalElements").isEqualTo(1);
    }

    /**
     * A save holding the row and a delivery refusal written at once both finish. The row is held from outside until
     * both wait on it, so the save takes it first; were the reload still inside the save's transaction behind a lock
     * the refusal holds, neither would ever return. The refusal is about the values the save replaced, so the saved
     * ones are not marked failing.
     */
    @Test
    void aSaveAndADeliveryRefusalReportedAtTheSameInstantBothFinish() throws Exception {

        // given
        var owner = owner();
        save(owner, openRelay(), tokenOf(test(owner, openRelay(), unique("ana") + "@acme.test")), revision(owner));
        var replaced = ((MailServerState.Configured) mailServer.current()).settings();
        var fixed = openRelay();
        fixed.put("fromName", "Acme Mail");
        var token = tokenOf(test(owner, fixed, unique("ana") + "@acme.test"));
        var revision = revision(owner);
        var database = SelfHostedDatabase.shared();
        try (var holder = DriverManager.getConnection(database.getJdbcUrl(), database.getUsername(),
                database.getPassword()); var statement = holder.createStatement()) {
            holder.setAutoCommit(false);
            statement.execute("SELECT revision FROM mail_server_settings FOR UPDATE");
            var saving = CompletableFuture.supplyAsync(() -> save(owner, fixed, token, revision), threads);
            awaitUpdatesWaitingOnTheRow(1);
            var recording = CompletableFuture.runAsync(
                    () -> mailServer.recordOutcome(
                            DeliveryOutcome.of(replaced, MailOutcome.refused(MailRefusal.AUTHENTICATION_FAILED))),
                    threads);
            awaitUpdatesWaitingOnTheRow(2);

            // when
            holder.commit();

            // then
            assertThat(saving.get(5, TimeUnit.SECONDS)).hasStatusOk();
            recording.get(5, TimeUnit.SECONDS);
        }
        assertThat(mailServer.current()).isInstanceOf(MailServerState.Configured.class);
        assertThat(get(owner, PATH)).bodyJson().extractingPath("$.state").isEqualTo("configured");
    }

    @Test
    void aMemberWithNeitherPermissionReadsOnlyTheStatus() {

        // given
        var member = member();
        var body = Map.of("values", openRelay(), "recipient", "ana@acme.test");

        // then
        assertThat(get(member, PATH)).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(get(member, PATH + "/changes")).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(send(member, mvc.post().uri(PATH + "/test"), body)).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(save(member, openRelay(), "v1.1.x", 0)).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(delete(member, 0)).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(get(member, PATH + "/status")).hasStatusOk();
    }

    @Test
    void aReaderReadsTheMailServerAndItsHistoryAndChangesNothing() throws SQLException {

        // given
        var reader = reader();
        var body = Map.of("values", openRelay(), "recipient", "ana@acme.test");

        // then
        assertThat(get(reader, PATH)).hasStatusOk();
        assertThat(get(reader, PATH + "/changes")).hasStatusOk();
        assertThat(send(reader, mvc.post().uri(PATH + "/test"), body)).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(save(reader, openRelay(), "v1.bff04208.1.x", 0)).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(delete(reader, 0)).hasStatus(HttpStatus.FORBIDDEN);
    }

    /** Write does not imply read: a save answers a writer only the revision it moved to, never the view. */
    @Test
    void aWriterChangesTheMailServerAndReadsNeitherItNorItsHistory() throws SQLException {

        // given
        var writer = holderOf("mail_server:write", "Wes Writer");
        var owner = owner();

        // when
        var saved = save(writer, openRelay(), tokenOf(test(writer, openRelay(), unique("ana") + "@acme.test")),
                revision(owner));

        // then
        assertThat(get(writer, PATH)).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(get(writer, PATH + "/changes")).hasStatus(HttpStatus.FORBIDDEN);
        assertThat(saved).hasStatusOk().bodyJson().doesNotHavePath("$.state").doesNotHavePath("$.host")
                .doesNotHavePath("$.lastChange");
        assertThat(json.readTree(saved.getResponse().getContentAsByteArray()).path("revision").asLong())
                .isEqualTo(revision(owner));
    }

    /**
     * Mailpit refuses no sender, so a server scripted on a socket refuses the one at {@code MAIL FROM}, and the reply
     * is read through the real SMTP client.
     */
    @Test
    void aSenderTheServerRefusesIsSenderRefused() throws Exception {

        try (var server = new ServerSocket(0)) {
            var script = CompletableFuture.runAsync(() -> refuseTheSender(server), threads);
            var relay = openRelay();
            relay.put("host", "127.0.0.1");
            relay.put("port", server.getLocalPort());

            // when
            var actual = test(owner(), relay, unique("ana") + "@acme.test");

            // then
            assertThat(actual).hasStatus(HttpStatus.UNPROCESSABLE_ENTITY).bodyJson().extractingPath("$.code")
                    .isEqualTo("sender_refused");
            assertThat(actual).bodyJson().extractingPath("$.detail").asString().startsWith("550");
            script.get(5, TimeUnit.SECONDS);
        }
    }

    private static void refuseTheSender(ServerSocket server) {

        try (var connection = server.accept();
                var in = new BufferedReader(
                        new InputStreamReader(connection.getInputStream(), StandardCharsets.US_ASCII));
                var out = new PrintWriter(
                        new OutputStreamWriter(connection.getOutputStream(), StandardCharsets.US_ASCII), true)) {
            out.print("220 scripted.test ESMTP\r\n");
            out.flush();
            for (var line = in.readLine(); line != null
                    && !line.toUpperCase(Locale.ROOT).startsWith("QUIT"); line = in.readLine()) {
                out.print(line.toUpperCase(Locale.ROOT).startsWith("MAIL FROM")
                        ? "550 5.7.1 Sender address rejected\r\n"
                        : "250 scripted.test\r\n");
                out.flush();
            }
            out.print("221 2.0.0 Bye\r\n");
            out.flush();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void storeAPasswordNoKeyOpens() throws SQLException {

        MailServerRows.set("host = 'smtp.stored.test', port = 587, security = 'STARTTLS', username = 'relay',"
                + " from_address = 'axonia@acme.test', password_secret = '"
                + new SettingsCipher(TestKeys.previousOnly()).seal(SecretField.MAIL_PASSWORD, "old") + "'");
        mailServer.reload();
    }

    private MvcTestResult test(String token, Map<String, Object> values, String recipient) {

        return send(token, mvc.post().uri(PATH + "/test"), Map.of("values", values, "recipient", recipient));
    }

    private MvcTestResult save(String token, Map<String, Object> values, String testToken, long baseRevision) {

        return send(token, mvc.put().uri(PATH),
                Map.of("values", values, "testToken", testToken, "baseRevision", baseRevision));
    }

    private MvcTestResult delete(String token, long baseRevision) {

        return mvc.delete().uri(PATH + "?baseRevision=" + baseRevision).with(SelfHostedRequests.signedIn(token))
                .exchange();
    }

    private static void awaitUpdatesWaitingOnTheRow(long waiting) {

        await().atMost(Duration.ofSeconds(5))
                .until(() -> SelfHostedDatabase
                        .countAsOwner("SELECT count(*) FROM pg_stat_activity WHERE wait_event_type = 'Lock'"
                                + " AND query LIKE 'UPDATE mail_server_settings%'") == waiting);
    }

    private MvcTestResult get(String token, String path) {

        return mvc.get().uri(path).with(SelfHostedRequests.signedIn(token)).exchange();
    }

    private MvcTestResult send(String token, MockMvcTester.MockMvcRequestBuilder request, Map<String, Object> body) {

        return request.with(SelfHostedRequests.signedIn(token)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)).exchange();
    }

    private String tokenOf(MvcTestResult tested) {

        assertThat(tested).hasStatusOk();
        return json.readTree(tested.getResponse().getContentAsByteArray()).path("testToken").asString();
    }

    private long revision(String token) {

        return json.readTree(get(token, PATH).getResponse().getContentAsByteArray()).path("revision").asLong();
    }

    private static Map<String, Object> openRelay() {

        return relayAt(Mailpit.open(), "NONE");
    }

    private static Map<String, Object> authenticatingRelay(String password) {

        var values = relayAt(Mailpit.authenticating(), "NONE");
        values.put("username", Mailpit.AUTH_USERNAME);
        values.put("password", password);
        return values;
    }

    private static Map<String, Object> relayAt(Mailpit mailpit, String security) {

        var values = new LinkedHashMap<String, Object>();
        values.put("host", mailpit.host());
        values.put("port", mailpit.smtpPort());
        values.put("security", security);
        values.put("username", null);
        values.put("password", null);
        values.put("fromAddress", "axonia@acme.test");
        values.put("fromName", "Acme Communities");
        return values;
    }

    private static Map<String, Object> unreachableRelay() throws IOException {

        var values = openRelay();
        try (var socket = new ServerSocket(0)) {
            values.put("host", "127.0.0.1");
            values.put("port", socket.getLocalPort());
        }
        return values;
    }

    private String owner() {

        return issuer.token(unique("owner"),
                Map.of("groups", List.of(SelfHostedTestInfrastructure.OWNER_GROUP), "name", "Ana Ruiz"));
    }

    private String member() {

        return issuer.token(unique("member"), Map.of("name", "Bob Member"));
    }

    private String reader() throws SQLException {

        return holderOf("mail_server:read", "Rita Reader");
    }

    private String holderOf(String permission, String name) throws SQLException {

        var subject = unique("holder");
        var role = "Mail server " + UUID.randomUUID();
        SelfHostedDatabase.asOwner(
                """
                        INSERT INTO role (organization_id, name, created_at, updated_at, version, system)
                        VALUES ('%1$s', '%2$s', now(), now(), 0, false);
                        INSERT INTO role_permission (role_id, permission) SELECT id, '%4$s' FROM role WHERE name = '%2$s';
                        INSERT INTO role_binding (organization_id, subject_type, subject_id, role_id, created_at, updated_at, version)
                        SELECT '%1$s', 'USER', '%3$s', id, now(), now(), 0 FROM role WHERE name = '%2$s';
                        """
                        .formatted(SelfHostedTestInfrastructure.ORGANIZATION_ID, role, subject, permission));
        return issuer.token(subject, Map.of("name", name));
    }

    private static String unique(String prefix) {

        return prefix + "-" + UUID.randomUUID();
    }
}
