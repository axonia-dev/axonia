package dev.axonia.events.application;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.events.api.Attendance;
import dev.axonia.events.api.Attendances;
import dev.axonia.events.application.port.out.AttendanceStore;
import dev.axonia.events.application.port.out.CommunityAccess;
import dev.axonia.events.application.port.out.CurrentUser;
import dev.axonia.events.application.port.out.OrganizationContext;
import dev.axonia.events.application.port.out.SessionStore;
import dev.axonia.events.domain.AttendanceRecord;
import dev.axonia.events.domain.SessionRecord;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.ForbiddenException;
import dev.axonia.shared.api.error.NotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AttendanceService implements Attendances {

    private final AttendanceStore attendanceStore;
    private final SessionStore sessionStore;
    private final CommunityAccess communityAccess;
    private final CurrentUser currentUser;
    private final OrganizationContext organizationContext;

    @Override
    @Transactional
    public Attendance signal(Long sessionId) {

        var organizationId = organizationContext.organizationId();
        var session = session(organizationId, sessionId);
        requireMember(session.getCommunityId());

        if ("CANCELLED".equals(session.getStatus())) {
            throw new ConflictException("SESSION_CANCELLED", "Cannot signal attendance for a cancelled session");
        }
        try {
            var saved = attendanceStore.save(AttendanceRecord.builder().organizationId(organizationId)
                    .sessionId(sessionId).userId(currentUser.userId()).build());
            return toAttendance(saved);
        } catch (DataIntegrityViolationException ex) {
            throw new ConflictException("ALREADY_GOING", "You are already going to this session");
        }
    }

    @Transactional
    public void unsignal(Long sessionId) {

        var organizationId = organizationContext.organizationId();
        var attendance = attendanceStore
                .findByOrganizationIdAndSessionIdAndUserId(organizationId, sessionId, currentUser.userId())
                .orElseThrow(() -> new NotFoundException("Attendance not found"));
        attendanceStore.delete(attendance);
    }

    @Transactional(readOnly = true)
    public Page<Attendance> list(Long sessionId, Pageable pageable) {

        var organizationId = organizationContext.organizationId();
        var session = session(organizationId, sessionId);
        requireMember(session.getCommunityId());
        return attendanceStore.findByOrganizationIdAndSessionId(organizationId, sessionId, pageable)
                .map(this::toAttendance);
    }

    private void requireMember(long communityId) {

        if (!communityAccess.isMember(communityId, currentUser.userId())) {
            throw new ForbiddenException("Community membership required");
        }
    }

    private SessionRecord session(String organizationId, Long sessionId) {

        return sessionStore.findByOrganizationIdAndId(organizationId, sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found: " + sessionId));
    }

    private Attendance toAttendance(AttendanceRecord e) {

        return Attendance.builder().id(e.getId()).sessionId(e.getSessionId()).userId(e.getUserId())
                .createdAt(e.getCreatedAt()).build();
    }
}
