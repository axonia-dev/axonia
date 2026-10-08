package dev.axonia.expertise.internal;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.axonia.events.SlotAttendanceReader;
import dev.axonia.expertise.SourceRef;
import dev.axonia.expertise.SourceType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SourceReachResolverAdapterTest {

    private final SlotAttendanceReader attendance = mock(SlotAttendanceReader.class);

    private final SourceReachResolverAdapter sut = new SourceReachResolverAdapter(attendance);

    @Test
    void reachFor_resolvesSlotRefs() {

        when(attendance.attendanceBySlot(List.of(11L))).thenReturn(Map.of(11L, 7));

        assertThat(sut.reachFor(List.of(new SourceRef(SourceType.SLOT, 11L))))
                .containsOnly(entry(new SourceRef(SourceType.SLOT, 11L), 7));
    }

    @Test
    void reachFor_ignoresEverySourceTypeButSlot() {

        sut.reachFor(List.of(new SourceRef(SourceType.ARTIFACT, 1L), new SourceRef(SourceType.TOPIC, 2L),
                new SourceRef(SourceType.ACTION_ITEM, 3L)));

        verify(attendance, never()).attendanceBySlot(anyCollection());
    }

    @Test
    void reachFor_slotTheReaderDidNotResolve_isAbsent() {

        when(attendance.attendanceBySlot(List.of(11L))).thenReturn(Map.of());

        assertThat(sut.reachFor(List.of(new SourceRef(SourceType.SLOT, 11L)))).isEmpty();
    }

    @Test
    void reachFor_deduplicatesRepeatedSlots() {

        when(attendance.attendanceBySlot(List.of(11L))).thenReturn(Map.of(11L, 7));

        var refs = List.of(new SourceRef(SourceType.SLOT, 11L), new SourceRef(SourceType.SLOT, 11L));

        assertThat(sut.reachFor(refs)).hasSize(1);
    }

    @Test
    void reachFor_noRefs_asksNothing() {

        assertThat(sut.reachFor(List.of())).isEmpty();
        verify(attendance, never()).attendanceBySlot(anyCollection());
    }
}
