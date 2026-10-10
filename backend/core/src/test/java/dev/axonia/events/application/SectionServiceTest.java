package dev.axonia.events.application;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.events.api.SectionTemplate;
import dev.axonia.events.api.SessionMother;
import dev.axonia.events.application.port.out.CurrentUser;
import dev.axonia.events.application.port.out.OrganizationContext;
import dev.axonia.events.application.port.out.SectionTypeStore;
import dev.axonia.events.application.port.out.SessionStore;
import dev.axonia.events.domain.SectionRecord;
import dev.axonia.events.domain.SectionTypeRecordMother;
import dev.axonia.events.domain.SessionRecord;
import dev.axonia.events.domain.SessionRecordMother;
import dev.axonia.events.domain.SlotRecord;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.ForbiddenException;
import dev.axonia.shared.api.error.NotFoundException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings
class SectionServiceTest {

    @Mock
    private SessionStore sessionStore;
    @Mock
    private SessionMapper sessionMapper;
    @Mock
    private SessionAuthorizer sessionAuthorizer;
    @Mock
    private CurrentUser currentUser;
    @Mock
    private OrganizationContext organizationContext;
    @Mock
    private SectionTypeStore sectionTypeStore;

    private SectionService sut;
    private SessionRecord session;

    @BeforeEach
    void wire() {
        sut = new SectionService(sessionStore, sessionMapper, sessionAuthorizer, currentUser, organizationContext,
                new SectionTypeCatalog(sectionTypeStore));
        session = SessionRecordMother.any().id(7L).communityId(1L)
                .sections(new ArrayList<>(
                        List.of(SectionRecord.builder().id(21L).sectionTypeId(1L).slots(new ArrayList<>()).build(),
                                SectionRecord.builder().id(22L).sectionTypeId(2L).slots(new ArrayList<>()).build())))
                .build();
        when(organizationContext.organizationId()).thenReturn("t1");
        when(sessionStore.findByOrganizationIdAndId("t1", 7L)).thenReturn(Optional.of(session));
        // Every write here targets a type this Organization's catalogue actually has, unless a test says otherwise.
        lenient().when(sectionTypeStore.findByOrganizationIdAndId(eq("t1"), anyLong()))
                .thenReturn(Optional.of(SectionTypeRecordMother.any().build()));
    }

    @Test
    void addSection_appendsAtEnd() {
        when(currentUser.userId()).thenReturn("u");
        when(sessionMapper.toSession(any())).thenReturn(SessionMother.any().build());
        when(sessionStore.save(any())).thenAnswer(inv -> inv.getArgument(0));
        sut.addSection(7L, new SectionTemplate(3L, "Lightning", 15));
        var saved = ArgumentCaptor.forClass(SessionRecord.class);
        verify(sessionStore).save(saved.capture());
        var sections = saved.getValue().getSections();
        assertThat(sections).hasSize(3);
        assertThat(sections.get(2).getSectionTypeId()).isEqualTo(3L);
        assertThat(sections.get(2).getTitle()).isEqualTo("Lightning");
        verify(sessionAuthorizer).requireStructureEditor(session, "u");
    }

    @Test
    void updateSection_changesFieldsKeepingSlots() {
        session.getSections().set(0, SectionRecord.builder().id(21L).sectionTypeId(1L)
                .slots(new ArrayList<>(List.of(SlotRecord.builder().id(500L).build()))).build());
        when(currentUser.userId()).thenReturn("u");
        when(sessionMapper.toSession(any())).thenReturn(SessionMother.any().build());
        when(sessionStore.save(any())).thenAnswer(inv -> inv.getArgument(0));
        sut.updateSection(7L, 21L, new SectionTemplate(9L, "Renamed", 25));
        var saved = ArgumentCaptor.forClass(SessionRecord.class);
        verify(sessionStore).save(saved.capture());
        var s = saved.getValue().getSections().get(0);
        assertThat(s.getId()).isEqualTo(21L);
        assertThat(s.getSectionTypeId()).isEqualTo(9L);
        assertThat(s.getTitle()).isEqualTo("Renamed");
        assertThat(s.getDurationMinutes()).isEqualTo(25);
        assertThat(s.getSlots()).extracting(SlotRecord::getId).containsExactly(500L);
    }

    @Test
    void removeSection_dropsEmptySection() {
        when(currentUser.userId()).thenReturn("u");
        when(sessionMapper.toSession(any())).thenReturn(SessionMother.any().build());
        when(sessionStore.save(any())).thenAnswer(inv -> inv.getArgument(0));
        sut.removeSection(7L, 21L);
        var saved = ArgumentCaptor.forClass(SessionRecord.class);
        verify(sessionStore).save(saved.capture());
        assertThat(saved.getValue().getSections()).extracting(SectionRecord::getId).containsExactly(22L);
    }

    @Test
    void removeSection_rejectsSectionWithSlots() {
        session.getSections().set(0, SectionRecord.builder().id(21L).sectionTypeId(1L)
                .slots(new ArrayList<>(List.of(SlotRecord.builder().id(100L).build()))).build());
        when(currentUser.userId()).thenReturn("u");
        assertThatThrownBy(() -> sut.removeSection(7L, 21L)).isInstanceOf(ConflictException.class).extracting("code")
                .isEqualTo("SECTION_HAS_SLOTS");
        verify(sessionStore, never()).save(any());
    }

    @Test
    void reorderSections_reordersByGivenIds() {
        when(currentUser.userId()).thenReturn("u");
        when(sessionMapper.toSession(any())).thenReturn(SessionMother.any().build());
        when(sessionStore.save(any())).thenAnswer(inv -> inv.getArgument(0));
        sut.reorderSections(7L, new ReorderSectionsRequest(List.of(22L, 21L)));
        var saved = ArgumentCaptor.forClass(SessionRecord.class);
        verify(sessionStore).save(saved.capture());
        assertThat(saved.getValue().getSections()).extracting(SectionRecord::getId).containsExactly(22L, 21L);
    }

    @Test
    void reorderSections_rejectsMismatch() {
        when(currentUser.userId()).thenReturn("u");
        assertThatThrownBy(() -> sut.reorderSections(7L, new ReorderSectionsRequest(List.of(21L, 999L))))
                .isInstanceOf(ConflictException.class).extracting("code").isEqualTo("SECTION_ORDER_MISMATCH");
        verify(sessionStore, never()).save(any());
    }

    @Test
    void addSection_onAHeldSession_succeeds_becauseTheFreezeIsAUiDefaultNotADomainInvariant() {
        var heldSession = heldSession();
        when(sessionStore.findByOrganizationIdAndId("t1", 7L)).thenReturn(Optional.of(heldSession));
        when(currentUser.userId()).thenReturn("u");
        when(sessionMapper.toSession(any())).thenReturn(SessionMother.any().build());
        when(sessionStore.save(any())).thenAnswer(inv -> inv.getArgument(0));

        sut.addSection(7L, new SectionTemplate(3L, "Retro", 30));

        var saved = ArgumentCaptor.forClass(SessionRecord.class);
        verify(sessionStore).save(saved.capture());
        assertThat(saved.getValue().getSections()).extracting(SectionRecord::getTitle).contains("Retro");
    }

    @Test
    void addSection_onAHeldSession_stillRequiresStructureEditorRights() {
        var heldSession = heldSession();
        when(sessionStore.findByOrganizationIdAndId("t1", 7L)).thenReturn(Optional.of(heldSession));
        when(currentUser.userId()).thenReturn("u");
        doThrow(new ForbiddenException("no")).when(sessionAuthorizer).requireStructureEditor(heldSession, "u");

        assertThatThrownBy(() -> sut.addSection(7L, new SectionTemplate(3L, "Retro", 30)))
                .isInstanceOf(ForbiddenException.class);
        verify(sessionStore, never()).save(any());
    }

    @Test
    void addSection_rejectsUnknownSession() {
        when(sessionStore.findByOrganizationIdAndId("t1", 7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> sut.addSection(7L, new SectionTemplate(3L, null, null)))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void addSection_propagatesAuthorizationFailure() {
        when(currentUser.userId()).thenReturn("u");
        doThrow(new ForbiddenException("no")).when(sessionAuthorizer).requireStructureEditor(session, "u");
        assertThatThrownBy(() -> sut.addSection(7L, new SectionTemplate(3L, null, null)))
                .isInstanceOf(ForbiddenException.class);
        verify(sessionStore, never()).save(any());
    }

    /** A section type is a row in this Organization's own catalogue, not an arbitrary number. */
    @Test
    void addSection_refusesASectionTypeThatIsNotInThisOrganizationsCatalogue() {
        when(currentUser.userId()).thenReturn("u");
        when(sectionTypeStore.findByOrganizationIdAndId("t1", 999_999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut.addSection(7L, new SectionTemplate(999_999L, "Bogus", null)))
                .isInstanceOf(NotFoundException.class);
        verify(sessionStore, never()).save(any());
    }

    @Test
    void updateSection_refusesASectionTypeThatIsNotInThisOrganizationsCatalogue() {
        when(currentUser.userId()).thenReturn("u");
        when(sectionTypeStore.findByOrganizationIdAndId("t1", 999_999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> sut.updateSection(7L, 21L, new SectionTemplate(999_999L, "Bogus", null)))
                .isInstanceOf(NotFoundException.class);
        verify(sessionStore, never()).save(any());
    }

    private SessionRecord heldSession() {
        return session.toBuilder().scheduledAt(Instant.now().minus(30, ChronoUnit.DAYS)).build();
    }
}
