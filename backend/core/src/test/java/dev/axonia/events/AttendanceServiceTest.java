package dev.axonia.events;

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

import dev.axonia.ConflictException;
import dev.axonia.ForbiddenException;
import dev.axonia.NotFoundException;
import dev.axonia.events.internal.AttendanceEntity;
import dev.axonia.events.internal.AttendanceRepository;
import dev.axonia.events.internal.SessionRepository;
import dev.axonia.events.mother.SessionEntityMother;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings
class AttendanceServiceTest {

    @Mock
    private AttendanceRepository attendanceRepository;

    @Mock
    private SessionRepository sessionRepository;

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
        when(sessionRepository.findByOrganizationIdAndId("default", 9L))
                .thenReturn(Optional.of(SessionEntityMother.any().id(9L).communityId(1L).build()));
        when(attendanceRepository.save(any()))
                .thenAnswer(inv -> ((AttendanceEntity) inv.getArgument(0)).toBuilder().id(3L).build());

        // when
        var actual = sut.signal(9L);

        // then
        assertThat(actual.userId()).isEqualTo("alice");
        assertThat(actual.sessionId()).isEqualTo(9L);
    }

    @Test
    void signal_onCancelledSessionIsRejected() {

        // given
        when(sessionRepository.findByOrganizationIdAndId("default", 9L))
                .thenReturn(Optional.of(SessionEntityMother.any().id(9L).communityId(1L).status("CANCELLED").build()));

        // when / then
        assertThatThrownBy(() -> sut.signal(9L)).isInstanceOf(ConflictException.class).extracting("code")
                .isEqualTo("SESSION_CANCELLED");
    }

    @Test
    void signal_duplicateMapsToAlreadyGoing() {

        // given
        when(sessionRepository.findByOrganizationIdAndId("default", 9L))
                .thenReturn(Optional.of(SessionEntityMother.any().id(9L).communityId(1L).build()));
        when(attendanceRepository.save(any())).thenThrow(new DataIntegrityViolationException("dup"));

        // when / then
        assertThatThrownBy(() -> sut.signal(9L)).isInstanceOf(ConflictException.class).extracting("code")
                .isEqualTo("ALREADY_GOING");
    }

    @Test
    void signal_rejectsNonMember() {

        // given
        when(sessionRepository.findByOrganizationIdAndId("default", 9L))
                .thenReturn(Optional.of(SessionEntityMother.any().id(9L).communityId(1L).build()));
        when(communityAccess.isMember(1L, "alice")).thenReturn(false);

        // when / then
        assertThatThrownBy(() -> sut.signal(9L)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void signal_sessionNotFound_throwsNotFoundException() {

        // given
        when(sessionRepository.findByOrganizationIdAndId("default", 9L)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> sut.signal(9L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void unsignal_deletesExistingAttendance() {

        // given
        var attendance = AttendanceEntity.builder().id(3L).organizationId("default").sessionId(9L).userId("alice")
                .build();
        when(attendanceRepository.findByOrganizationIdAndSessionIdAndUserId("default", 9L, "alice"))
                .thenReturn(Optional.of(attendance));

        // when
        sut.unsignal(9L);

        // then
        verify(attendanceRepository).delete(attendance);
    }

    @Test
    void unsignal_attendanceNotFound_throwsNotFoundException() {

        // given
        when(attendanceRepository.findByOrganizationIdAndSessionIdAndUserId("default", 9L, "alice"))
                .thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> sut.unsignal(9L)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void list_returnsPage() {

        // given
        when(sessionRepository.findByOrganizationIdAndId("default", 9L))
                .thenReturn(Optional.of(SessionEntityMother.any().id(9L).communityId(1L).build()));
        var entity = AttendanceEntity.builder().id(1L).organizationId("default").sessionId(9L).userId("alice").build();
        when(attendanceRepository.findByOrganizationIdAndSessionId("default", 9L, Pageable.unpaged()))
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
        when(sessionRepository.findByOrganizationIdAndId("default", 9L))
                .thenReturn(Optional.of(SessionEntityMother.any().id(9L).communityId(1L).build()));
        when(communityAccess.isMember(1L, "alice")).thenReturn(false);

        // when / then
        assertThatThrownBy(() -> sut.list(9L, Pageable.unpaged())).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void list_sessionNotFound_throwsNotFoundException() {

        // given
        when(sessionRepository.findByOrganizationIdAndId("default", 9L)).thenReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> sut.list(9L, Pageable.unpaged())).isInstanceOf(NotFoundException.class);
    }
}
