package dev.axonia.events.application;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import dev.axonia.community.api.ContributionPolicy;
import dev.axonia.community.api.TopicManagementPolicy;
import dev.axonia.events.api.TopicStatus;
import dev.axonia.events.application.port.out.CommunityAccess;
import dev.axonia.events.domain.TopicRecord;
import dev.axonia.events.domain.TopicRecordMother;
import dev.axonia.events.domain.TopicSpeaker;
import dev.axonia.shared.api.error.ForbiddenException;

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
        given(communityAccess.isMember(1L, "u1")).willReturn(true);
        assertThatCode(() -> authorizer.requireAssigneeOrManager(1L, "u1", "u1")).doesNotThrowAnyException();
        verify(communityAccess, never()).topicManagementPolicy(1L);
    }

    /** Being assigned work makes nobody a member, so an assignee who never joined cannot close or drop it. */
    @Test
    void requireAssigneeOrManager_refusesAnAssigneeWhoIsNotAMember() {
        given(communityAccess.isMember(1L, "u1")).willReturn(false);
        given(communityAccess.topicManagementPolicy(1L)).willReturn(TopicManagementPolicy.ANY_MEMBER);
        assertThatThrownBy(() -> authorizer.requireAssigneeOrManager(1L, "u1", "u1"))
                .isInstanceOf(ForbiddenException.class);
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

    @Test
    void canManageSpeakers_allowsAnOrganizer() {
        given(communityAccess.isOrganizer(1L, "org")).willReturn(true);
        assertThat(authorizer.canManageSpeakers(topicBy("alice"), List.of(), "org")).isTrue();
    }

    @Test
    void canManageSpeakers_allowsAScheduleManager() {
        given(communityAccess.isMember(1L, "u1")).willReturn(true);
        given(communityAccess.contributionPolicy(1L)).willReturn(new ContributionPolicy(false, false, false, true));
        assertThat(authorizer.canManageSpeakers(topicBy("alice"), List.of(), "u1")).isTrue();
    }

    @Test
    void canManageSpeakers_allowsTheCreatorOrASpeakerWhoIsAMember() {
        given(communityAccess.isMember(1L, "alice")).willReturn(true);
        given(communityAccess.isMember(1L, "bob")).willReturn(true);
        given(communityAccess.contributionPolicy(1L)).willReturn(ContributionPolicy.curated());
        assertThat(authorizer.canManageSpeakers(topicBy("alice"), List.of("bob"), "alice")).isTrue();
        assertThat(authorizer.canManageSpeakers(topicBy("alice"), List.of("bob"), "bob")).isTrue();
    }

    @Test
    void canManageSpeakers_refusesAnotherPlainMember() {
        given(communityAccess.isMember(1L, "carol")).willReturn(true);
        given(communityAccess.contributionPolicy(1L)).willReturn(ContributionPolicy.curated());
        assertThat(authorizer.canManageSpeakers(topicBy("alice"), List.of("bob"), "carol")).isFalse();
    }

    @Test
    void canManageSpeakers_isFalseOnceTheTopicLeftTheBacklog() {
        given(communityAccess.isOrganizer(1L, "org")).willReturn(true);
        assertThat(authorizer.canManageSpeakers(topicBy("alice").toBuilder().status(TopicStatus.SCHEDULED).build(),
                List.of(), "org")).isFalse();
    }

    @Test
    void canManageSpeakers_refusesACreatorWhoIsNoLongerAMember() {
        given(communityAccess.isMember(1L, "alice")).willReturn(false);
        assertThat(authorizer.canManageSpeakers(topicBy("alice"), List.of(), "alice")).isFalse();
    }

    @Test
    void requireSpeakerManager_allowsASpeakerAndRefusesAnyoneElse() {
        var topic = TopicRecordMother.any().id(5L).communityId(1L).proposerUserId("alice")
                .speakers(Set.of(TopicSpeaker.builder().userId("bob").build())).build();
        given(communityAccess.isMember(1L, "bob")).willReturn(true);
        given(communityAccess.isMember(1L, "carol")).willReturn(true);
        given(communityAccess.contributionPolicy(1L)).willReturn(ContributionPolicy.curated());
        assertThatCode(() -> authorizer.requireSpeakerManager(topic, "bob")).doesNotThrowAnyException();
        assertThatThrownBy(() -> authorizer.requireSpeakerManager(topic, "carol"))
                .isInstanceOf(ForbiddenException.class);
    }

    private static TopicRecord topicBy(String creator) {
        return TopicRecordMother.any().id(5L).communityId(1L).proposerUserId(creator).build();
    }
}
