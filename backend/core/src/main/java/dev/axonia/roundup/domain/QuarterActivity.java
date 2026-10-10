package dev.axonia.roundup.domain;

import java.util.List;

import dev.axonia.roundup.api.spi.ArtifactActivity.CapturedArtifact;
import dev.axonia.roundup.api.spi.SessionActivity.HeldSession;
import dev.axonia.roundup.api.spi.SessionActivity.UpcomingSession;
import dev.axonia.roundup.api.spi.TopicActivity.TagCount;
import lombok.Builder;

/**
 * Everything a roundup was able to read about one closed quarter, gathered by whoever is about to render it - the
 * producer on its way to the outbox, or the preview endpoint rendering live facts because nothing is enqueued yet.
 * {@link RoundupFacts#of} turns it into the flat fact map the template reads, and does no reading of its own, so the
 * two callers cannot end up describing the same quarter differently.
 *
 * <p>
 * A parameter object rather than a ten-argument static method: this repo's convention for anything past four, and the
 * one that keeps a caller from silently transposing two of the four counts.
 */
@Builder
public record QuarterActivity(String organizationName, Quarter quarter, List<NamedCommunity> communities,
        List<HeldSession> held, List<UpcomingSession> upcoming, int firstTimePresenterCount,
        List<CapturedArtifact> captured, List<TagCount> topics, int openTalkCount, int communityCap,
        int sessionsPerCommunityCap, int topicCap, String communitiesUrl) {

    /**
     * A community as the LETTER needs it, which is not the same shape {@code CommunityActivity} answers.
     *
     * <p>
     * <b>The difference is {@code badgeUrl}.</b> {@code CommunitySummary#badgeKey} is a storage object key - the domain
     * works in opaque keys, and {@code AbsoluteMediaLinks#urlFor} decides how one maps to a public URL - so a key
     * interpolated straight into an {@code <img src>} is a broken image in every inbox. {@code RoundupFactsAssembler}
     * resolves it once, on the way in, so no raw key can reach the fact map and no template has to know that keys and
     * URLs are different things. Null stays null: a community that never uploaded a badge falls back to its initials
     * monogram, which the shell already knows how to draw.
     *
     * <p>
     * A {@code slug} is carried by neither this record nor {@code CommunitySummary} any more, for the same reason: the
     * letter deliberately does not link a community (a roundup carries ONE call to action), so a slug at either layer
     * would be a value nothing can read and the next person would wire it into an href without noticing why it was
     * never used.
     */
    public record NamedCommunity(long id, String name, String badgeUrl, String accentColor) {
    }
}
