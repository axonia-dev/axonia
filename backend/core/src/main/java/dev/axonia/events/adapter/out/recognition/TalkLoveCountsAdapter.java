package dev.axonia.events.adapter.out.recognition;

import java.util.Collection;
import java.util.Map;

import org.springframework.stereotype.Component;

import dev.axonia.events.application.port.out.TalkLoveCounts;
import dev.axonia.recognition.api.ContributionLoveCounts;
import dev.axonia.recognition.api.ContributionType;
import lombok.RequiredArgsConstructor;

/** A talk's loves are the loves of the slot it was presented in, which recognition already counts. */
@Component
@RequiredArgsConstructor
class TalkLoveCountsAdapter implements TalkLoveCounts {

    private final ContributionLoveCounts contributionLoveCounts;

    @Override
    public Map<Long, Long> lovesBySlot(String organizationId, Collection<Long> slotIds) {

        return contributionLoveCounts.countFor(organizationId, ContributionType.SLOT_PRESENTED, slotIds);
    }
}
