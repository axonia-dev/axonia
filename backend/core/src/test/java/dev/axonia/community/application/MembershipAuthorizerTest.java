package dev.axonia.community.application;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.community.api.CommunityRole;
import dev.axonia.community.application.port.out.MembershipLedger;
import dev.axonia.community.domain.MembershipRecordMother;
import dev.axonia.shared.api.error.ForbiddenException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@MockitoSettings
class MembershipAuthorizerTest {

    @Mock
    private MembershipLedger membershipLedger;

    @InjectMocks
    private MembershipAuthorizer sut;

    @Test
    void requireOrganizer() {

        // given
        var organizer = MembershipRecordMother.any().role(CommunityRole.ORGANIZER).build();
        when(membershipLedger.find("default", 1L, "dev-user")).thenReturn(Optional.of(organizer));

        // when / then
        assertThatCode(() -> sut.requireOrganizer("default", 1L, "dev-user")).doesNotThrowAnyException();
    }

    @Test
    void requireOrganizer_notAMember() {

        // given
        when(membershipLedger.find("default", 1L, "dev-user")).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> sut.requireOrganizer("default", 1L, "dev-user"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void requireOrganizer_member() {

        // given
        var member = MembershipRecordMother.any().role(CommunityRole.MEMBER).build();
        when(membershipLedger.find("default", 1L, "dev-user")).thenReturn(Optional.of(member));

        // when / then
        assertThatThrownBy(() -> sut.requireOrganizer("default", 1L, "dev-user"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void requireOrganizerOrOpenMember_organizerAlwaysAllowed() {

        // given
        var organizer = MembershipRecordMother.any().role(CommunityRole.ORGANIZER).build();
        when(membershipLedger.find("default", 1L, "dev-user")).thenReturn(Optional.of(organizer));

        // when / then
        assertThatCode(() -> sut.requireOrganizerOrOpenMember("default", 1L, "dev-user", false))
                .doesNotThrowAnyException();
    }

    @Test
    void requireOrganizerOrOpenMember_memberAllowedWhenOpen() {

        // given
        var member = MembershipRecordMother.any().role(CommunityRole.MEMBER).build();
        when(membershipLedger.find("default", 1L, "dev-user")).thenReturn(Optional.of(member));

        // when / then
        assertThatCode(() -> sut.requireOrganizerOrOpenMember("default", 1L, "dev-user", true))
                .doesNotThrowAnyException();
    }

    @Test
    void requireOrganizerOrOpenMember_memberForbiddenWhenClosed() {

        // given
        var member = MembershipRecordMother.any().role(CommunityRole.MEMBER).build();
        when(membershipLedger.find("default", 1L, "dev-user")).thenReturn(Optional.of(member));

        // when / then
        assertThatThrownBy(() -> sut.requireOrganizerOrOpenMember("default", 1L, "dev-user", false))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void requireOrganizerOrOpenMember_nonMemberForbidden() {

        // given
        when(membershipLedger.find("default", 1L, "dev-user")).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> sut.requireOrganizerOrOpenMember("default", 1L, "dev-user", true))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void isOrganizerOrOpenMember_organizerReturnsTrue() {

        // given
        var organizer = MembershipRecordMother.any().role(CommunityRole.ORGANIZER).build();
        when(membershipLedger.find("default", 1L, "dev-user")).thenReturn(Optional.of(organizer));

        // when / then
        assertThat(sut.isOrganizerOrOpenMember("default", 1L, "dev-user", false)).isTrue();
    }

    @Test
    void isOrganizerOrOpenMember_memberAndOpenReturnsTrue() {

        // given
        var member = MembershipRecordMother.any().role(CommunityRole.MEMBER).build();
        when(membershipLedger.find("default", 1L, "dev-user")).thenReturn(Optional.of(member));

        // when / then
        assertThat(sut.isOrganizerOrOpenMember("default", 1L, "dev-user", true)).isTrue();
    }

    @Test
    void isOrganizerOrOpenMember_memberAndClosedReturnsFalse() {

        // given
        var member = MembershipRecordMother.any().role(CommunityRole.MEMBER).build();
        when(membershipLedger.find("default", 1L, "dev-user")).thenReturn(Optional.of(member));

        // when / then
        assertThat(sut.isOrganizerOrOpenMember("default", 1L, "dev-user", false)).isFalse();
    }

    @Test
    void isOrganizerOrOpenMember_nonMemberReturnsFalse() {

        // given
        when(membershipLedger.find("default", 1L, "dev-user")).thenReturn(Optional.empty());

        // when / then
        assertThat(sut.isOrganizerOrOpenMember("default", 1L, "dev-user", true)).isFalse();
    }
}
