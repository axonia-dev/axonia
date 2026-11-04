package dev.axonia.agent.adapter.in.mcp;

import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Component;

import dev.axonia.community.api.Communities;
import dev.axonia.events.api.Sessions;
import dev.axonia.expertise.api.CommunityVocabulary;
import dev.axonia.expertise.api.TagSnapshot;
import lombok.RequiredArgsConstructor;

/**
 * What a tagging tool tells the agent about the tags it stored: those the community did not carry before, which the
 * call created, and those a curator merged into another tag. Agents backfilling a community's history tag everything
 * they create, and a tag invented on each import is how a vocabulary sprawls, so every tagging tool answers with this.
 *
 * <p>
 * The community's tags are read before the write, since afterwards every tag is carried, and the record's tags are read
 * against them after it, so the report names what was actually stored rather than what was asked for. It counts only
 * tags any member can see, so it tells nobody that a hidden declaration exists, and it is handed back only once the
 * write succeeded.
 */
@Component
@RequiredArgsConstructor
class TagReports {

    /** The steer every tagging tool's description ends with, worded once so the tools cannot drift apart on it. */
    static final String REUSE_TAGS = "Call tag.list first and reuse an existing tag rather than inventing a "
            + "near-duplicate. The result lists under tagReport the tags this call created new and any you used that "
            + "a curator merged into another tag.";

    private final CommunityVocabulary vocabulary;
    private final Communities communities;
    private final Sessions sessions;

    record MergedTagView(String tag, String canonical) {
    }

    record TagReportView(List<String> created, List<MergedTagView> merged) {

        static final TagReportView NONE = new TagReportView(List.of(), List.of());
    }

    /** A write about to happen: the community's tags as they stood before it, if the write carries any. */
    record Pending(TagSnapshot before) {

        static final Pending NOTHING = new Pending(null);

        TagReportView reportOn(Collection<String> storedTags) {
            if (before == null) {
                return TagReportView.NONE;
            }
            var reading = before.read(storedTags);
            return new TagReportView(reading.created(), reading.merged().stream()
                    .map(merged -> new MergedTagView(merged.tag(), merged.canonical())).toList());
        }
    }

    Pending inCommunity(String slug, Collection<String> requestedTags) {
        if (requestedTags == null || requestedTags.isEmpty()) {
            return Pending.NOTHING;
        }
        return new Pending(vocabulary.snapshot(communities.getBySlug(slug).id()));
    }

    Pending inSession(Long sessionId, Collection<String> requestedTags) {
        if (requestedTags == null || requestedTags.isEmpty()) {
            return Pending.NOTHING;
        }
        return new Pending(vocabulary.snapshot(sessions.get(sessionId).communityId()));
    }
}
