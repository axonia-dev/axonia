package dev.axonia.community;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.NotFoundException;
import dev.axonia.community.internal.Communities;
import dev.axonia.community.internal.MembershipAuthorizer;
import dev.axonia.community.mother.CommunityEntityMother;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@MockitoSettings
class CommunityCapabilitiesTest {

    @Mock
    private Communities communities;

    @Mock
    private MembershipAuthorizer membershipAuthorizer;

    @Mock
    private OrganizationContext organizationContext;

    @InjectMocks
    private CommunityCapabilities sut;

    @Test
    void canCurateTags_returnsTrue_whenOrganizerOrAxisOpen() {

        // given
        when(organizationContext.organizationId()).thenReturn("default");
        var entity = CommunityEntityMother.any().id(1L).canCurateTags(true).build();
        when(communities.findByIdOrThrow("default", 1L)).thenReturn(entity);
        when(membershipAuthorizer.isOrganizerOrOpenMember("default", 1L, "dev-user", true)).thenReturn(true);

        // when / then
        assertThat(sut.canCurateTags(1L, "dev-user")).isTrue();
    }

    @Test
    void canCurateTags_returnsFalse_whenMemberAndAxisClosed() {

        // given
        when(organizationContext.organizationId()).thenReturn("default");
        var entity = CommunityEntityMother.any().id(1L).canCurateTags(false).build();
        when(communities.findByIdOrThrow("default", 1L)).thenReturn(entity);
        when(membershipAuthorizer.isOrganizerOrOpenMember("default", 1L, "dev-user", false)).thenReturn(false);

        // when / then
        assertThat(sut.canCurateTags(1L, "dev-user")).isFalse();
    }

    @Test
    void canCurateTags_notFound_throwsNotFoundException() {

        // given
        when(organizationContext.organizationId()).thenReturn("default");
        when(communities.findByIdOrThrow("default", 99L)).thenThrow(new NotFoundException("Community not found: test"));

        // when / then
        assertThatThrownBy(() -> sut.canCurateTags(99L, "dev-user")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void canViewHealth_returnsTrue_whenOrganizerOrAxisOpen() {

        // given
        when(organizationContext.organizationId()).thenReturn("default");
        var entity = CommunityEntityMother.any().id(1L).canViewHealth(true).build();
        when(communities.findByIdOrThrow("default", 1L)).thenReturn(entity);
        when(membershipAuthorizer.isOrganizerOrOpenMember("default", 1L, "dev-user", true)).thenReturn(true);

        // when / then
        assertThat(sut.canViewHealth(1L, "dev-user")).isTrue();
    }

    @Test
    void canViewHealth_returnsFalse_whenMemberAndAxisClosed() {

        // given
        when(organizationContext.organizationId()).thenReturn("default");
        var entity = CommunityEntityMother.any().id(1L).canViewHealth(false).build();
        when(communities.findByIdOrThrow("default", 1L)).thenReturn(entity);
        when(membershipAuthorizer.isOrganizerOrOpenMember("default", 1L, "dev-user", false)).thenReturn(false);

        // when / then
        assertThat(sut.canViewHealth(1L, "dev-user")).isFalse();
    }

    @Test
    void canViewHealth_notFound_throwsNotFoundException() {

        // given
        when(organizationContext.organizationId()).thenReturn("default");
        when(communities.findByIdOrThrow("default", 99L)).thenThrow(new NotFoundException("Community not found: test"));

        // when / then
        assertThatThrownBy(() -> sut.canViewHealth(99L, "dev-user")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void canInviteMembers_returnsTrue_whenOrganizerOrAxisOpen() {

        // given
        when(organizationContext.organizationId()).thenReturn("default");
        var entity = CommunityEntityMother.any().id(1L).canInviteMembers(true).build();
        when(communities.findByIdOrThrow("default", 1L)).thenReturn(entity);
        when(membershipAuthorizer.isOrganizerOrOpenMember("default", 1L, "dev-user", true)).thenReturn(true);

        // when / then
        assertThat(sut.canInviteMembers(1L, "dev-user")).isTrue();
    }

    @Test
    void canInviteMembers_returnsFalse_whenMemberAndAxisClosed() {

        // given
        when(organizationContext.organizationId()).thenReturn("default");
        var entity = CommunityEntityMother.any().id(1L).canInviteMembers(false).build();
        when(communities.findByIdOrThrow("default", 1L)).thenReturn(entity);
        when(membershipAuthorizer.isOrganizerOrOpenMember("default", 1L, "dev-user", false)).thenReturn(false);

        // when / then
        assertThat(sut.canInviteMembers(1L, "dev-user")).isFalse();
    }

    @Test
    void canInviteMembers_notFound_throwsNotFoundException() {

        // given
        when(organizationContext.organizationId()).thenReturn("default");
        when(communities.findByIdOrThrow("default", 99L)).thenThrow(new NotFoundException("Community not found: test"));

        // when / then
        assertThatThrownBy(() -> sut.canInviteMembers(99L, "dev-user")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void canManageCommunication_returnsTrue_whenOrganizerOrAxisOpen() {

        // given
        when(organizationContext.organizationId()).thenReturn("default");
        var entity = CommunityEntityMother.any().id(1L).canManageCommunication(true).build();
        when(communities.findByIdOrThrow("default", 1L)).thenReturn(entity);
        when(membershipAuthorizer.isOrganizerOrOpenMember("default", 1L, "dev-user", true)).thenReturn(true);

        // when / then
        assertThat(sut.canManageCommunication(1L, "dev-user")).isTrue();
    }

    @Test
    void canManageCommunication_returnsFalse_whenMemberAndAxisClosed() {

        // given
        when(organizationContext.organizationId()).thenReturn("default");
        var entity = CommunityEntityMother.any().id(1L).canManageCommunication(false).build();
        when(communities.findByIdOrThrow("default", 1L)).thenReturn(entity);
        when(membershipAuthorizer.isOrganizerOrOpenMember("default", 1L, "dev-user", false)).thenReturn(false);

        // when / then
        assertThat(sut.canManageCommunication(1L, "dev-user")).isFalse();
    }

    @Test
    void canManageCommunication_notFound_throwsNotFoundException() {

        // given
        when(organizationContext.organizationId()).thenReturn("default");
        when(communities.findByIdOrThrow("default", 99L)).thenThrow(new NotFoundException("Community not found: test"));

        // when / then
        assertThatThrownBy(() -> sut.canManageCommunication(99L, "dev-user")).isInstanceOf(NotFoundException.class);
    }

    @Test
    void canCreditContributions_returnsTrue_whenOrganizerOrAxisOpen() {

        // given
        when(organizationContext.organizationId()).thenReturn("default");
        var entity = CommunityEntityMother.any().id(1L).canCreditContributions(true).build();
        when(communities.findByIdOrThrow("default", 1L)).thenReturn(entity);
        when(membershipAuthorizer.isOrganizerOrOpenMember("default", 1L, "dev-user", true)).thenReturn(true);

        // when / then
        assertThat(sut.canCreditContributions(1L, "dev-user")).isTrue();
    }

    @Test
    void canCreditContributions_returnsFalse_whenMemberAndAxisClosed() {

        // given
        when(organizationContext.organizationId()).thenReturn("default");
        var entity = CommunityEntityMother.any().id(1L).canCreditContributions(false).build();
        when(communities.findByIdOrThrow("default", 1L)).thenReturn(entity);
        when(membershipAuthorizer.isOrganizerOrOpenMember("default", 1L, "dev-user", false)).thenReturn(false);

        // when / then
        assertThat(sut.canCreditContributions(1L, "dev-user")).isFalse();
    }

    @Test
    void canCreditContributions_notFound_throwsNotFoundException() {

        // given
        when(organizationContext.organizationId()).thenReturn("default");
        when(communities.findByIdOrThrow("default", 99L)).thenThrow(new NotFoundException("Community not found: test"));

        // when / then
        assertThatThrownBy(() -> sut.canCreditContributions(99L, "dev-user")).isInstanceOf(NotFoundException.class);
    }
}
