package dev.axonia.events.application;

import java.util.Arrays;
import java.util.Optional;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.community.api.ContributionPolicy;
import dev.axonia.events.application.port.out.CommunityAccess;
import dev.axonia.events.application.port.out.TopicStore;
import dev.axonia.events.domain.SessionRecord;
import dev.axonia.events.domain.SessionRecordMother;
import dev.axonia.events.domain.SlotPresenter;
import dev.axonia.events.domain.SlotRecord;
import dev.axonia.events.domain.TopicRecord;
import dev.axonia.shared.api.error.ForbiddenException;
import dev.axonia.shared.api.error.NotFoundException;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@MockitoSettings
class SessionAuthorizerTest {

    @Mock
    private CommunityAccess communityAccess;

    @Mock
    private TopicStore topicStore;

    @InjectMocks
    private SessionAuthorizer sut;

    private static SessionRecord session() {

        return SessionRecordMother.any().id(7L).communityId(1L).build();
    }

    @Test
    void requireOrganizer_allowsOrganizer() {

        // given
        when(communityAccess.isOrganizer(1L, "org")).thenReturn(true);

        // then
        assertThatCode(() -> sut.requireOrganizer(session(), "org")).doesNotThrowAnyException();
    }

    @Test
    void requireOrganizer_rejectsOther() {

        // given
        when(communityAccess.isOrganizer(1L, "nobody")).thenReturn(false);

        // then
        assertThatThrownBy(() -> sut.requireOrganizer(session(), "nobody")).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void requireCommunicationManager_allowsWhoeverHoldsTheAxis() {

        // given
        when(communityAccess.canManageCommunication(1L, "u")).thenReturn(true);

        // then
        assertThatCode(() -> sut.requireCommunicationManager(session(), "u")).doesNotThrowAnyException();
    }

    @Test
    void requireCommunicationManager_rejectsWhoeverDoesNot() {

        // given
        when(communityAccess.canManageCommunication(1L, "u")).thenReturn(false);

        // then
        assertThatThrownBy(() -> sut.requireCommunicationManager(session(), "u"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void requireCommunityMatches_allowsTheSessionsOwnCommunity() {

        // then
        assertThatCode(() -> sut.requireCommunityMatches(session(), 1L)).doesNotThrowAnyException();
    }

    @Test
    void requireCommunityMatches_rejectsADifferentCommunity() {

        // then
        assertThatThrownBy(() -> sut.requireCommunityMatches(session(), 2L)).isInstanceOf(NotFoundException.class);
    }

    private static SlotRecord slot(Long topicId, String... presenters) {

        var refs = Arrays.stream(presenters).map(p -> SlotPresenter.builder().userId(p).build())
                .collect(Collectors.toSet());
        return SlotRecord.builder().id(11L).topicId(topicId).presenters(refs).build();
    }

    @Test
    void requireSlotOwnerOrManager_allowsPresenter() {

        // then
        assertThatCode(() -> sut.requireSlotOwnerOrManager("t1", session(), slot(null, "u1"), "u1"))
                .doesNotThrowAnyException();
    }

    @Test
    void requireSlotOwnerOrManager_allowsProposer() {

        // given
        when(topicStore.findByOrganizationIdAndId("t1", 5L))
                .thenReturn(Optional.of(TopicRecord.builder().id(5L).proposerUserId("u2").build()));

        // then
        assertThatCode(() -> sut.requireSlotOwnerOrManager("t1", session(), slot(5L), "u2")).doesNotThrowAnyException();
    }

    @Test
    void requireSlotOwnerOrManager_allowsOrganizer() {

        // given
        when(communityAccess.isOrganizer(1L, "org")).thenReturn(true);

        // then
        assertThatCode(() -> sut.requireSlotOwnerOrManager("t1", session(), slot(null, "other"), "org"))
                .doesNotThrowAnyException();
    }

    @Test
    void requireSlotOwnerOrManager_allowsMemberWithManageSchedule() {

        // given
        when(communityAccess.isOrganizer(1L, "u3")).thenReturn(false);
        when(communityAccess.contributionPolicy(1L)).thenReturn(new ContributionPolicy(false, false, false, true));
        when(communityAccess.isMember(1L, "u3")).thenReturn(true);

        // then
        assertThatCode(() -> sut.requireSlotOwnerOrManager("t1", session(), slot(null, "other"), "u3"))
                .doesNotThrowAnyException();
    }

    @Test
    void requireSlotOwnerOrManager_rejectsPlainMember() {

        // given — curated() has canManageSchedule=false so isMember is never reached
        when(communityAccess.isOrganizer(1L, "u4")).thenReturn(false);
        when(communityAccess.contributionPolicy(1L)).thenReturn(ContributionPolicy.curated());

        // then
        assertThatThrownBy(() -> sut.requireSlotOwnerOrManager("t1", session(), slot(null, "other"), "u4"))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void requireSlotOwnerOrManager_handlesNullPresenters() {

        // given
        var slotNoPresenters = SlotRecord.builder().id(11L).topicId(null).presenters(null).build();
        when(communityAccess.isOrganizer(1L, "org")).thenReturn(true);

        // then
        assertThatCode(() -> sut.requireSlotOwnerOrManager("t1", session(), slotNoPresenters, "org"))
                .doesNotThrowAnyException();
    }

    @Test
    void requireScheduleManager_allowsOrganizer() {

        // given
        when(communityAccess.isOrganizer(1L, "org")).thenReturn(true);

        // then
        assertThatCode(() -> sut.requireScheduleManager(1L, "org")).doesNotThrowAnyException();
    }

    @Test
    void requireScheduleManager_allowsMemberWithManageSchedule() {

        // given
        when(communityAccess.isOrganizer(1L, "u3")).thenReturn(false);
        when(communityAccess.contributionPolicy(1L)).thenReturn(new ContributionPolicy(false, false, false, true));
        when(communityAccess.isMember(1L, "u3")).thenReturn(true);

        // then
        assertThatCode(() -> sut.requireScheduleManager(1L, "u3")).doesNotThrowAnyException();
    }

    @Test
    void requireScheduleManager_rejectsPlainMember() {

        // given - curated() has canManageSchedule=false so isMember is never reached
        when(communityAccess.isOrganizer(1L, "u4")).thenReturn(false);
        when(communityAccess.contributionPolicy(1L)).thenReturn(ContributionPolicy.curated());

        // then
        assertThatThrownBy(() -> sut.requireScheduleManager(1L, "u4")).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void requireScheduleManager_rejectsNonMemberEvenWhenPolicyOpen() {

        // given
        when(communityAccess.isOrganizer(1L, "u")).thenReturn(false);
        when(communityAccess.contributionPolicy(1L)).thenReturn(new ContributionPolicy(false, false, false, true));
        when(communityAccess.isMember(1L, "u")).thenReturn(false);

        // then
        assertThatThrownBy(() -> sut.requireScheduleManager(1L, "u")).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void requireStructureEditor_allowsOrganizer() {

        // given
        when(communityAccess.isOrganizer(1L, "u")).thenReturn(true);

        // then
        assertThatCode(() -> sut.requireStructureEditor(session(), "u")).doesNotThrowAnyException();
    }

    @Test
    void requireStructureEditor_allowsMemberWhenPolicyOpen() {

        // given
        when(communityAccess.isOrganizer(1L, "u")).thenReturn(false);
        when(communityAccess.contributionPolicy(1L)).thenReturn(new ContributionPolicy(false, true, false, false));
        when(communityAccess.isMember(1L, "u")).thenReturn(true);

        // then
        assertThatCode(() -> sut.requireStructureEditor(session(), "u")).doesNotThrowAnyException();
    }

    @Test
    void requireStructureEditor_rejectsWhenPolicyClosed() {

        // given
        when(communityAccess.isOrganizer(1L, "u")).thenReturn(false);
        when(communityAccess.contributionPolicy(1L)).thenReturn(new ContributionPolicy(false, false, false, false));

        // then
        assertThatThrownBy(() -> sut.requireStructureEditor(session(), "u")).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void requireStructureEditor_rejectsNonMemberEvenWhenPolicyOpen() {

        // given
        when(communityAccess.isOrganizer(1L, "u")).thenReturn(false);
        when(communityAccess.contributionPolicy(1L)).thenReturn(new ContributionPolicy(false, true, false, false));
        when(communityAccess.isMember(1L, "u")).thenReturn(false);

        // then
        assertThatThrownBy(() -> sut.requireStructureEditor(session(), "u")).isInstanceOf(ForbiddenException.class);
    }
}
