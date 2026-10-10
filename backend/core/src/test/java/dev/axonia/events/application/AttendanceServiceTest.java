package dev.axonia.events.application;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import dev.axonia.events.application.port.out.AttendanceStore;
import dev.axonia.events.application.port.out.CommunityAccess;
import dev.axonia.events.application.port.out.CurrentUser;
import dev.axonia.events.application.port.out.OrganizationContext;
import dev.axonia.events.application.port.out.SessionStore;
import dev.axonia.events.domain.AttendanceRecord;
import dev.axonia.events.domain.SessionRecordMother;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.ForbiddenException;
import dev.axonia.shared.api.error.NotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings
class AttendanceServiceTest {

    @Mock
    private AttendanceStore attendanceStore;

    @Mock
    private SessionStore sessionStore;

    @Mock
    private CommunityAccess communityAccess;

    @Mock
    private CurrentUser currentUser;

    @Mock
    private OrganizationContext organizationContext;

    @InjectMocks
    private AttendanceService sut;

    @BeforeEach
    void seedSeams() {

        when(organizationContext.organizationId()).thenReturn("default");
        lenient().when(currentUser.userId()).thenReturn("alice");
        lenient().when(communityAccess.isMember(1L, "alice")).thenReturn(true);
    }

    @Test
    void signal_createsAttendanceForMember() {

        // given
        when(sessionStore.findByOrganizationIdAndId("default", 9L))
                .thenReturn(Optional.of(SessionRecordMother.any().id(9L).communityId(1L).build()));
        when(attendanceStore.save(any()))
                .thenAnswer(inv -> ((AttendanceRecord) inv.getArgument(0)).toBuilder().id(3L).build());

        // when
        var actual = sut.signal(9L);

        // then
        assertThat(actual.userId()).isEqualTo("alice");
        assertThat(actual.sessionId()).isEqualTo(9L);
    }

    @Test
    void signal_onCancelledSessionIsRejected() {

        // given
        when(sessionStore.findByOrganizationIdAndId("default", 9L))
                .thenReturn(Optional.of(SessionRecordMother.any().id(9L).communityId(1L).status("CANCELLED").build()));

        // when / then
        assertThatThrownBy(() -> sut.signal(9L)).isInstanceOf(ConflictException.class).extracting("code")
                .isEqualTo("SESSION_CANCELLED");
    }

    @Test
    void signal_duplicateMapsToAlreadyGoing() {

        // given
        when(sessionStore.findByOrganizationIdAndId("default", 9L))
                .thenReturn(Optional.of(SessionRecordMother.any().id(9L).communityId(1L).build()));
        when(attendanceStore.save(any())).thenThrow(new DataIntegrityViolationException("dup"));

        // when / then
        assertThatThrownBy(() -> sut.signal(9L)).isInstanceOf(ConflictException.class).extracting("code")
                .isEqualTo("ALREADY_GOING");
    }

    @Test
    void signal_rejectsNonMember() {

        // given
        when(sessionStore.findByOrganizationIdAndId("default", 9L))
                .thenReturn(Optional.of(SessionRecordMother.any().id(9L).communityId(1L).build()));
        when(communityAccess.isMember(1L, "alice")).thenReturn(false);

        // when / then
        assertThatThrownBy(() -> sut.signal(9L)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void signal_sessionNotFound_throwsNotFoundException() {

        // given
        when(sessionStore.findByOrganizationIdAndId("default", 9L)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> sut.signal(9L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void unsignal_deletesExistingAttendance() {

        // given
        var attendance = AttendanceRecord.builder().id(3L).organizationId("default").sessionId(9L).userId("alice")
                .build();
        when(attendanceStore.findByOrganizationIdAndSessionIdAndUserId("default", 9L, "alice"))
                .thenReturn(Optional.of(attendance));

        // when
        sut.unsignal(9L);

        // then
        verify(attendanceStore).delete(attendance);
    }

    @Test
    void unsignal_attendanceNotFound_throwsNotFoundException() {

        // given
        when(attendanceStore.findByOrganizationIdAndSessionIdAndUserId("default", 9L, "alice"))
                .thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> sut.unsignal(9L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void list_returnsPage() {

        // given
        when(sessionStore.findByOrganizationIdAndId("default", 9L))
                .thenReturn(Optional.of(SessionRecordMother.any().id(9L).communityId(1L).build()));
        var entity = AttendanceRecord.builder().id(1L).organizationId("default").sessionId(9L).userId("alice").build();
        when(attendanceStore.findByOrganizationIdAndSessionId("default", 9L, Pageable.unpaged()))
                .thenReturn(new PageImpl<>(List.of(entity)));

        // when
        var actual = sut.list(9L, Pageable.unpaged());

        // then
        assertThat(actual.getTotalElements()).isEqualTo(1L);
        assertThat(actual.getContent().getFirst().userId()).isEqualTo("alice");
    }

    @Test
    void list_rejectsNonMember() {

        // given
        when(sessionStore.findByOrganizationIdAndId("default", 9L))
                .thenReturn(Optional.of(SessionRecordMother.any().id(9L).communityId(1L).build()));
        when(communityAccess.isMember(1L, "alice")).thenReturn(false);

        // when / then
        assertThatThrownBy(() -> sut.list(9L, Pageable.unpaged())).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void list_sessionNotFound_throwsNotFoundException() {

        // given
        when(sessionStore.findByOrganizationIdAndId("default", 9L)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> sut.list(9L, Pageable.unpaged())).isInstanceOf(NotFoundException.class);
    }
}
