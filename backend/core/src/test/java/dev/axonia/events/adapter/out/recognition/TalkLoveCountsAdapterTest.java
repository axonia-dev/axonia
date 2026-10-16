package dev.axonia.events.adapter.out.recognition;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.recognition.api.ContributionLoveCounts;
import dev.axonia.recognition.api.ContributionType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@MockitoSettings
class TalkLoveCountsAdapterTest {

    @Mock
    private ContributionLoveCounts contributionLoveCounts;

    @InjectMocks
    private TalkLoveCountsAdapter sut;

    @Test
    void asksRecognitionForThePresentedSlotsLoves() {

        // given
        var loves = Map.of(11L, 3L);
        when(contributionLoveCounts.countFor("default", ContributionType.SLOT_PRESENTED, List.of(11L, 12L)))
                .thenReturn(loves);

        // when
        var actual = sut.lovesBySlot("default", List.of(11L, 12L));

        // then
        assertThat(actual).isSameAs(loves);
    }
}
