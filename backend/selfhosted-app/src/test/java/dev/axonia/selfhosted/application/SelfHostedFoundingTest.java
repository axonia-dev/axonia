package dev.axonia.selfhosted.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;

import dev.axonia.access.api.AccessSeeding;
import dev.axonia.access.api.OwnerBinding;
import dev.axonia.founding.api.FoundingRequest;
import dev.axonia.founding.api.OrganizationFounder;
import dev.axonia.organization.api.Organization;
import dev.axonia.organization.api.OrganizationKind;
import dev.axonia.organization.api.OrganizationLifecycleState;
import dev.axonia.organization.api.OrganizationRegistry;
import dev.axonia.selfhosted.application.port.out.FoundingLedger;
import dev.axonia.selfhosted.domain.Founded;
import dev.axonia.selfhosted.domain.Installation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SelfHostedFoundingTest {

    private static final Instant NOW = Instant.parse("2026-10-02T10:15:30.123456789Z");

    private static final Installation ACME = new Installation("acme", "Acme", "axonia-owners", "sub");

    private static final Founded FOUNDED = new Founded("acme", "sub", Instant.parse("2026-09-30T08:00:00Z"));

    @Mock
    private FoundingLedger settings;

    @Mock
    private OrganizationFounder founder;

    @Mock
    private AccessSeeding accessSeeder;

    @Mock
    private OrganizationRegistry organizations;

    private SelfHostedFounding sut;

    @BeforeEach
    void setUp() {

        sut = new SelfHostedFounding(settings, founder, accessSeeder, organizations, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void foundsTheOrganizationWhenTheInstallationHasNone() {

        // given
        when(settings.find()).thenReturn(Optional.empty());

        // when
        sut.establish(ACME);

        // then
        verify(founder).found(new FoundingRequest("acme", "Acme", new OwnerBinding.Group("axonia-owners", "Owners")));
        verify(settings).record(new Founded("acme", "sub", Instant.parse("2026-10-02T10:15:30.123Z")));
        verifyNoInteractions(accessSeeder, organizations);
    }

    @Test
    void takesTheFoundingLockBeforeItReadsAnything() {

        // given
        when(settings.find()).thenReturn(Optional.empty());

        // when
        sut.establish(ACME);

        // then
        var order = inOrder(settings, founder);
        order.verify(settings).serializeFounding();
        order.verify(settings).find();
        order.verify(founder).found(any());
    }

    @Test
    void aLaterStartWithNothingChangedWritesNothing() {

        // given
        when(settings.find()).thenReturn(Optional.of(FOUNDED));
        when(organizations.organization("acme")).thenReturn(Optional.of(named("Acme")));
        when(accessSeeder.ensureGroupOwner("acme", new OwnerBinding.Group("axonia-owners", "Owners")))
                .thenReturn(false);

        // when
        sut.establish(ACME);

        // then
        verify(organizations, never()).rename(any(), any());
        verify(founder, never()).found(any());
        verify(settings, never()).record(any());
    }

    @Test
    void bindsANewlyConfiguredOwnerGroupOnALaterStart() {

        // given
        when(settings.find()).thenReturn(Optional.of(FOUNDED));
        when(organizations.organization("acme")).thenReturn(Optional.of(named("Acme")));
        when(accessSeeder.ensureGroupOwner("acme", new OwnerBinding.Group("platform-admins", "Owners")))
                .thenReturn(true);

        // when
        sut.establish(new Installation("acme", "Acme", "platform-admins", "sub"));

        // then
        verify(accessSeeder).ensureGroupOwner("acme", new OwnerBinding.Group("platform-admins", "Owners"));
        verify(founder, never()).found(any());
    }

    @Test
    void renamesTheOrganizationWhenTheConfiguredNameChanged() {

        // given
        when(settings.find()).thenReturn(Optional.of(FOUNDED));
        when(organizations.organization("acme")).thenReturn(Optional.of(named("Acme")));

        // when
        sut.establish(new Installation("acme", "Acme Corporation", "axonia-owners", "sub"));

        // then
        verify(organizations).rename("acme", "Acme Corporation");
    }

    @Test
    void refusesAnotherOrganizationThanTheOneItFounded() {

        // given
        when(settings.find()).thenReturn(Optional.of(FOUNDED));

        // when / then
        assertThatThrownBy(() -> sut.establish(new Installation("globex", "Globex", "axonia-owners", "sub")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("'acme'")
                .hasMessageContaining("'globex'");
        verifyNoInteractions(founder, accessSeeder, organizations);
        verify(settings, never()).record(any());
    }

    @Test
    void refusesAnotherSubjectClaimThanTheOneItFounded() {

        // given
        when(settings.find()).thenReturn(Optional.of(FOUNDED));

        // when / then
        assertThatThrownBy(() -> sut.establish(new Installation("acme", "Acme", "axonia-owners", "oid")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("'sub'").hasMessageContaining("'oid'");
        verifyNoInteractions(founder, accessSeeder, organizations);
    }

    @Test
    void refusesToStartWhenTheFoundedOrganizationIsMissingFromTheRegistry() {

        // given
        when(settings.find()).thenReturn(Optional.of(FOUNDED));
        when(organizations.organization("acme")).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> sut.establish(ACME)).isInstanceOf(IllegalStateException.class)
                .hasMessage("The installation's Organization 'acme' is missing from the registry.");
        verifyNoInteractions(founder, accessSeeder);
    }

    @Test
    void establishRunsInATransaction() throws NoSuchMethodException {

        // when
        var establish = SelfHostedFounding.class.getDeclaredMethod("establish", Installation.class);

        // then
        assertThat(new AnnotationTransactionAttributeSource(false).getTransactionAttribute(establish,
                SelfHostedFounding.class)).isNotNull();
    }

    private static Organization named(String name) {

        return new Organization("acme", name, OrganizationKind.ENTERPRISE, OrganizationLifecycleState.ACTIVE, true);
    }
}
