package dev.axonia.events.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.events.application.port.out.AttendanceStore;
import dev.axonia.events.domain.AttendanceRecord;
import dev.axonia.events.domain.SessionAttendeeCount;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JdbcAttendanceStore implements AttendanceStore {

    private final AttendanceRepository attendanceRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<AttendanceRecord> findByOrganizationIdAndSessionIdAndUserId(String organizationId, Long sessionId,
            String userId) {

        return attendanceRepository.findByOrganizationIdAndSessionIdAndUserId(organizationId, sessionId, userId)
                .map(JdbcAttendanceStore::toRecord);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<AttendanceRecord> findByOrganizationIdAndSessionId(String organizationId, Long sessionId,
            Pageable pageable) {

        return attendanceRepository.findByOrganizationIdAndSessionId(organizationId, sessionId, pageable)
                .map(JdbcAttendanceStore::toRecord);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SessionAttendeeCount> countBySessionIdIn(String organizationId, Collection<Long> sessionIds) {

        return attendanceRepository.countBySessionIdIn(organizationId, sessionIds);
    }

    @Override
    @Transactional
    public AttendanceRecord save(AttendanceRecord attendance) {

        return toRecord(attendanceRepository.save(toEntity(attendance)));
    }

    @Override
    public void delete(AttendanceRecord attendance) {

        attendanceRepository.delete(toEntity(attendance));
    }

    static AttendanceRecord toRecord(AttendanceEntity entity) {

        return AttendanceRecord.builder().id(entity.getId()).organizationId(entity.getOrganizationId())
                .sessionId(entity.getSessionId()).userId(entity.getUserId()).createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt()).version(entity.getVersion()).build();
    }

    static AttendanceEntity toEntity(AttendanceRecord attendance) {

        return AttendanceEntity.builder().id(attendance.getId()).organizationId(attendance.getOrganizationId())
                .sessionId(attendance.getSessionId()).userId(attendance.getUserId())
                .createdAt(attendance.getCreatedAt()).updatedAt(attendance.getUpdatedAt())
                .version(attendance.getVersion()).build();
    }
}
