package dev.axonia.onboarding.internal;

import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;

import dev.axonia.knowledge.ArtifactFilter;
import dev.axonia.knowledge.ArtifactService;
import dev.axonia.onboarding.LovedArtifacts;
import dev.axonia.onboarding.Pick;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
class LovedArtifactsAdapter implements LovedArtifacts {

    /** The library's own "Most loved" ordering, the same string the community home's feed already asks for. */
    private static final String MOST_APPRECIATED = "mostAppreciated";

    private final ArtifactService artifacts;

    @Override
    public Optional<Pick> mostLoved(String slug) {

        var page = artifacts.search(slug, ArtifactFilter.builder().sort(MOST_APPRECIATED).build(),
                PageRequest.of(0, 1));
        return page.getContent().stream()
                // An artifact nobody has loved is not "the one everybody references", so an untouched library
                // produces no step rather than a step pointing at an arbitrary row.
                .filter(artifact -> artifact.loveCount() > 0).findFirst()
                .map(artifact -> new Pick(artifact.id(), artifact.title()));
    }
}
