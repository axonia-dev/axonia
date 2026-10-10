package dev.axonia.events.adapter.out.persistence;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.PagingAndSortingRepository;
import org.springframework.data.repository.query.Param;

import dev.axonia.events.domain.SessionAttendeeCount;

public interface AttendanceRepository
        extends
            CrudRepository<AttendanceEntity, Long>,
            PagingAndSortingRepository<AttendanceEntity, Long> {

    Optional<AttendanceEntity> findByOrganizationIdAndSessionIdAndUserId(String organizationId, Long sessionId,
            String userId);

    long countByOrganizationIdAndSessionId(String organizationId, Long sessionId);

    Page<AttendanceEntity> findByOrganizationIdAndSessionId(String organizationId, Long sessionId, Pageable pageable);

    /** Every session's attendee count in one read, so {@code SessionMapper} resolves a batch's counts once. */
    @Query("SELECT session_id, count(*) AS attendee_count FROM session_attendance "
            + "WHERE organization_id = :organizationId AND session_id IN (:sessionIds) GROUP BY session_id")
    List<SessionAttendeeCount> countBySessionIdIn(@Param("organizationId") String organizationId,
            @Param("sessionIds") Collection<Long> sessionIds);
}
