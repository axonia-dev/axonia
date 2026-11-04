package dev.axonia.agent.adapter.in.mcp;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.agent.adapter.in.mcp.TagReports.MergedTagView;
import dev.axonia.agent.adapter.in.mcp.TagReports.TagReportView;
import dev.axonia.community.api.Communities;
import dev.axonia.community.api.Community;
import dev.axonia.events.api.Session;
import dev.axonia.events.api.Sessions;
import dev.axonia.expertise.api.CommunityVocabulary;
import dev.axonia.expertise.api.TagReading;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@MockitoSettings
class TagReportsTest {

    @Mock
    CommunityVocabulary vocabulary;
    @Mock
    Communities communities;
    @Mock
    Sessions sessions;

    TagReports sut;

    @BeforeEach
    void setUp() {
        sut = new TagReports(vocabulary, communities, sessions);
    }

    @Test
    void inCommunity_readsTheTagsAgainstTheSlugsVocabulary() {
        when(communities.getBySlug("backend-guild")).thenReturn(Community.builder().id(3L).build());
        when(vocabulary.read(3L, List.of("Azure PIM", "k8s")))
                .thenReturn(new TagReading(List.of("azure-pim"), List.of(new TagReading.Merged("k8s", "kubernetes"))));

        var actual = sut.inCommunity("backend-guild", List.of("Azure PIM", "k8s"));

        assertThat(actual)
                .isEqualTo(new TagReportView(List.of("azure-pim"), List.of(new MergedTagView("k8s", "kubernetes"))));
    }

    @Test
    void inSession_readsTheTagsAgainstTheSessionsCommunity() {
        when(sessions.get(42L)).thenReturn(Session.builder().id(42L).communityId(3L).build());
        when(vocabulary.read(3L, List.of("kafka"))).thenReturn(new TagReading(List.of("kafka"), List.of()));

        assertThat(sut.inSession(42L, List.of("kafka"))).isEqualTo(new TagReportView(List.of("kafka"), List.of()));
    }

    @Test
    void noTags_readNothing() {
        assertThat(sut.inCommunity("backend-guild", null)).isEqualTo(TagReportView.NONE);
        assertThat(sut.inCommunity("backend-guild", List.of())).isEqualTo(TagReportView.NONE);
        assertThat(sut.inSession(42L, null)).isEqualTo(TagReportView.NONE);
        assertThat(sut.inSession(42L, List.of())).isEqualTo(TagReportView.NONE);
        verifyNoInteractions(vocabulary, communities, sessions);
    }
}
