package dev.axonia.mentoring.adapter.out.persistence;

import java.util.List;

import org.springframework.stereotype.Component;

import dev.axonia.community.api.TagUsage;
import dev.axonia.community.api.spi.CommunityTagWriter;
import dev.axonia.mentoring.application.port.out.JournalRepository;
import dev.axonia.mentoring.application.port.out.OrganizationContext;
import lombok.RequiredArgsConstructor;

/**
 * The mentoring context's slice of a curator's rename or delete: the areas journal topics of the guild's pairings link
 * to. Deleting an area unlinks the topics and keeps their words, so nothing is lost a curator must be warned about, and
 * no usage is reported. A private journal is never a source of a tag, so a curator learns nothing of what a pairing
 * works on.
 */
@Component("mentoringTagWriter")
@RequiredArgsConstructor
class MentoringTagWriter implements CommunityTagWriter {

    private final OrganizationContext organizationContext;
    private final JournalRepository journal;

    @Override
    public List<String> tagsIn(long communityId) {

        return List.of();
    }

    @Override
    public List<TagUsage> usage(long communityId, String tag) {

        return List.of();
    }

    @Override
    public void rename(long communityId, String from, String to) {

        journal.renameArea(organizationContext.organizationId(), communityId, from, to);
    }

    @Override
    public void remove(long communityId, String tag) {

        journal.unlinkArea(organizationContext.organizationId(), communityId, tag);
    }
}
