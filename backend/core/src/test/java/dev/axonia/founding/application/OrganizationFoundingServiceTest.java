package dev.axonia.founding.application;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.axonia.access.api.AccessSeeding;
import dev.axonia.access.api.OwnerBinding;
import dev.axonia.community.api.CommunityKindDefaults;
import dev.axonia.events.api.DefaultSectionTypes;
import dev.axonia.founding.api.FoundingRequest;
import dev.axonia.knowledge.api.DefaultArtifactTypes;
import dev.axonia.organization.api.Organization;
import dev.axonia.organization.api.OrganizationRegistration;
import dev.axonia.organization.api.OrganizationRegistry;
import dev.axonia.shared.api.error.ConflictException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationFoundingServiceTest {

    @Mock
    OrganizationRegistry organizationService;
    @Mock
    AccessSeeding accessSeeder;
    @Mock
    CommunityKindDefaults communityKindDefaults;
    @Mock
    DefaultArtifactTypes artifactTypeDefaults;
    @Mock
    DefaultSectionTypes sectionTypeDefaults;

    @InjectMocks
    OrganizationFoundingService founder;

    private static final OwnerBinding OWNERS = new OwnerBinding.Group("8f1c-owners", "Owners");

    @Test
    void foundsTheOrganizationWithItsOwnerAndAllThreeVocabularies() {

        var created = Organization.builder().id("acme").name("Acme").build();
        when(organizationService.register(any())).thenReturn(created);

        var actual = founder.found(new FoundingRequest("acme", "Acme", OWNERS));

        assertThat(actual).isSameAs(created);
        var registrations = ArgumentCaptor.forClass(OrganizationRegistration.class);
        verify(organizationService).register(registrations.capture());
        assertThat(registrations.getValue().id()).isEqualTo("acme");
        assertThat(registrations.getValue().name()).isEqualTo("Acme");
        verify(accessSeeder).seedEnterpriseAccess("acme", OWNERS);
        verify(communityKindDefaults).seedFor("acme");
        verify(artifactTypeDefaults).seedFor("acme");
        verify(sectionTypeDefaults).seedFor("acme");
    }

    @Test
    void refusesAnOrganizationThatAlreadyExists() {

        when(organizationService.organizationExists("acme")).thenReturn(true);

        assertThatThrownBy(() -> founder.found(new FoundingRequest("acme", "Acme", OWNERS)))
                .isInstanceOf(ConflictException.class).hasMessageContaining("acme");
        verify(organizationService, never()).register(any());
    }
}
