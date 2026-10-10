package dev.axonia.events.application.port.out;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import dev.axonia.events.domain.AttendanceRecord;
import dev.axonia.events.domain.SessionAttendeeCount;

public interface AttendanceStore {

    Optional<AttendanceRecord> findByOrganizationIdAndSessionIdAndUserId(String organizationId, Long sessionId,
            String userId);

    Page<AttendanceRecord> findByOrganizationIdAndSessionId(String organizationId, Long sessionId, Pageable pageable);

    List<SessionAttendeeCount> countBySessionIdIn(String organizationId, Collection<Long> sessionIds);

    AttendanceRecord save(AttendanceRecord attendance);

    void delete(AttendanceRecord attendance);
}
