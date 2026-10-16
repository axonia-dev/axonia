package dev.axonia.events.application;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.events.api.MemoryTraces;
import dev.axonia.events.api.Section;
import dev.axonia.events.api.SessionMother;
import dev.axonia.events.api.Slot;
import dev.axonia.events.api.spi.ArtifactTrace;
import dev.axonia.events.api.spi.SessionTraceAccess;
import dev.axonia.events.application.port.out.TalkLoveCounts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@MockitoSettings
class MemoryTraceAssemblerTest {

    @Mock
    private TalkLoveCounts talkLoveCounts;

    @Mock
    private SessionTraceAccess sessionTraceAccess;

    @InjectMocks
    private MemoryTraceAssembler sut;

    @Test
    void sumsTalkAndArtifactLovesAndReadsTheRecording() {

        // given
        var session = SessionMother.any().id(1L).recordingUrl("https://example.com/rec")
                .sections(List.of(section(slot(11L), slot(12L)), section(slot(13L)))).build();
        when(talkLoveCounts.lovesBySlot("default", List.of(11L, 12L, 13L))).thenReturn(Map.of(11L, 2L, 13L, 1L));
        when(sessionTraceAccess.artifactTraces("default", 5L, List.of(1L)))
                .thenReturn(Map.of(1L, new ArtifactTrace(4, 6)));

        // when
        var actual = sut.tracesFor("default", 5L, List.of(session));

        // then
        assertThat(actual).containsExactly(Map.entry(1L, new MemoryTraces(9, true, 4)));
    }

    @Test
    void aSessionThatLeftNothingHasEmptyTraces() {

        // given
        var bare = SessionMother.any().id(1L).recordingUrl("  ").sections(List.of()).build();
        var unrecorded = SessionMother.any().id(2L).recordingUrl(null).sections(List.of(section(slot(21L)))).build();
        when(talkLoveCounts.lovesBySlot("default", List.of(21L))).thenReturn(Map.of());
        when(sessionTraceAccess.artifactTraces("default", 5L, List.of(1L, 2L))).thenReturn(Map.of());

        // when
        var actual = sut.tracesFor("default", 5L, List.of(bare, unrecorded));

        // then
        assertThat(actual).containsOnly(Map.entry(1L, new MemoryTraces(0, false, 0)),
                Map.entry(2L, new MemoryTraces(0, false, 0)));
    }

    private static Section section(Slot... slots) {

        return Section.builder().slots(List.of(slots)).build();
    }

    private static Slot slot(long id) {

        return Slot.builder().id(id).build();
    }

}
