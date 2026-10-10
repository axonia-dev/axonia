package dev.axonia.events.adapter.out.persistence;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import dev.axonia.events.domain.AttendanceRecord;
import dev.axonia.events.domain.SessionAttendeeCount;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings
class JdbcAttendanceStoreTest {

    private static final AttendanceEntity STORED = AttendanceEntity.builder().id(6L).organizationId("default")
            .sessionId(7L).userId("bob").createdAt(Instant.parse("2026-06-01T00:00:00Z"))
            .updatedAt(Instant.parse("2026-06-02T00:00:00Z")).version(1L).build();

    @Mock
    private AttendanceRepository attendanceRepository;

    @InjectMocks
    private JdbcAttendanceStore sut;

    @Test
    void the_mapping_round_trips_every_column() {

        // when
        var record = JdbcAttendanceStore.toRecord(STORED);

        // then
        assertThat(record).usingRecursiveComparison().isEqualTo(STORED);
        assertThat(JdbcAttendanceStore.toEntity(record)).usingRecursiveComparison().isEqualTo(STORED);
    }

    @Test
    void the_reads_map_what_the_repository_found() {

        // given
        var pageable = PageRequest.of(0, 10);
        var count = new SessionAttendeeCount(7L, 3L);
        when(attendanceRepository.findByOrganizationIdAndSessionIdAndUserId("default", 7L, "bob"))
                .thenReturn(Optional.of(STORED));
        when(attendanceRepository.findByOrganizationIdAndSessionId("default", 7L, pageable))
                .thenReturn(new PageImpl<>(List.of(STORED), pageable, 1));
        when(attendanceRepository.countBySessionIdIn("default", List.of(7L))).thenReturn(List.of(count));

        // then
        assertThat(sut.findByOrganizationIdAndSessionIdAndUserId("default", 7L, "bob")).get().usingRecursiveComparison()
                .isEqualTo(STORED);
        assertThat(sut.findByOrganizationIdAndSessionId("default", 7L, pageable).getContent())
                .extracting(AttendanceRecord::getId).containsExactly(6L);
        assertThat(sut.countBySessionIdIn("default", List.of(7L))).containsExactly(count);
    }

    @Test
    void save_and_delete_write_the_record_as_an_entity() {

        // given
        when(attendanceRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        var saved = sut.save(JdbcAttendanceStore.toRecord(STORED));
        sut.delete(JdbcAttendanceStore.toRecord(STORED));

        // then
        var captor = ArgumentCaptor.forClass(AttendanceEntity.class);
        verify(attendanceRepository).save(captor.capture());
        assertThat(captor.getValue()).usingRecursiveComparison().isEqualTo(STORED);
        assertThat(saved).usingRecursiveComparison().isEqualTo(STORED);
        verify(attendanceRepository).delete(captor.capture());
        assertThat(captor.getValue()).usingRecursiveComparison().isEqualTo(STORED);
    }
}
