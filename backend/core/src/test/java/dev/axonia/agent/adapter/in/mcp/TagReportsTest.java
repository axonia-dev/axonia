package dev.axonia.agent.adapter.in.mcp;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.agent.adapter.in.mcp.TagReports.MergedTagView;
import dev.axonia.agent.adapter.in.mcp.TagReports.Pending;
import dev.axonia.agent.adapter.in.mcp.TagReports.TagReportView;
import dev.axonia.community.api.Communities;
import dev.axonia.community.api.Community;
import dev.axonia.events.api.Session;
import dev.axonia.events.api.Sessions;
import dev.axonia.expertise.api.CommunityVocabulary;
import dev.axonia.expertise.api.TagSnapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@MockitoSettings
class TagReportsTest {

    private static final TagSnapshot BEFORE = new TagSnapshot(Set.of("kafka", "kubernetes", "k8s"),
            Map.of("k8s", "kubernetes"));

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
    void inCommunity_snapshotsTheSlugsCommunity_andReportsOnTheStoredTags() {
        when(communities.getBySlug("backend-guild")).thenReturn(Community.builder().id(3L).build());
        when(vocabulary.snapshot(3L)).thenReturn(BEFORE);

        var pending = sut.inCommunity("backend-guild", List.of("Azure PIM", "k8s", "a".repeat(60)));

        assertThat(pending.reportOn(List.of("azure-pim", "k8s", "kafka")))
                .as("only what was stored is reported, so a tag the write dropped is never claimed as created")
                .isEqualTo(new TagReportView(List.of("azure-pim"), List.of(new MergedTagView("k8s", "kubernetes"))));
    }

    @Test
    void inSession_snapshotsTheSessionsCommunity() {
        when(sessions.get(42L)).thenReturn(Session.builder().id(42L).communityId(3L).build());
        when(vocabulary.snapshot(3L)).thenReturn(BEFORE);

        assertThat(sut.inSession(42L, List.of("flink")).reportOn(List.of("flink")))
                .isEqualTo(new TagReportView(List.of("flink"), List.of()));
    }

    @Test
    void noTags_readNothing_andReportNothing() {
        assertThat(sut.inCommunity("backend-guild", null)).isEqualTo(Pending.NOTHING);
        assertThat(sut.inCommunity("backend-guild", List.of())).isEqualTo(Pending.NOTHING);
        assertThat(sut.inSession(42L, null)).isEqualTo(Pending.NOTHING);
        assertThat(sut.inSession(42L, List.of())).isEqualTo(Pending.NOTHING);
        assertThat(Pending.NOTHING.reportOn(List.of("kafka"))).isEqualTo(TagReportView.NONE);
        verifyNoInteractions(vocabulary, communities, sessions);
    }
}
