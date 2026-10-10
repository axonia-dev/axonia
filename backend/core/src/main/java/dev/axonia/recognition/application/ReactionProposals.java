package dev.axonia.recognition.application;

import java.util.function.Supplier;

import org.springframework.stereotype.Service;

import dev.axonia.media.api.ImageUpload;
import dev.axonia.recognition.application.port.out.CurrentUser;
import dev.axonia.recognition.application.port.out.OrganizationContext;
import dev.axonia.recognition.domain.ReactionDefinition;
import dev.axonia.recognition.domain.ReactionRateLimitedException;
import lombok.RequiredArgsConstructor;

/**
 * Proposes a custom reaction with its image. Eligibility is settled before a byte is read or stored: the rate limiter's
 * budget accepts paying for an upload that a later validation refuses, but paying for one that an authorization refuses
 * would let a member holding neither the standing nor {@code reaction:write} drive repeated writes to object storage,
 * each briefly readable at a public URL, before being told no. {@link ReactionCatalogService#propose} keeps its own
 * identical check, so it stays safe when something other than this calls it.
 */
@Service
@RequiredArgsConstructor
public class ReactionProposals {

    private final ReactionProposalRateLimiter rateLimiter;
    private final ReactionCatalogService catalog;
    private final ReactionImages images;
    private final OrganizationContext organizationContext;
    private final CurrentUser currentUser;

    public ReactionDefinition propose(String name, Supplier<ImageUpload> upload) {

        var attempt = rateLimiter.allow(currentUser.userId());
        if (!attempt.allowed()) {
            throw new ReactionRateLimitedException(attempt);
        }
        catalog.requireMayPropose();
        var imageKey = images.store(organizationContext.organizationId(), upload.get());
        try {
            return catalog.propose(name, imageKey);
        } catch (RuntimeException e) {
            images.deleteBlob(imageKey);
            throw e;
        }
    }
}
