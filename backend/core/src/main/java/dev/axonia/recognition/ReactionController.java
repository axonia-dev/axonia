package dev.axonia.recognition;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import dev.axonia.media.ImageUploads;
import dev.axonia.recognition.internal.ReactionImages;
import dev.axonia.recognition.internal.ReactionProposalRateLimiter;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/${spring.application.name}/recognition")
@RequiredArgsConstructor
public class ReactionController {

    private final ReactionCatalogService catalog;

    private final ReactionImages images;

    private final OrganizationContext organizationContext;

    private final CurrentUser currentUser;

    private final ReactionProposalRateLimiter proposalRateLimiter;

    private final ReactionService reactionService;

    @GetMapping("/reactions")
    List<ReactionTally> tally(@RequestParam ContributionType contributionType, @RequestParam Long contributionId) {

        return reactionService.tally(contributionType, contributionId);
    }

    @PostMapping("/reactions")
    @ResponseStatus(HttpStatus.CREATED)
    ReactionTally react(@RequestBody @Valid ReactionRequest request) {

        return reactionService.react(request);
    }

    @DeleteMapping("/reactions")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void unreact(@Valid @ModelAttribute UnreactRequest request) {

        reactionService.unreact(request.contributionType(), request.contributionId(),
                new ReactionRef(request.kind(), request.emoji(), request.definitionId()));
    }

    /** Every member needs this to react at all, so it is ungated reference data. */
    @GetMapping("/reaction-definitions")
    List<ReactionDefinition> approved() {

        return catalog.approved();
    }

    /** The administration view, queue included. Gated on {@code reaction:read} inside the service. */
    @GetMapping("/reaction-definitions/all")
    List<ReactionDefinition> all() {

        return catalog.all();
    }

    /**
     * The caller's own waiting proposals, so a Withdraw has something to sit beside. Ungated for the reason
     * {@link ReactionCatalogService#mine()} carries: it answers with nothing but rows the caller proposed.
     */
    @GetMapping("/reaction-definitions/mine")
    List<ReactionDefinition> mine() {

        return catalog.mine();
    }

    @GetMapping("/reaction-eligibility")
    ReactionEligibility eligibility() {

        return catalog.eligibility();
    }

    /**
     * Eligibility is settled before a byte is stored. The rate limiter's own budget accepts paying for an upload that a
     * later <em>validation</em> refuses; paying for one that an <em>authorization</em> refuses is a different thing,
     * and would let a member holding neither the standing nor {@code reaction:write} drive repeated writes to object
     * storage, each briefly readable at a public URL, before being told no. {@code propose} keeps its own identical
     * check, so the service is still safe when something other than this controller calls it.
     */
    @PostMapping(path = "/reaction-definitions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    ReactionDefinition propose(@RequestParam("name") String name, @RequestParam("file") MultipartFile file) {

        var attempt = proposalRateLimiter.allow(currentUser.userId());
        if (!attempt.allowed()) {
            throw new ReactionRateLimitedException(attempt);
        }
        catalog.requireMayPropose();
        var imageKey = images.store(organizationContext.organizationId(), ImageUploads.from(file));
        try {
            return catalog.propose(name, imageKey);
        } catch (RuntimeException e) {
            // The row never landed, so the blob is an orphan the moment this throws.
            images.deleteBlob(imageKey);
            throw e;
        }
    }

    @PostMapping("/reaction-definitions/{id}/approve")
    ReactionDefinition approve(@PathVariable Long id) {

        return catalog.approve(id);
    }

    /** Decline, withdraw and remove are one act with three names; the service decides which the caller may do. */
    @DeleteMapping("/reaction-definitions/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void remove(@PathVariable Long id) {

        images.deleteBlob(catalog.remove(id));
    }
}
