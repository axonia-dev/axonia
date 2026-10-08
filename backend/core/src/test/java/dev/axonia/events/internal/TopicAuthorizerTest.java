package dev.axonia.events.internal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.axonia.ForbiddenException;
import dev.axonia.community.TopicManagementPolicy;
import dev.axonia.events.CommunityAccess;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TopicAuthorizerTest {

    @Mock
    CommunityAccess communityAccess;
    @InjectMocks
    TopicAuthorizer authorizer;

    @Test
    void organizerOnly_allowsOrganizer() {
        given(communityAccess.topicManagementPolicy(1L)).willReturn(TopicManagementPolicy.ORGANIZER_ONLY);
        given(communityAccess.isOrganizer(1L, "u1")).willReturn(true);
        assertThatCode(() -> authorizer.requireTopicManagementPermission(1L, "u1")).doesNotThrowAnyException();
    }

    @Test
    void organizerOnly_rejectsMember() {
        given(communityAccess.topicManagementPolicy(1L)).willReturn(TopicManagementPolicy.ORGANIZER_ONLY);
        given(communityAccess.isOrganizer(1L, "u1")).willReturn(false);
        assertThatThrownBy(() -> authorizer.requireTopicManagementPermission(1L, "u1"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void anyMember_allowsMember() {
        given(communityAccess.topicManagementPolicy(1L)).willReturn(TopicManagementPolicy.ANY_MEMBER);
        given(communityAccess.isMember(1L, "u1")).willReturn(true);
        assertThatCode(() -> authorizer.requireTopicManagementPermission(1L, "u1")).doesNotThrowAnyException();
    }

    @Test
    void anyMember_rejectsNonMember() {
        given(communityAccess.topicManagementPolicy(1L)).willReturn(TopicManagementPolicy.ANY_MEMBER);
        given(communityAccess.isMember(1L, "u1")).willReturn(false);
        assertThatThrownBy(() -> authorizer.requireTopicManagementPermission(1L, "u1"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void canManageInCommunity_reflectsThePolicyDirectly() {
        given(communityAccess.topicManagementPolicy(1L)).willReturn(TopicManagementPolicy.ANY_MEMBER);
        given(communityAccess.isMember(1L, "u1")).willReturn(true);
        assertThat(authorizer.canManageInCommunity(1L, "u1")).isTrue();
    }

    @Test
    void requireAssigneeOrManager_skipsThePolicyLookupForTheAssigneeItself() {
        assertThatCode(() -> authorizer.requireAssigneeOrManager(1L, "u1", "u1")).doesNotThrowAnyException();
        verify(communityAccess, never()).topicManagementPolicy(1L);
    }

    @Test
    void requireAssigneeOrManager_fallsBackToManagementPermissionForAnyoneElse() {
        given(communityAccess.topicManagementPolicy(1L)).willReturn(TopicManagementPolicy.ANY_MEMBER);
        given(communityAccess.isMember(1L, "u2")).willReturn(true);
        assertThatCode(() -> authorizer.requireAssigneeOrManager(1L, "u1", "u2")).doesNotThrowAnyException();
    }

    @Test
    void requireAssigneeOrManager_refusesAnUnrelatedNonManager() {
        given(communityAccess.topicManagementPolicy(1L)).willReturn(TopicManagementPolicy.ORGANIZER_ONLY);
        given(communityAccess.isOrganizer(1L, "u2")).willReturn(false);
        assertThatThrownBy(() -> authorizer.requireAssigneeOrManager(1L, "u1", "u2"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void assigneeOrManager_isTrueForTheAssigneeEvenWithoutManageRights() {
        assertThat(TopicAuthorizer.assigneeOrManager(false, "u1", "u1")).isTrue();
    }

    @Test
    void assigneeOrManager_isTrueForAManagerEvenWhenNotTheAssignee() {
        assertThat(TopicAuthorizer.assigneeOrManager(true, "u1", "u2")).isTrue();
    }

    @Test
    void assigneeOrManager_isFalseForNeither() {
        assertThat(TopicAuthorizer.assigneeOrManager(false, "u1", "u2")).isFalse();
    }
}
