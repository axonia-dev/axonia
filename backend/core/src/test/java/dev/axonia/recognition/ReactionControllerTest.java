package dev.axonia.recognition;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MvcTestResult;

import dev.axonia.ConflictException;
import dev.axonia.ControllerTestSupport;
import dev.axonia.CoreWebMvcTest;
import dev.axonia.FakeRateLimitStore;
import dev.axonia.ForbiddenException;
import dev.axonia.RateLimiters;
import dev.axonia.recognition.internal.ReactionImages;
import dev.axonia.recognition.internal.ReactionProposalRateLimiter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

/**
 * The rate limiter is imported real, backed by a {@link FakeRateLimitStore}, on the same precedent
 * {@code UnsubscribeControllerTest} follows: a mocked limiter would prove nothing about the budget or the 429 it
 * produces, and this is also the only place {@link ReactionProposalRateLimiter}'s own lines run.
 */
@CoreWebMvcTest(controllers = ReactionController.class)
@Import({ReactionProposalRateLimiter.class, ReactionControllerTest.RateLimitersConfig.class})
class ReactionControllerTest extends ControllerTestSupport {

    private static final String URI = "/axonia/recognition/reaction-definitions";

    @MockitoBean
    private ReactionCatalogService catalog;

    @MockitoBean
    private ReactionImages images;

    @MockitoBean
    private OrganizationContext organizationContext;

    @MockitoBean
    private CurrentUser currentUser;

    @MockitoBean
    private ReactionService reactionService;

    @Test
    void placingAReaction() {

        when(reactionService.react(any())).thenReturn(
                ReactionTally.builder().kind(ReactionKind.EMOJI).emoji("🧠").count(1).viewerHasReacted(true).build());

        var actual = mvc.post().uri("/axonia/recognition/reactions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"contributionType\":\"SLOT_PRESENTED\",\"contributionId\":100,\"kind\":\"EMOJI\","
                        + "\"emoji\":\"🧠\"}");

        actual.assertThat().hasStatus(HttpStatus.CREATED).bodyJson().extractingPath("$.count").isEqualTo(1);
    }

    @Test
    void placingAnInvalidReactionIs400() {

        var actual = mvc.post().uri("/axonia/recognition/reactions").contentType(MediaType.APPLICATION_JSON)
                .content("{\"contributionId\":100}");

        actual.assertThat().hasStatus(HttpStatus.BAD_REQUEST);
    }

    @Test
    void tallyingAContribution() {

        when(reactionService.tally(ContributionType.SLOT_PRESENTED, 100L))
                .thenReturn(List.of(ReactionTally.builder().kind(ReactionKind.EMOJI).emoji("🧠").count(2).build()));

        var actual = mvc.get().uri("/axonia/recognition/reactions").queryParam("contributionType", "SLOT_PRESENTED")
                .queryParam("contributionId", "100");

        actual.assertThat().hasStatus2xxSuccessful().bodyJson().extractingPath("$[0].count").isEqualTo(2);
    }

    @Test
    void withdrawingAReaction() {

        var actual = mvc.delete().uri(
                "/axonia/recognition/reactions?contributionType={t}&contributionId={id}" + "&kind={k}&emoji={e}",
                "SLOT_PRESENTED", 100L, "EMOJI", "🧠");

        actual.assertThat().hasStatus(HttpStatus.NO_CONTENT);
        verify(reactionService).unreact(ContributionType.SLOT_PRESENTED, 100L,
                new ReactionRef(ReactionKind.EMOJI, "🧠", null));
    }

    @Test
    void listingApprovedReactionsNeedsNoPermission() {

        when(catalog.approved()).thenReturn(List.of(ReactionDefinition.builder().id(1L).name("ship-it")
                .imageUrl("https://cdn/ship.png").pending(false).build()));

        var actual = mvc.get().uri(URI);

        actual.assertThat().hasStatus(HttpStatus.OK).bodyJson().extractingPath("$[0].name").isEqualTo("ship-it");
        actual.assertThat().bodyJson().extractingPath("$[0].pending").isEqualTo(false);
    }

    @Test
    void listingAllReactionsIncludesTheQueue() {

        when(catalog.all()).thenReturn(List.of(
                ReactionDefinition.builder().id(1L).name("ship-it").imageUrl("https://cdn/ship.png").pending(false)
                        .build(),
                ReactionDefinition.builder().id(2L).name("yak-shaving").imageUrl("https://cdn/y.png").pending(true)
                        .proposedByUserId("dev-user").build()));

        var actual = mvc.get().uri(URI + "/all");

        actual.assertThat().hasStatus(HttpStatus.OK).bodyJson().extractingPath("$.length()").isEqualTo(2);
        actual.assertThat().bodyJson().extractingPath("$[1].proposedByUserId").isEqualTo("dev-user");
    }

    @Test
    void listingOnesOwnProposalsNeedsNoPermission() {

        when(catalog.mine()).thenReturn(List.of(ReactionDefinition.builder().id(2L).name("yak-shaving")
                .imageUrl("https://cdn/y.png").pending(true).proposedByUserId("dev-user").build()));

        var actual = mvc.get().uri(URI + "/mine");

        actual.assertThat().hasStatus(HttpStatus.OK).bodyJson().extractingPath("$[0].name").isEqualTo("yak-shaving");
        actual.assertThat().bodyJson().extractingPath("$[0].pending").isEqualTo(true);
    }

    @Test
    void eligibilityReflectsWhatThePickerNeeds() {

        when(catalog.eligibility()).thenReturn(ReactionEligibility.builder().mayPropose(true).openProposals(1)
                .openProposalLimit(3).approvedLimit(60).build());

        var actual = mvc.get().uri("/axonia/recognition/reaction-eligibility");

        actual.assertThat().hasStatus(HttpStatus.OK).bodyJson().extractingPath("$.mayPropose").isEqualTo(true);
        actual.assertThat().bodyJson().extractingPath("$.openProposals").isEqualTo(1);
        actual.assertThat().bodyJson().extractingPath("$.approvedLimit").isEqualTo(60);
    }

    @Test
    void proposingUploadsThenWritesTheRow() {

        when(organizationContext.organizationId()).thenReturn("acme");
        when(currentUser.userId()).thenReturn("proposer-1");
        when(images.store(eq("acme"), any())).thenReturn("k");
        when(catalog.propose(eq("yak-shaving"), eq("k"))).thenReturn(ReactionDefinition.builder().id(2L)
                .name("yak-shaving").imageUrl("https://cdn/y.png").pending(true).build());

        var actual = proposeReaction("yak-shaving");

        actual.assertThat().hasStatus(HttpStatus.CREATED).bodyJson().extractingPath("$.pending").isEqualTo(true);
    }

    /** The blob must not outlive a proposal that never landed. */
    @Test
    void aRefusedProposalTakesItsBlobWithIt() {

        when(organizationContext.organizationId()).thenReturn("acme");
        when(currentUser.userId()).thenReturn("proposer-2");
        when(images.store(eq("acme"), any())).thenReturn("k");
        when(catalog.propose(any(), any())).thenThrow(new ConflictException("REACTION_EXISTS", "taken"));

        var actual = proposeReaction("ship-it");

        actual.assertThat().hasStatus(HttpStatus.CONFLICT);
        verify(images).deleteBlob("k");
    }

    /** {@code toUpload}'s {@code catch (IOException)}, the same shape {@code UserControllerTest} exercises. */
    @Test
    void proposingAnUnreadableImageIsRejected() {

        when(currentUser.userId()).thenReturn("proposer-3");
        var broken = new MockMultipartFile("file", "y.png", "image/png", new byte[]{1}) {

            @Override
            public byte[] getBytes() throws IOException {

                throw new IOException("simulated disk error");
            }
        };

        var actual = mvc.perform(multipart(URI).file(broken).param("name", "ship-it"));

        actual.assertThat().hasStatus(HttpStatus.BAD_REQUEST).bodyJson().extractingPath("$.code")
                .isEqualTo("INVALID_IMAGE");
    }

    /**
     * A caller holding neither the paid capability nor {@code reaction:write} must not be able to drive a write to
     * object storage, briefly readable at a public URL, before being refused. Authorization is settled first, so
     * nothing is stored and nothing has to be cleaned up afterwards.
     */
    @Test
    void proposingWithoutTheEntitlementNeverReachesTheUpload() {

        when(currentUser.userId()).thenReturn("free-member");
        doThrow(new ForbiddenException("Not open.")).when(catalog).requireMayPropose();

        var actual = proposeReaction("yak-shaving");

        actual.assertThat().hasStatus(HttpStatus.FORBIDDEN);
        verify(images, never()).store(any(), any());
        verify(images, never()).deleteBlob(any());
        verify(catalog, never()).propose(any(), any());
    }

    /** A rate-limited caller's attempt must never reach the upload or the write. */
    @Test
    void proposingWhenRateLimitedIsRefused() {

        when(organizationContext.organizationId()).thenReturn("acme");
        when(currentUser.userId()).thenReturn("proposer-4");
        when(images.store(eq("acme"), any())).thenReturn("k");
        when(catalog.propose(any(), any()))
                .thenReturn(ReactionDefinition.builder().id(3L).name("yak-shaving").pending(true).build());

        for (var attempt = 0; attempt < 10; attempt++) {
            proposeReaction("yak-" + attempt).assertThat().hasStatus(HttpStatus.CREATED);
        }

        // then - a capacity-10 bucket refilling once an hour needs 360s for its next token
        var actual = proposeReaction("yak-eleven");
        actual.assertThat().hasStatus(HttpStatus.TOO_MANY_REQUESTS).hasContentType(MediaType.APPLICATION_PROBLEM_JSON)
                .hasHeader("Retry-After", "360");
        verify(images, times(10)).store(any(), any());
    }

    @Test
    void approvingPublishesTheReaction() {

        when(catalog.approve(2L)).thenReturn(ReactionDefinition.builder().id(2L).name("yak-shaving")
                .imageUrl("https://cdn/y.png").pending(false).build());

        var actual = mvc.post().uri(URI + "/{id}/approve", 2L);

        actual.assertThat().hasStatus(HttpStatus.OK).bodyJson().extractingPath("$.pending").isEqualTo(false);
    }

    @Test
    void removingADefinitionDeletesTheBlobItPointedAt() {

        when(catalog.remove(2L)).thenReturn("reactions/acme/y.png");

        var actual = mvc.delete().uri(URI + "/{id}", 2L);

        actual.assertThat().hasStatus(HttpStatus.NO_CONTENT);
        verify(images).deleteBlob("reactions/acme/y.png");
    }

    private MvcTestResult proposeReaction(String name) {

        return mvc.perform(multipart(URI).file(new MockMultipartFile("file", "y.png", "image/png", new byte[]{1}))
                .param("name", name));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class RateLimitersConfig {

        @Bean
        RateLimiters rateLimiters() {

            return new RateLimiters(new FakeRateLimitStore(), new SimpleMeterRegistry());
        }
    }
}
