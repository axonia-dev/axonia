package dev.axonia.agent.adapter.in.mcp;

import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Component;

import dev.axonia.community.api.Communities;
import dev.axonia.events.api.Sessions;
import dev.axonia.expertise.api.CommunityVocabulary;
import lombok.RequiredArgsConstructor;

/**
 * What a tagging tool tells the agent about the tags it stored: those the community never carried before, which the
 * call created, and those a curator merged into another tag. Agents backfilling a community's history tag everything
 * they create, and a tag invented on each import is how a vocabulary sprawls, so every tagging tool answers with this.
 *
 * <p>
 * The reading is taken before the write, since afterwards every tag is carried, and handed back only once the write
 * succeeded, so it discloses nothing a refused caller could not see.
 */
@Component
@RequiredArgsConstructor
class TagReports {

    /** The steer every tagging tool's description ends with, worded once so the tools cannot drift apart on it. */
    static final String REUSE_TAGS = "If you can curate tags, call tag.vocabulary first and reuse an existing tag "
            + "rather than inventing a near-duplicate. The result lists under tagReport the tags this call created "
            + "new and any you used that a curator merged into another tag.";

    private final CommunityVocabulary vocabulary;
    private final Communities communities;
    private final Sessions sessions;

    record MergedTagView(String tag, String canonical) {
    }

    record TagReportView(List<String> created, List<MergedTagView> merged) {

        static final TagReportView NONE = new TagReportView(List.of(), List.of());
    }

    TagReportView inCommunity(String slug, Collection<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return TagReportView.NONE;
        }
        return read(communities.getBySlug(slug).id(), tags);
    }

    TagReportView inSession(Long sessionId, Collection<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return TagReportView.NONE;
        }
        return read(sessions.get(sessionId).communityId(), tags);
    }

    private TagReportView read(long communityId, Collection<String> tags) {
        var reading = vocabulary.read(communityId, tags);
        return new TagReportView(reading.created(),
                reading.merged().stream().map(merged -> new MergedTagView(merged.tag(), merged.canonical())).toList());
    }
}
