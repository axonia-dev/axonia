package dev.axonia.roundup.application;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.communication.api.MessageRenderPort;
import dev.axonia.communication.api.MessageType;
import dev.axonia.communication.api.RenderRequest;
import dev.axonia.communication.api.RenderedMessage;
import dev.axonia.roundup.api.spi.ArtifactActivity.CapturedArtifact;
import dev.axonia.roundup.api.spi.SessionActivity.HeldSession;
import dev.axonia.roundup.api.spi.TopicActivity.TagCount;
import dev.axonia.roundup.domain.Quarter;
import dev.axonia.roundup.domain.QuarterActivity;
import dev.axonia.roundup.domain.QuarterActivity.NamedCommunity;
import dev.axonia.roundup.domain.RoundupFacts;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verifyNoInteractions;

@MockitoSettings
class RoundupPreviewerTest {

    private static final String ORG = "acme";
    private static final String ORG_NAME = "Acme Corp";
    private static final String VIEWER = "owner-1";
    private static final String QUARTER_KEY = "2026-Q2";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-10T09:00:00Z"), ZoneOffset.UTC);

    @Mock
    private RoundupFactsAssembler assembler;
    @Mock
    private MessageRenderPort renderPort;

    private RoundupPreviewer sut;

    @BeforeEach
    void buildPreviewer() {

        sut = new RoundupPreviewer(assembler, renderPort, CLOCK);
    }

    /**
     * Once a quarter's roundup is enqueued its facts are frozen, and that frozen row is the message every recipient
     * will be shown. Re-deriving it live here would answer a question nobody asked while claiming to answer the one
     * that matters.
     */
    @Test
    void showsTheFrozenMessageWhenTheQuarterHasAlreadyBeenEnqueued() {

        var frozen = new RenderedMessage("April to June 2026 across your communities", "<p>frozen</p>");
        given(renderPort.renderEnqueued(ORG, VIEWER, MessageType.ORGANIZATION_ROUNDUP, QUARTER_KEY))
                .willReturn(Optional.of(frozen));

        var actual = sut.preview(ORG, ORG_NAME, VIEWER);

        assertThat(actual).isEqualTo(frozen);
        then(renderPort).should(never()).render(any());
        verifyNoInteractions(assembler);
    }

    @Test
    void rendersTheQuarterLiveWhenNothingIsEnqueuedForItYet() {

        var activity = anActiveQuarter();
        given(renderPort.renderEnqueued(ORG, VIEWER, MessageType.ORGANIZATION_ROUNDUP, QUARTER_KEY))
                .willReturn(Optional.empty());
        given(assembler.read(ORG, ORG_NAME, new Quarter(2026, 2))).willReturn(activity);
        given(renderPort.render(any())).willReturn(new RenderedMessage("subject", "<p>live</p>"));

        var actual = sut.preview(ORG, ORG_NAME, VIEWER);

        assertThat(actual.html()).isEqualTo("<p>live</p>");
        var request = ArgumentCaptor.forClass(RenderRequest.class);
        then(renderPort).should().render(request.capture());
        assertThat(request.getValue().organizationId()).isEqualTo(ORG);
        assertThat(request.getValue().userId()).isEqualTo(VIEWER);
        assertThat(request.getValue().type()).isEqualTo(MessageType.ORGANIZATION_ROUNDUP);
        assertThat(request.getValue().template()).isEqualTo("organization-roundup");
    }

    /**
     * The live fallback renders exactly what the producer would have enqueued, through the one shared assembly, so a
     * preview taken before the quarter is produced cannot differ from the send it stands in for.
     */
    @Test
    void theLiveFallbackRendersTheSameFactsTheProducerWouldHaveEnqueued() {

        var activity = anActiveQuarter();
        given(renderPort.renderEnqueued(ORG, VIEWER, MessageType.ORGANIZATION_ROUNDUP, QUARTER_KEY))
                .willReturn(Optional.empty());
        given(assembler.read(ORG, ORG_NAME, new Quarter(2026, 2))).willReturn(activity);
        given(renderPort.render(any())).willReturn(new RenderedMessage("subject", "<p>live</p>"));

        sut.preview(ORG, ORG_NAME, VIEWER);

        var request = ArgumentCaptor.forClass(RenderRequest.class);
        then(renderPort).should().render(request.capture());
        assertThat(request.getValue().facts()).isEqualTo(RoundupFacts.of(activity));
    }

    /**
     * The producer would enqueue nothing for this quarter - its floor is whether the letter can name a session - and
     * the preview renders it anyway, with the templates' zero arms. Deliberate, and pinned so nobody "fixes" it into a
     * refusal, and the reason the templates keep suppressing the first-timer sentence at zero sessions: the previewed
     * quarter is already one that will never be sent in the ordinary case, because the roundup ships off and this
     * endpoint exists to inform the decision to switch it on. Refusing on the floor while rendering happily for a
     * switched-off tenant would act on one of the two reasons a quarter goes unsent and ignore the commoner one.
     */
    @Test
    void rendersAQuarterBelowTheProducersActivityFloor() {

        var deadQuarter = quarterActivity().held(List.of()).captured(List.of()).build();
        given(renderPort.renderEnqueued(ORG, VIEWER, MessageType.ORGANIZATION_ROUNDUP, QUARTER_KEY))
                .willReturn(Optional.empty());
        given(assembler.read(ORG, ORG_NAME, new Quarter(2026, 2))).willReturn(deadQuarter);
        given(renderPort.render(any())).willReturn(new RenderedMessage("subject", "<p>nothing happened</p>"));

        var actual = sut.preview(ORG, ORG_NAME, VIEWER);

        assertThat(actual.html()).isEqualTo("<p>nothing happened</p>");
        var request = ArgumentCaptor.forClass(RenderRequest.class);
        then(renderPort).should().render(request.capture());
        assertThat(request.getValue().facts()).containsEntry("sessionCount", 0).containsEntry("resourceCount", 0);
    }

    /** The quarter previewed is the one a produce tick would be working on right now, keyed the same way. */
    @Test
    void previewsTheMostRecentlyClosedQuarter() {

        given(renderPort.renderEnqueued(ORG, VIEWER, MessageType.ORGANIZATION_ROUNDUP, QUARTER_KEY))
                .willReturn(Optional.of(new RenderedMessage("s", "h")));

        sut.preview(ORG, ORG_NAME, VIEWER);

        then(renderPort).should().renderEnqueued(ORG, VIEWER, MessageType.ORGANIZATION_ROUNDUP, QUARTER_KEY);
    }

    private static QuarterActivity anActiveQuarter() {

        return quarterActivity().build();
    }

    private static QuarterActivity.QuarterActivityBuilder quarterActivity() {

        return QuarterActivity.builder().organizationName(ORG_NAME).quarter(new Quarter(2026, 2))
                .communities(List.of(new NamedCommunity(7L, "Backend Guild", null, null)))
                .held(List.of(HeldSession.builder().sessionId(1L).communityId(7L).title("Hexagons in anger")
                        .heldAt(Instant.parse("2026-05-12T17:00:00Z")).talkCount(2).talkTitles(List.of())
                        .presenterNames(List.of("Alice Dubois")).build()))
                .upcoming(List.of()).firstTimePresenterCount(1)
                .captured(IntStream.range(0, 4)
                        .mapToObj(i -> new CapturedArtifact(1L, "Artifact " + i, "https://example.test/" + i,
                                "Recording"))
                        .toList())
                .topics(List.of(new TagCount("hexagonal", 2))).openTalkCount(1).communityCap(8)
                .sessionsPerCommunityCap(4).topicCap(12).communitiesUrl("https://axonia.example/communities");
    }
}
