package dev.axonia.events.adapter.in.web;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import dev.axonia.ControllerTestSupport;
import dev.axonia.CoreWebMvcTest;
import dev.axonia.events.api.Memory;
import dev.axonia.events.api.MemoryCursor;
import dev.axonia.events.api.MemoryPage;
import dev.axonia.events.api.MemoryTraces;
import dev.axonia.events.api.Ribbon;
import dev.axonia.events.api.RibbonTick;
import dev.axonia.events.api.ScheduleEntry;
import dev.axonia.events.api.SessionStatus;
import dev.axonia.events.application.MemoryService;
import dev.axonia.events.application.ScheduleRibbonService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@CoreWebMvcTest(controllers = ScheduleTimelineController.class)
class ScheduleTimelineControllerTest extends ControllerTestSupport {

    private static final Instant AT = Instant.parse("2026-09-01T18:00:00Z");

    @MockitoBean
    private MemoryService memoryService;

    @MockitoBean
    private ScheduleRibbonService scheduleRibbonService;

    @Test
    void memoriesStartFromNowWithTwentyByDefault() {

        // given
        var entry = ScheduleEntry.builder().sessionId(7L).scheduledAt(AT).status(SessionStatus.HELD).build();
        when(memoryService.memories("frontend", null, 20)).thenReturn(
                new MemoryPage(List.of(new Memory(entry, new MemoryTraces(3, true, 2))), new MemoryCursor(AT, 7L)));

        // when
        var actual = mvc.get().uri("/axonia/communities/{slug}/schedule/memories", "frontend").exchange();

        // then
        assertThat(actual).hasStatus(200);
        assertThat(actual).bodyJson().extractingPath("$.items[0].entry.sessionId").isEqualTo(7);
        assertThat(actual).bodyJson().extractingPath("$.items[0].traces.loves").isEqualTo(3);
        assertThat(actual).bodyJson().extractingPath("$.items[0].traces.recorded").isEqualTo(true);
        assertThat(actual).bodyJson().extractingPath("$.items[0].traces.artifacts").isEqualTo(2);
        assertThat(actual).bodyJson().extractingPath("$.next.at").isEqualTo("2026-09-01T18:00:00Z");
        assertThat(actual).bodyJson().extractingPath("$.next.id").isEqualTo(7);
    }

    @Test
    void memoriesForwardTheCursorAndSize() {

        // given
        when(memoryService.memories("frontend", new MemoryCursor(AT, 7L), 5))
                .thenReturn(new MemoryPage(List.of(), null));

        // when
        var actual = mvc.get()
                .uri("/axonia/communities/{slug}/schedule/memories?before=2026-09-01T18:00:00Z&beforeId=7&size=5",
                        "frontend")
                .exchange();

        // then
        assertThat(actual).hasStatus(200);
        assertThat(actual).bodyJson().extractingPath("$.next").isNull();
    }

    @Test
    void aHalfCursorReadsAsTheFirstPage() {

        // given
        when(memoryService.memories("frontend", null, 20)).thenReturn(new MemoryPage(List.of(), null));

        // when
        var actual = mvc.get()
                .uri("/axonia/communities/{slug}/schedule/memories?before=2026-09-01T18:00:00Z", "frontend").exchange();

        // then
        assertThat(actual).hasStatus(200);
        verify(memoryService).memories("frontend", null, 20);
    }

    @Test
    void aSizeBelowOneIsRefused() {

        var actual = mvc.get().uri("/axonia/communities/{slug}/schedule/memories?size=0", "frontend").exchange();

        assertThat(actual).hasStatus(400);
        verifyNoInteractions(memoryService);
    }

    @Test
    void aSizeAboveFiftyIsRefused() {

        var actual = mvc.get().uri("/axonia/communities/{slug}/schedule/memories?size=51", "frontend").exchange();

        assertThat(actual).hasStatus(400);
        verifyNoInteractions(memoryService);
    }

    @Test
    void ribbon() {

        // given
        var from = Instant.parse("2026-01-01T00:00:00Z");
        var to = Instant.parse("2027-01-01T00:00:00Z");
        when(scheduleRibbonService.ribbon("frontend", from, to)).thenReturn(
                new Ribbon(Instant.parse("2024-01-01T00:00:00Z"), List.of(new RibbonTick(AT, RibbonTick.Kind.MEMORY))));

        // when
        var actual = mvc.get()
                .uri("/axonia/communities/{slug}/schedule/ribbon?from=2026-01-01T00:00:00Z&to=2027-01-01T00:00:00Z",
                        "frontend")
                .exchange();

        // then
        assertThat(actual).hasStatus(200);
        assertThat(actual).bodyJson().extractingPath("$.firstSessionAt").isEqualTo("2024-01-01T00:00:00Z");
        assertThat(actual).bodyJson().extractingPath("$.ticks[0].at").isEqualTo("2026-09-01T18:00:00Z");
        assertThat(actual).bodyJson().extractingPath("$.ticks[0].kind").isEqualTo("MEMORY");
    }
}
