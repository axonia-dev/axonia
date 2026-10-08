package dev.axonia.events;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.ConflictException;
import dev.axonia.ForbiddenException;
import dev.axonia.NotFoundException;
import dev.axonia.events.internal.AttendanceEntity;
import dev.axonia.events.internal.AttendanceRepository;
import dev.axonia.events.internal.SessionEntity;
import dev.axonia.events.internal.SessionRepository;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AttendanceService {

    private final AttendanceRepository attendanceRepository;
    private final SessionRepository sessionRepository;
    private final CommunityAccess communityAccess;
    private final CurrentUser currentUser;
    private final OrganizationContext organizationContext;

    @Transactional
    public Attendance signal(Long sessionId) {

        var organizationId = organizationContext.organizationId();
        var session = session(organizationId, sessionId);
        requireMember(session.getCommunityId());

        if ("CANCELLED".equals(session.getStatus())) {
            throw new ConflictException("SESSION_CANCELLED", "Cannot signal attendance for a cancelled session");
        }
        try {
            var saved = attendanceRepository.save(AttendanceEntity.builder().organizationId(organizationId)
                    .sessionId(sessionId).userId(currentUser.userId()).build());
            return toAttendance(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("ALREADY_GOING", "You are already going to this session");
        }
    }

    @Transactional
    public void unsignal(Long sessionId) {

        var organizationId = organizationContext.organizationId();
        var attendance = attendanceRepository
                .findByOrganizationIdAndSessionIdAndUserId(organizationId, sessionId, currentUser.userId())
                .orElseThrow(() -> new NotFoundException("Attendance not found"));
        attendanceRepository.delete(attendance);
    }

    @Transactional(readOnly = true)
    public Page<Attendance> list(Long sessionId, Pageable pageable) {

        var organizationId = organizationContext.organizationId();
        var session = session(organizationId, sessionId);
        requireMember(session.getCommunityId());
        return attendanceRepository.findByOrganizationIdAndSessionId(organizationId, sessionId, pageable)
                .map(this::toAttendance);
    }

    private void requireMember(long communityId) {

        if (!communityAccess.isMember(communityId, currentUser.userId())) {
            throw new ForbiddenException("Community membership required");
        }
    }

    private SessionEntity session(String organizationId, Long sessionId) {

        return sessionRepository.findByOrganizationIdAndId(organizationId, sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found: " + sessionId));
    }

    private Attendance toAttendance(AttendanceEntity e) {

        return Attendance.builder().id(e.getId()).sessionId(e.getSessionId()).userId(e.getUserId())
                .createdAt(e.getCreatedAt()).build();
    }
}
