package dev.axonia.events.adapter.out.organization;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.organization.api.Organization;
import dev.axonia.organization.api.OrganizationKind;
import dev.axonia.organization.api.OrganizationLifecycleState;
import dev.axonia.organization.api.OrganizationRegistry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@MockitoSettings
class OrganizationAccessAdapterTest {

    @Mock
    private OrganizationRegistry organizationService;

    @InjectMocks
    private OrganizationAccessAdapter sut;

    private static Organization inState(OrganizationLifecycleState state) {

        return Organization.builder().id("northwind").name("Northwind").kind(OrganizationKind.ENTERPRISE)
                .lifecycleState(state).communicationsEnabled(true).build();
    }

    @Test
    void isActive() {

        // given
        when(organizationService.organization("northwind"))
                .thenReturn(Optional.of(inState(OrganizationLifecycleState.ACTIVE)));

        // when / then
        assertThat(sut.isActive("northwind")).isTrue();
    }

    @Test
    void isActive_suspended() {

        // given
        when(organizationService.organization("northwind"))
                .thenReturn(Optional.of(inState(OrganizationLifecycleState.SUSPENDED)));

        // when / then
        assertThat(sut.isActive("northwind")).isFalse();
    }

    @Test
    void isActive_unknownOrganization() {

        // given
        when(organizationService.organization("gone")).thenReturn(Optional.empty());

        // when / then
        assertThat(sut.isActive("gone")).isFalse();
    }
}
