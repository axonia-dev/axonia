package dev.axonia.knowledge.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.knowledge.application.port.out.ArtifactStore;
import dev.axonia.knowledge.application.port.out.CommunityAccess;
import dev.axonia.knowledge.application.port.out.OrganizationContext;
import dev.axonia.knowledge.domain.TagCount;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@MockitoSettings
class TrendingTagsServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-24T10:00:00Z");

    @Mock
    private ArtifactStore artifactStore;
    @Mock
    private CommunityAccess communityAccess;
    @Mock
    private OrganizationContext organizationContext;

    private TrendingTagsService sut;

    @BeforeEach
    void setUp() {

        sut = new TrendingTagsService(artifactStore, communityAccess, organizationContext,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void trendingTags_countsTheWholeLibraryOverTheLastNinetyDays() {

        // given
        when(communityAccess.communityIdForSlug("eng")).thenReturn(1L);
        when(organizationContext.organizationId()).thenReturn("default");
        var tags = List.of(TagCount.builder().tag("kafka").count(12).build());
        when(artifactStore.trendingTags("default", 1L, Instant.parse("2026-08-26T10:00:00Z"), 6)).thenReturn(tags);

        // when
        var actual = sut.trendingTags("eng");

        // then
        assertThat(actual).isEqualTo(tags);
    }
}
