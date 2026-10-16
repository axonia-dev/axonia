package dev.axonia.events.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.data.domain.PageRequest;

import dev.axonia.events.api.Memory;
import dev.axonia.events.api.MemoryCursor;
import dev.axonia.events.api.MemoryTraces;
import dev.axonia.events.api.Session;
import dev.axonia.events.api.SessionMother;
import dev.axonia.events.api.SessionStatus;
import dev.axonia.events.application.port.out.CommunityAccess;
import dev.axonia.events.application.port.out.OrganizationContext;
import dev.axonia.events.application.port.out.SessionListing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@MockitoSettings
class MemoryServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-16T12:00:00Z");

    @Mock
    private SessionListing sessionListing;

    @Mock
    private CommunityAccess communityAccess;

    @Mock
    private OrganizationContext organizationContext;

    @Mock
    private MemoryTraceAssembler memoryTraceAssembler;

    private MemoryService sut;

    @BeforeEach
    void wire() {

        sut = new MemoryService(sessionListing, communityAccess, organizationContext, memoryTraceAssembler,
                Clock.fixed(NOW, ZoneOffset.UTC));
        when(organizationContext.organizationId()).thenReturn("default");
        when(communityAccess.communityIdForSlug("frontend")).thenReturn(5L);
    }

    @Test
    void firstPageReadsThePastFromNowAndPairsEachSessionWithItsTraces() {

        // given
        var newer = held(2L, "2026-10-01T18:00:00Z");
        var older = held(1L, "2026-09-01T18:00:00Z");
        when(sessionListing.page(query().build())).thenReturn(List.of(newer, older));
        var traces = Map.of(2L, new MemoryTraces(3, true, 1), 1L, new MemoryTraces(0, false, 0));
        when(memoryTraceAssembler.tracesFor("default", 5L, List.of(newer, older))).thenReturn(traces);

        // when
        var actual = sut.memories("frontend", null, 2);

        // then
        assertThat(actual.items()).containsExactly(new Memory(ScheduleEntries.of(newer), traces.get(2L)),
                new Memory(ScheduleEntries.of(older), traces.get(1L)));
        assertThat(actual.next()).isEqualTo(new MemoryCursor(older.scheduledAt(), 1L));
    }

    @Test
    void aSkippedSessionIsNoMemoryButStillAdvancesTheCursor() {

        // given
        var kept = held(2L, "2026-10-01T18:00:00Z");
        var skipped = SessionMother.any().id(1L).status(SessionStatus.SKIPPED)
                .scheduledAt(Instant.parse("2026-09-01T18:00:00Z")).build();
        when(sessionListing.page(query().build())).thenReturn(List.of(kept, skipped));
        when(memoryTraceAssembler.tracesFor("default", 5L, List.of(kept)))
                .thenReturn(Map.of(2L, new MemoryTraces(0, false, 0)));

        // when
        var actual = sut.memories("frontend", null, 2);

        // then
        assertThat(actual.items()).extracting(memory -> memory.entry().sessionId()).containsExactly(2L);
        assertThat(actual.next()).isEqualTo(new MemoryCursor(skipped.scheduledAt(), 1L));
    }

    @Test
    void aShortPageIsTheLastOne() {

        // given
        var only = held(1L, "2026-09-01T18:00:00Z");
        when(sessionListing.page(query().build())).thenReturn(List.of(only));
        when(memoryTraceAssembler.tracesFor("default", 5L, List.of(only)))
                .thenReturn(Map.of(1L, new MemoryTraces(0, false, 0)));

        // when
        var actual = sut.memories("frontend", null, 2);

        // then
        assertThat(actual.items()).hasSize(1);
        assertThat(actual.next()).isNull();
    }

    @Test
    void aCursorResumesBeforeTheLastSessionRead() {

        // given
        var at = Instant.parse("2026-09-01T18:00:00Z");
        when(sessionListing.page(query().to(at).beforeId(9L).build())).thenReturn(List.of());
        when(memoryTraceAssembler.tracesFor("default", 5L, List.of())).thenReturn(Map.of());

        // when
        var actual = sut.memories("frontend", new MemoryCursor(at, 9L), 2);

        // then
        assertThat(actual.items()).isEmpty();
        assertThat(actual.next()).isNull();
    }

    private static SessionPageQuery.SessionPageQueryBuilder query() {

        return SessionPageQuery.builder().organizationId("default").communityId(5L).window(SessionPageQuery.Window.PAST)
                .now(NOW).pageable(PageRequest.of(0, 2));
    }

    private static Session held(long id, String at) {

        return SessionMother.aHeldSession().id(id).scheduledAt(Instant.parse(at)).build();
    }
}
