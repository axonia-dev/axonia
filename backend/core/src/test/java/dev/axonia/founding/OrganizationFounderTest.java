package dev.axonia.founding;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.axonia.ConflictException;
import dev.axonia.access.AccessSeeder;
import dev.axonia.access.OwnerBinding;
import dev.axonia.community.CommunityKindDefaults;
import dev.axonia.events.SectionTypeDefaults;
import dev.axonia.knowledge.ArtifactTypeDefaults;
import dev.axonia.organization.Organization;
import dev.axonia.organization.OrganizationRegistration;
import dev.axonia.organization.OrganizationService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrganizationFounderTest {

    @Mock
    OrganizationService organizationService;
    @Mock
    AccessSeeder accessSeeder;
    @Mock
    CommunityKindDefaults communityKindDefaults;
    @Mock
    ArtifactTypeDefaults artifactTypeDefaults;
    @Mock
    SectionTypeDefaults sectionTypeDefaults;

    @InjectMocks
    OrganizationFounder founder;

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
