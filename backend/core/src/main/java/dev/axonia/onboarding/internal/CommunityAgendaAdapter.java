package dev.axonia.onboarding.internal;

import java.time.Clock;
import java.time.Duration;
import java.util.Comparator;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import dev.axonia.events.PastSessionsRequest;
import dev.axonia.events.SessionService;
import dev.axonia.events.SessionStatus;
import dev.axonia.events.Topic;
import dev.axonia.events.TopicQuery;
import dev.axonia.events.TopicService;
import dev.axonia.events.TopicStatus;
import dev.axonia.onboarding.CommunityAgenda;
import dev.axonia.onboarding.Pick;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
class CommunityAgendaAdapter implements CommunityAgenda {

    /**
     * How far back "what happened last time" is willing to look. A session older than this is not what the community
     * last did in any useful sense, and pointing a new arrival at it would be worse than showing them nothing.
     */
    private static final Duration LOOKBACK = Duration.ofDays(365);

    /**
     * The backlog has no vote-ordered query, and adding one for this alone would put a read model in the events context
     * to serve a guide that runs once - so this samples only the first page of the default-ordered backlog. A community
     * with more than {@code BACKLOG_SAMPLE} proposed topics can therefore freeze a topic that is not actually the
     * top-voted one. Accepted, not unnoticed: the alternative is a permanent read model for a one-time read.
     */
    private static final int BACKLOG_SAMPLE = 50;

    private final SessionService sessions;

    private final TopicService topics;

    private final Clock clock;

    @Override
    public Optional<Pick> lastHeld(String slug) {

        var now = clock.instant();
        return sessions
                .past(PastSessionsRequest.builder().slug(slug).from(now.minus(LOOKBACK)).to(now)
                        .status(SessionStatus.HELD).pageable(PageRequest.of(0, 1)).build())
                .getContent().stream().findFirst().map(session -> new Pick(session.id(), session.title()));
    }

    @Override
    public Optional<Pick> nextUp(String slug) {

        return sessions.upcoming(slug, PageRequest.of(0, 1)).getContent().stream().findFirst()
                .map(session -> new Pick(session.id(), session.title()));
    }

    @Override
    public Optional<Pick> topVotedTopic(String slug) {

        var query = TopicQuery.builder().status(TopicStatus.PROPOSED).build();
        return topics.list(slug, query, PageRequest.of(0, BACKLOG_SAMPLE)).getContent().stream()
                .max(Comparator.comparing(topic -> votes(topic))).map(topic -> new Pick(topic.id(), topic.title()));
    }

    private static long votes(Topic topic) {

        return topic.voteCount() == null ? 0L : topic.voteCount();
    }
}
