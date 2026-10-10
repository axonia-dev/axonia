package dev.axonia.onboarding.adapter.out.events;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.springframework.data.domain.PageImpl;

import dev.axonia.events.api.PastSessionsRequest;
import dev.axonia.events.api.Session;
import dev.axonia.events.api.SessionStatus;
import dev.axonia.events.api.Sessions;
import dev.axonia.events.api.Topic;
import dev.axonia.events.api.Topics;
import dev.axonia.onboarding.domain.Pick;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@MockitoSettings
class CommunityAgendaAdapterTest {

    @Mock
    private Sessions sessions;

    @Mock
    private Topics topics;

    @Mock
    private Clock clock;

    @InjectMocks
    private CommunityAgendaAdapter adapter;

    @Test
    void picksTheTopicWithTheMostVotes() {

        when(topics.list(eq("guild"), any(), any())).thenReturn(
                new PageImpl<>(List.of(Topic.builder().id(1L).title("one build cache").voteCount(9L).build(),
                        Topic.builder().id(2L).title("flaky tests").voteCount(2L).build())));

        assertThat(adapter.topVotedTopic("guild")).contains(new Pick(1L, "one build cache"));
    }

    @Test
    void treatsATopicWithNoVoteCountAsZeroRatherThanFailing() {

        when(topics.list(eq("guild"), any(), any()))
                .thenReturn(new PageImpl<>(List.of(Topic.builder().id(1L).title("unvoted").voteCount(null).build(),
                        Topic.builder().id(2L).title("voted once").voteCount(1L).build())));

        assertThat(adapter.topVotedTopic("guild")).contains(new Pick(2L, "voted once"));
    }

    @Test
    void offersNothingWhenTheBacklogIsEmpty() {

        when(topics.list(eq("guild"), any(), any())).thenReturn(new PageImpl<>(List.of()));

        assertThat(adapter.topVotedTopic("guild")).isEmpty();
    }

    @Test
    void lastHeldPointsAtTheMostRecentPastSession() {

        when(clock.instant()).thenReturn(Instant.parse("2026-09-12T00:00:00Z"));
        when(sessions.past(any(PastSessionsRequest.class))).thenReturn(new PageImpl<>(List.of(Session.builder().id(5L)
                .title("Retro: what broke in Q3").occurrenceDate(LocalDate.of(2026, 8, 1)).build())));

        assertThat(adapter.lastHeld("guild")).contains(new Pick(5L, "Retro: what broke in Q3"));
    }

    /**
     * The method is named for what it promises. A past page is every non-cancelled occurrence whose hour has passed, so
     * without the status the onboarding nudge would point a newcomer at a session the community skipped.
     */
    @Test
    void lastHeldAsksForHeldSessionsOnly() {

        when(clock.instant()).thenReturn(Instant.parse("2026-09-12T00:00:00Z"));
        when(sessions.past(any(PastSessionsRequest.class))).thenReturn(new PageImpl<>(List.of()));

        adapter.lastHeld("guild");

        var captor = ArgumentCaptor.forClass(PastSessionsRequest.class);
        verify(sessions).past(captor.capture());
        assertThat(captor.getValue().status()).isEqualTo(SessionStatus.HELD);
        assertThat(captor.getValue().slug()).isEqualTo("guild");
    }

    @Test
    void nextUpPointsAtTheNextUpcomingSession() {

        when(sessions.upcoming(eq("guild"), any())).thenReturn(new PageImpl<>(List.of(Session.builder().id(6L)
                .title("Deploying with confidence").occurrenceDate(LocalDate.of(2026, 10, 1)).build())));

        assertThat(adapter.nextUp("guild")).contains(new Pick(6L, "Deploying with confidence"));
    }
}
