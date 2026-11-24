package dev.axonia.knowledge.application;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.knowledge.application.port.out.ArtifactStore;
import dev.axonia.knowledge.application.port.out.CommunityAccess;
import dev.axonia.knowledge.application.port.out.OrganizationContext;
import dev.axonia.knowledge.domain.TagCount;
import lombok.RequiredArgsConstructor;

/**
 * What a community has been sharing about lately: the tags most carried by the artifacts captured in the last
 * {@link #WINDOW}, counted over the whole library rather than a page of it. A content signal, never a member ranking.
 */
@Service
@RequiredArgsConstructor
public class TrendingTagsService {

    static final Duration WINDOW = Duration.ofDays(90);
    static final int LIMIT = 6;

    private final ArtifactStore artifactStore;
    private final CommunityAccess communityAccess;
    private final OrganizationContext organizationContext;
    private final Clock clock;

    @Transactional(readOnly = true)
    public List<TagCount> trendingTags(String slug) {

        var communityId = communityAccess.communityIdForSlug(slug);
        return artifactStore.trendingTags(organizationContext.organizationId(), communityId,
                clock.instant().minus(WINDOW), LIMIT);
    }
}
