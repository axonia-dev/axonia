package dev.axonia.knowledge.adapter.out.events;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.events.api.ActivityTitles;
import dev.axonia.events.api.SessionHeading;
import dev.axonia.knowledge.domain.SessionLabel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.when;

@MockitoSettings
class SessionLabelsAdapterTest {

    @Mock
    private ActivityTitles activityTitles;

    @InjectMocks
    private SessionLabelsAdapter sut;

    @Test
    void of_carriesEachHeadingTheEventsContextResolves() {

        // given
        when(activityTitles.sessionHeadings(List.of(9L, 10L))).thenReturn(
                Map.of(9L, new SessionHeading("Kafka night", "Platform evenings", LocalDate.of(2026, 3, 2))));

        // when
        var actual = sut.of(List.of(9L, 10L));

        // then
        assertThat(actual).containsExactly(entry(9L, SessionLabel.builder().name("Kafka night")
                .seriesTitle("Platform evenings").heldOn(LocalDate.of(2026, 3, 2)).build()));
    }
}
