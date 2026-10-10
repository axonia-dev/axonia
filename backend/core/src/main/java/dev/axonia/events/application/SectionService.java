package dev.axonia.events.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.events.api.SectionTemplate;
import dev.axonia.events.api.Sections;
import dev.axonia.events.api.Session;
import dev.axonia.events.application.port.out.CurrentUser;
import dev.axonia.events.application.port.out.OrganizationContext;
import dev.axonia.events.application.port.out.SessionStore;
import dev.axonia.events.domain.SectionRecord;
import dev.axonia.events.domain.SessionRecord;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.NotFoundException;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SectionService implements Sections {

    private final SessionStore sessionStore;
    private final SessionMapper sessionMapper;
    private final SessionAuthorizer sessionAuthorizer;
    private final CurrentUser currentUser;
    private final OrganizationContext organizationContext;
    private final SectionTypeCatalog sectionTypeCatalog;

    @Override
    @Transactional
    public Session addSection(Long sessionId, SectionTemplate request) {

        var session = requireEditable(sessionId);
        sectionTypeCatalog.require(organizationContext.organizationId(), request.sectionTypeId());
        var sections = new ArrayList<>(SessionSections.nonNullSections(session.getSections()));
        sections.add(SectionRecord.builder().sectionTypeId(request.sectionTypeId()).title(request.title())
                .durationMinutes(request.durationMinutes()).notes(null).slots(new ArrayList<>()).build());
        return save(session, sections);
    }

    @Transactional
    public Session updateSection(Long sessionId, Long sectionId, SectionTemplate request) {

        var session = requireEditable(sessionId);
        sectionTypeCatalog.require(organizationContext.organizationId(), request.sectionTypeId());
        var existing = SessionSections.section(session, sectionId);
        var updated = existing.toBuilder().sectionTypeId(request.sectionTypeId()).title(request.title())
                .durationMinutes(request.durationMinutes()).build();
        return sessionMapper.toSession(sessionStore.save(SessionSections.replaceSection(session, sectionId, updated)));
    }

    @Transactional
    public Session removeSection(Long sessionId, Long sectionId) {

        var session = requireEditable(sessionId);
        var section = SessionSections.section(session, sectionId);
        if (!SessionSections.nonNullSlots(section.getSlots()).isEmpty()) {
            throw new ConflictException("SECTION_HAS_SLOTS", "Clear the section's topics before removing it");
        }
        var sections = SessionSections.nonNullSections(session.getSections()).stream()
                .filter(s -> !sectionId.equals(s.getId())).toList();
        return save(session, sections);
    }

    @Transactional
    public Session reorderSections(Long sessionId, ReorderSectionsRequest request) {

        var session = requireEditable(sessionId);
        var sections = SessionSections.nonNullSections(session.getSections());
        requireCompleteSectionOrder(sections, request.sectionIds());
        Map<Long, SectionRecord> byId = sections.stream().collect(Collectors.toMap(SectionRecord::getId, s -> s));
        var ordered = request.sectionIds().stream().map(byId::get).toList();
        return save(session, ordered);
    }

    private SessionRecord requireEditable(Long sessionId) {

        var session = sessionStore.findByOrganizationIdAndId(organizationContext.organizationId(), sessionId)
                .orElseThrow(() -> new NotFoundException("Session not found: " + sessionId));
        sessionAuthorizer.requireStructureEditor(session, currentUser.userId());
        // The isScheduled() gate is deliberately gone. "A held session's shape is frozen" protects against an
        // accidental rewrite, which is an interface concern: the session page keeps it read-only behind an explicit
        // Edit toggle. Holding it in the domain also forbade legitimate acts (correcting a past talk's title,
        // backfilling a community's history). Who may edit is still decided, one line up.
        return session;
    }

    private Session save(SessionRecord session, List<SectionRecord> sections) {

        return sessionMapper.toSession(sessionStore.save(session.toBuilder().sections(sections).build()));
    }

    private void requireCompleteSectionOrder(List<SectionRecord> sections, List<Long> sectionIds) {

        var expected = sections.stream().map(SectionRecord::getId).sorted().toList();
        if (!sectionIds.stream().sorted().toList().equals(expected)) {
            throw new ConflictException("SECTION_ORDER_MISMATCH", "Order must list every section exactly once");
        }
    }
}
