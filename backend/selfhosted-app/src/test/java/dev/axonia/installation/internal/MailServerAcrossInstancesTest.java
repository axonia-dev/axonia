package dev.axonia.installation.internal;

import java.sql.SQLException;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import dev.axonia.Mailpit;
import dev.axonia.OrganizationScope;
import dev.axonia.communication.CommunicationPort;
import dev.axonia.communication.MailServer;
import dev.axonia.communication.MailServerState;
import dev.axonia.communication.MessageType;
import dev.axonia.communication.OutboundMessage;
import dev.axonia.selfhosted.MockOidcIssuer;
import dev.axonia.selfhosted.SelfHostedDatabase;
import dev.axonia.selfhosted.SelfHostedRequests;
import dev.axonia.selfhosted.SelfHostedSpringBootTest;
import dev.axonia.selfhosted.SelfHostedTestInfrastructure;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * A second installation instance on the same database and issuer, as behind a load balancer: what one saves the other
 * sends with within one revision check, one still on the old settings key keeps sending while another rotates, and mail
 * a real drain met with no mail server stays suppressed once one is configured.
 */
@SelfHostedSpringBootTest
class MailServerAcrossInstancesTest {

    private static final String ROTATED_KEY = "bmV3LXJvdGF0ZWQtc2V0dGluZ3Mta2V5LTMyYnl0ZXM=";

    private static final String ROTATED_KEY_ID = "9f77ad90";

    @Autowired
    private MockMvcTester mvc;

    @Autowired
    private MockOidcIssuer issuer;

    @Autowired
    private JsonMapper json;

    @Autowired
    private StoredMailServer mailServer;

    @Autowired
    private MailServerSettingsTable table;

    @BeforeEach
    void forgetTheMailServer() throws SQLException {

        MailServerRows.reset();
        mailServer.reload();
    }

    @Test
    void whatOneInstanceSavesOrClearsTheOtherSendsWithWithinOneRevisionCheck() {

        try (var other = SelfHostedTestInfrastructure.start(Map.of())) {
            // given
            var otherMailServer = other.getBean(MailServer.class);
            assertThat(otherMailServer.current()).isInstanceOf(MailServerState.NotConfigured.class);

            // when
            var owner = owner();
            saveAnOpenRelay(owner);

            // then
            await().atMost(Duration.ofSeconds(8)).untilAsserted(() -> assertThat(otherMailServer.current())
                    .isInstanceOfSatisfying(MailServerState.Configured.class, configured -> {
                        assertThat(configured.settings().host()).isEqualTo(Mailpit.open().host());
                        assertThat(configured.settings().port()).isEqualTo(Mailpit.open().smtpPort());
                        assertThat(configured.settings().fromAddress()).isEqualTo("axonia@acme.test");
                    }));

            // and when a second save changes one value only
            var changed = values(null, null);
            changed.put("fromAddress", "mail@acme.test");
            save(owner, changed);

            // then
            await().atMost(Duration.ofSeconds(8))
                    .untilAsserted(() -> assertThat(otherMailServer.current()).isInstanceOfSatisfying(
                            MailServerState.Configured.class,
                            configured -> assertThat(configured.settings().fromAddress()).isEqualTo("mail@acme.test")));

            // and when
            assertThat(mvc.delete().uri("/axonia/mail-server?baseRevision=" + revision(owner))
                    .with(SelfHostedRequests.signedIn(owner)).exchange()).hasStatus2xxSuccessful();

            // then
            await().atMost(Duration.ofSeconds(8)).untilAsserted(
                    () -> assertThat(otherMailServer.current()).isInstanceOf(MailServerState.NotConfigured.class));
        }
    }

    /** The middle step of a three-step rotation (R7): the new key current there, the old one previous. */
    @Test
    void anInstanceStillOnTheOldKeyKeepsSendingWhileAnotherRotatesAndOnlyAReloadLosesIt() {

        // given
        var owner = owner();
        saveAnOpenRelayWithALogin(owner);
        assertThat(mailServer.current()).isInstanceOf(MailServerState.Configured.class);

        // when
        try (var rotated = SelfHostedTestInfrastructure.start(Map.of("axonia.installation.settings-key", ROTATED_KEY,
                "axonia.installation.previous-settings-keys", TestKeys.CURRENT))) {

            // then
            assertThat(table.read().passwordSecret()).startsWith("v1:" + ROTATED_KEY_ID + ":");
            assertThat(rotated.getBean(MailServer.class).current()).isInstanceOf(MailServerState.Configured.class);
            mailServer.checkRevision();
            assertThat(mailServer.current()).as("rotation changes no value, so the revision does not move")
                    .isInstanceOf(MailServerState.Configured.class);
            mailServer.reload();
            assertThat(mailServer.current()).as("without the new key as a previous one, a restart loses the password")
                    .isInstanceOf(MailServerState.NotConfigured.class);
        }
    }

    /**
     * During the middle step's rolling deploy, a save served by an instance still on the old key seals the password
     * under it; an instance already on the new key re-seals it at the revision check that sees the save, so the last
     * step, which drops the old key, loses nothing.
     */
    @Test
    void aSaveByAnInstanceStillOnTheOldKeyIsResealedUnderTheNewOneWithinOneRevisionCheck() {

        try (var rotated = SelfHostedTestInfrastructure.start(Map.of("axonia.installation.settings-key", ROTATED_KEY,
                "axonia.installation.previous-settings-keys", TestKeys.CURRENT))) {
            // when
            saveAnOpenRelayWithALogin(owner());

            // then
            await().atMost(Duration.ofSeconds(8)).untilAsserted(
                    () -> assertThat(table.read().passwordSecret()).startsWith("v1:" + ROTATED_KEY_ID + ":"));
            assertThat(rotated.getBean(MailServer.class).current()).isInstanceOf(MailServerState.Configured.class);
        }
    }

    /**
     * During the middle step's rolling deploy, an instance still configured as the first step (the old key current, the
     * new one previous) and an updated one each re-seal the row under their own key at their checks. Once only updated
     * instances remain, the row settles under the new key within one check, so the last step, which drops the old key,
     * loses nothing. Neither context ticks on its own here: each check is called in turn.
     */
    @Test
    void theRowSettlesUnderTheKeyOfTheInstancesThatRemain() {

        // given
        saveAnOpenRelayWithALogin(owner());
        var byHand = "PT1H";
        try (var updated = SelfHostedTestInfrastructure.start(
                Map.of("axonia.installation.settings-key", ROTATED_KEY, "axonia.installation.previous-settings-keys",
                        TestKeys.CURRENT, "axonia.installation.mail-server-check", byHand))) {
            var updatedMailServer = updated.getBean(StoredMailServer.class);
            assertThat(table.read().passwordSecret()).as("re-sealed at its start")
                    .startsWith("v1:" + ROTATED_KEY_ID + ":");
            try (var notYetUpdated = SelfHostedTestInfrastructure
                    .start(Map.of("axonia.installation.previous-settings-keys", ROTATED_KEY,
                            "axonia.installation.mail-server-check", byHand))) {

                // when
                updatedMailServer.checkRevision();
                var afterTheUpdatedCheck = table.read().passwordSecret();
                notYetUpdated.getBean(StoredMailServer.class).checkRevision();

                // then
                assertThat(afterTheUpdatedCheck).startsWith("v1:" + ROTATED_KEY_ID + ":");
                assertThat(table.read().passwordSecret()).as("the instance on the old key alone flips it back")
                        .startsWith("v1:" + TestKeys.CURRENT_ID + ":");
            }

            // when only the updated instance remains
            updatedMailServer.checkRevision();
            var settled = table.read().passwordSecret();
            updatedMailServer.checkRevision();

            // then
            assertThat(settled).startsWith("v1:" + ROTATED_KEY_ID + ":");
            assertThat(table.read().passwordSecret()).as("settled: a check under its own key writes nothing")
                    .isEqualTo(settled);
            assertThat(updatedMailServer.current()).isInstanceOf(MailServerState.Configured.class);
        }
    }

    /**
     * Mail is suppressed, not queued, while no mail server is configured, and configuring one releases none of it. The
     * second context drains every second for real, so the gate reads {@code current()} inside each row's transaction as
     * in production.
     */
    @Test
    void mailDrainedBeforeAMailServerIsConfiguredIsSuppressedAndNeverSentOnceOneIs() throws Exception {

        try (var draining = SelfHostedTestInfrastructure.start(
                Map.of("axonia.communication.sweep.enabled", "true", "axonia.communication.drain-interval", "1s"))) {
            // given
            var subject = unique("not-configured");
            var communication = draining.getBean(CommunicationPort.class);
            OrganizationScope.runAs(SelfHostedTestInfrastructure.ORGANIZATION_ID,
                    () -> communication.enqueue(OutboundMessage.builder().type(MessageType.SESSION_WRAP_UP)
                            .subjectRef(subject).recipientUserIds(List.of(unique("ana"), unique("bob")))
                            .template("session-wrap-up").facts(Map.of()).build()));

            // when
            await().atMost(Duration.ofSeconds(10))
                    .until(() -> mailRows(subject, "d.suppressed_reason = 'MAIL_NOT_CONFIGURED'") == 2);
            saveAnOpenRelay(owner());
            var drainingMailServer = draining.getBean(MailServer.class);
            await().atMost(Duration.ofSeconds(8))
                    .until(() -> drainingMailServer.current() instanceof MailServerState.Configured);

            // then
            await().during(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(5))
                    .until(() -> mailRows(subject,
                            "d.status = 'SUPPRESSED' AND d.suppressed_reason = 'MAIL_NOT_CONFIGURED'") == 2
                            && mailRows(subject, "d.status <> 'SUPPRESSED'") == 0);
        }
    }

    private static long mailRows(String subject, String condition) throws SQLException {

        return SelfHostedDatabase.countAsOwner("SELECT count(*) FROM communication_delivery d"
                + " JOIN communication_message m ON m.id = d.message_id WHERE m.subject_ref = '" + subject
                + "' AND d.channel = 'EMAIL' AND " + condition);
    }

    private long revision(String owner) {

        return json.readTree(mvc.get().uri("/axonia/mail-server").with(SelfHostedRequests.signedIn(owner)).exchange()
                .getResponse().getContentAsByteArray()).path("revision").asLong();
    }

    private void saveAnOpenRelay(String owner) {

        save(owner, values(null, null));
    }

    private void saveAnOpenRelayWithALogin(String owner) {

        save(owner, values("relay", "s3cret"));
    }

    private void save(String owner, Map<String, Object> values) {

        var tested = send(owner, mvc.post().uri("/axonia/mail-server/test"),
                Map.of("values", values, "recipient", unique("ana") + "@acme.test"));
        assertThat(tested).hasStatusOk();
        var token = json.readTree(tested.getResponse().getContentAsByteArray()).path("testToken").asString();
        assertThat(send(owner, mvc.put().uri("/axonia/mail-server"),
                Map.of("values", values, "testToken", token, "baseRevision", revision(owner)))).hasStatusOk();
    }

    private MvcTestResult send(String token, MockMvcTester.MockMvcRequestBuilder request, Map<String, Object> body) {

        return request.with(SelfHostedRequests.signedIn(token)).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)).exchange();
    }

    private static Map<String, Object> values(String username, String password) {

        var values = new LinkedHashMap<String, Object>();
        values.put("host", Mailpit.open().host());
        values.put("port", Mailpit.open().smtpPort());
        values.put("security", "NONE");
        values.put("username", username);
        values.put("password", password);
        values.put("fromAddress", "axonia@acme.test");
        values.put("fromName", null);
        return values;
    }

    private String owner() {

        return issuer.token(unique("owner"),
                Map.of("groups", List.of(SelfHostedTestInfrastructure.OWNER_GROUP), "name", "Ana Ruiz"));
    }

    private static String unique(String prefix) {

        return prefix + "-" + UUID.randomUUID();
    }
}
