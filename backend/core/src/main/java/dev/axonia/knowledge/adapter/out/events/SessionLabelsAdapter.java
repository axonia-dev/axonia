package dev.axonia.knowledge.adapter.out.events;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import dev.axonia.events.api.ActivityTitles;
import dev.axonia.events.api.SessionHeading;
import dev.axonia.knowledge.application.port.out.SessionLabels;
import dev.axonia.knowledge.domain.SessionLabel;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
class SessionLabelsAdapter implements SessionLabels {

    private final ActivityTitles activityTitles;

    @Override
    public Map<Long, SessionLabel> of(Collection<Long> sessionIds) {

        return activityTitles.sessionHeadings(sessionIds).entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> label(entry.getValue())));
    }

    private static SessionLabel label(SessionHeading heading) {

        return SessionLabel.builder().name(heading.name()).seriesTitle(heading.seriesTitle()).heldOn(heading.heldOn())
                .build();
    }
}
