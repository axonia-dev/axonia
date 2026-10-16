package dev.axonia.recognition.application;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.recognition.api.ContributionType;
import dev.axonia.recognition.application.port.out.LoveTallies;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@MockitoSettings
class LoveCountServiceTest {

    @Mock
    private LoveTallies loveTallies;

    @InjectMocks
    private LoveCountService sut;

    @Test
    void emptyIdsAskNothing() {

        // when
        var actual = sut.countFor("default", ContributionType.SLOT_PRESENTED, List.of());

        // then
        assertThat(actual).isEmpty();
        verifyNoInteractions(loveTallies);
    }

    @Test
    void delegatesTheIds() {

        // given
        when(loveTallies.countFor("default", ContributionType.SLOT_PRESENTED, List.of(7L))).thenReturn(Map.of(7L, 3L));

        // when
        var actual = sut.countFor("default", ContributionType.SLOT_PRESENTED, List.of(7L));

        // then
        assertThat(actual).containsEntry(7L, 3L);
    }
}
