package dev.axonia.installation.application;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import dev.axonia.communication.api.MailSecurity;
import dev.axonia.identity.api.UserDirectory;
import dev.axonia.identity.api.UserDirectory.DirectoryUser;
import dev.axonia.identity.api.UserDirectory.MemberStatus;
import dev.axonia.installation.application.port.out.SettingsChangeLog;
import dev.axonia.installation.configuration.MailServerOverlayProperties;
import dev.axonia.installation.domain.MailFieldChange;
import dev.axonia.installation.domain.MailServerOverlay;
import dev.axonia.installation.domain.MailServerRow;
import dev.axonia.installation.domain.MailValues;
import dev.axonia.installation.domain.SettingsChangeEntry;
import dev.axonia.installation.domain.TestKeys;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

class MailServerViewsTest {

    private static final Instant AT = Instant.parse("2026-10-02T14:02:11.123Z");

    private final SettingsChangeLog history = mock(SettingsChangeLog.class);

    private final UserDirectory directory = mock(UserDirectory.class);

    private final TestedFingerprints fingerprints = new TestedFingerprints(TestKeys.current());

    private final MailServerViews views = views(MailServerOverlayProperties.builder().build());

    @Test
    void anInstallationThatNeverSavedIsNotConfiguredAndShowsNothing() {

        // given
        given(history.latest()).willReturn(Optional.empty());

        // when
        var actual = views.view(resolved(MailValues.NONE, MailServerRow.builder().build(), false), "acme");

        // then
        assertThat(actual.state()).isEqualTo("not_configured");
        assertThat(actual.host()).isEqualTo(new MailServerFieldView<>(null, null));
        assertThat(actual.password()).isEqualTo(new MailServerPasswordView(false, true, null));
        assertThat(actual.changedSinceTest()).isFalse();
        assertThat(actual.lastFailure()).isNull();
        assertThat(actual.lastChange()).isNull();
    }

    @Test
    void aSavedAndTestedMailServerIsConfiguredAndSaysWhoChangedItLast() {

        // given
        var row = savedRow().toBuilder().testedFingerprint(fingerprints.digest(fingerprintOf(savedValues()))).build();
        given(history.latest()).willReturn(Optional.of(new SettingsChangeEntry(12, AT, "ana", "[]")));
        given(directory.byIds("acme", List.of("ana"))).willReturn(List.of(ana()));

        // when
        var actual = views.view(resolved(savedValues(), row, false), "acme");

        // then
        assertThat(actual.state()).isEqualTo("configured");
        assertThat(actual.revision()).isEqualTo(3);
        assertThat(actual.port()).isEqualTo(new MailServerFieldView<>(587, null));
        assertThat(actual.security()).isEqualTo(new MailServerFieldView<>(MailSecurity.STARTTLS, null));
        assertThat(actual.password()).isEqualTo(new MailServerPasswordView(true, true, null));
        assertThat(actual.lastChange()).isEqualTo(new MailLastChangeView(AT, "Ana Ruiz"));
    }

    @Test
    void valuesTheDeploymentChangedSinceTheTestAreSaidToBeSoAndLocked() {

        // given
        var tested = savedRow().toBuilder().testedFingerprint(fingerprints.digest(fingerprintOf(savedValues())))
                .build();
        var locked = views(MailServerOverlayProperties.builder().host("smtp.deployed.test").build());
        given(history.latest()).willReturn(Optional.empty());

        // when
        var actual = locked.view(new ResolvedMailServer(MailValues.builder().host("smtp.deployed.test").build(),
                savedValues(), false, tested), "acme");

        // then
        assertThat(actual.state()).isEqualTo("changed_since_test");
        assertThat(actual.changedSinceTest()).isTrue();
        assertThat(actual.host()).isEqualTo(new MailServerFieldView<>("smtp.deployed.test", "AXONIA_MAIL_HOST"));
    }

    @Test
    void aMailServerConfiguredByTheDeploymentAloneAndNeverTestedIsConfigured() {

        // given
        given(history.latest()).willReturn(Optional.empty());

        // when
        var actual = views.view(resolved(savedValues(), MailServerRow.builder().revision(0).build(), false), "acme");

        // then
        assertThat(actual.state()).isEqualTo("configured");
        assertThat(actual.changedSinceTest()).isFalse();
    }

    @Test
    void aRecordedRefusalIsDeliveryFailingAndAnUnreadablePasswordOutranksIt() {

        // given
        var failing = savedRow().toBuilder().lastFailureReason("authentication_failed").lastFailureAt(AT).build();
        given(history.latest()).willReturn(Optional.empty());

        // when
        var actual = views.view(resolved(savedValues(), failing, false), "acme");
        var unreadable = views.view(resolved(savedValues().toBuilder().password(null).build(), failing, true), "acme");

        // then
        assertThat(actual.state()).isEqualTo("delivery_failing");
        assertThat(actual.lastFailure()).isEqualTo(new MailFailureView("authentication_failed", AT));
        assertThat(unreadable.state()).isEqualTo("secret_unreadable");
        assertThat(unreadable.password()).isEqualTo(new MailServerPasswordView(true, false, null));
    }

    @Test
    void anErasedActorHasNoName() {

        // given
        given(history.latest()).willReturn(Optional.of(new SettingsChangeEntry(12, AT, null, "[]")));

        // when
        var actual = views.view(resolved(MailValues.NONE, MailServerRow.builder().build(), false), "acme");

        // then
        assertThat(actual.lastChange()).isEqualTo(new MailLastChangeView(AT, null));
    }

    /** An actor who left the Organization, or was erased from its directory, has no name. */
    @Test
    void anActorNoLongerAMemberHasNoName() {

        // given
        var pageable = PageRequest.of(0, 20);
        given(history.page(pageable)).willReturn(new PageImpl<>(
                List.of(new SettingsChangeEntry(14, AT, "left", "[]"), new SettingsChangeEntry(13, AT, "erased", "[]")),
                pageable, 2));
        given(directory.byIds(eq("acme"), any())).willReturn(
                List.of(new DirectoryUser("left", "Lea Left", "lea@acme.test", AT, AT, MemberStatus.DEACTIVATED),
                        new DirectoryUser("erased", "Deleted member", null, AT, AT, MemberStatus.ERASED)));

        // when
        var actual = views.changes(pageable, "acme");

        // then
        assertThat(actual.getContent()).extracting(MailServerChangeView::actorName).containsExactly(null, null);
    }

    @Test
    void aTestedServerWhosePasswordCannotBeReadIsNotSaidToHaveChangedSinceTheTest() {

        // given
        var row = savedRow().toBuilder().testedFingerprint(fingerprints.digest(fingerprintOf(savedValues()))).build();
        given(history.latest()).willReturn(Optional.empty());

        // when
        var actual = views.view(resolved(savedValues().toBuilder().password(null).build(), row, true), "acme");

        // then
        assertThat(actual.state()).isEqualTo("secret_unreadable");
        assertThat(actual.changedSinceTest()).isFalse();
    }

    /**
     * A host holding an ideographic space, which only a direct edit of the row stores: the card still reads, and learns
     * the revision a Clear needs.
     */
    @Test
    void storedSettingsDeliveryWouldRefuseAreNotConfiguredAndStillCarryTheirRevision() {

        // given
        var stored = savedValues().toBuilder().host("smtp\u3000acme.test").build();
        var row = savedRow().toBuilder().host("smtp\u3000acme.test")
                .testedFingerprint(fingerprints.digest(fingerprintOf(savedValues()))).build();
        given(history.latest()).willReturn(Optional.empty());

        // when
        var actual = views.view(resolved(stored, row, false), "acme");

        // then
        assertThat(actual.state()).isEqualTo("not_configured");
        assertThat(actual.revision()).isEqualTo(3);
        assertThat(actual.changedSinceTest()).isFalse();
        assertThat(actual.host()).isEqualTo(new MailServerFieldView<>("smtp\u3000acme.test", null));
    }

    @Test
    void pagesTheHistoryWithEachActorsNameAndWhatChanged() {

        // given
        var pageable = PageRequest.of(0, 20);
        given(history.page(pageable)).willReturn(new PageImpl<>(List.of(
                new SettingsChangeEntry(13, AT, "ana",
                        "[{\"field\":\"password\",\"kind\":\"replaced\",\"from\":null,\"to\":null}]"),
                new SettingsChangeEntry(12, AT, "gone", "[]")), pageable, 2));
        given(directory.byIds(eq("acme"), any())).willReturn(List.of(ana()));

        // when
        var actual = views.changes(pageable, "acme");

        // then
        assertThat(actual.getTotalElements()).isEqualTo(2);
        assertThat(actual.getContent()).containsExactly(
                new MailServerChangeView(13, AT, "Ana Ruiz",
                        List.of(new MailFieldChange("password", "replaced", null, null))),
                new MailServerChangeView(12, AT, null, List.of()));
    }

    private MailServerViews views(MailServerOverlayProperties overlay) {

        return new MailServerViews(MailServerOverlay.from(overlay), fingerprints, history, directory,
                JsonMapper.builder().build());
    }

    private static ResolvedMailServer resolved(MailValues stored, MailServerRow row, boolean unreadable) {

        return new ResolvedMailServer(MailValues.NONE, stored, unreadable, row);
    }

    private static MailValues savedValues() {

        return MailValues.builder().host("smtp.acme.test").port(587).security(MailSecurity.STARTTLS).username("relay")
                .password("s3cret").fromAddress("axonia@acme.test").build();
    }

    private static MailServerRow savedRow() {

        return MailServerRow.builder().host("smtp.acme.test").port(587).security(MailSecurity.STARTTLS)
                .username("relay").passwordSecret("v1:bff04208:x").fromAddress("axonia@acme.test").revision(3).build();
    }

    private static String fingerprintOf(MailValues values) {

        return values.settings().orElseThrow().fingerprint();
    }

    private static DirectoryUser ana() {

        return new DirectoryUser("ana", "Ana Ruiz", "ana@acme.test", AT, AT, MemberStatus.PRESENT);
    }
}
