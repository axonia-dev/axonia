package dev.axonia.knowledge.application;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.knowledge.api.DefaultArtifactTypes;
import dev.axonia.knowledge.application.port.out.ArtifactTypeStore;
import dev.axonia.knowledge.domain.StoredArtifactType;
import lombok.RequiredArgsConstructor;

/** Seeds a fresh organization's artifact-type vocabulary — the defaults V1 seeded for the {@code default} org. */
@Service
@RequiredArgsConstructor
public class ArtifactTypeDefaults implements DefaultArtifactTypes {

    private record Seed(String label, String icon, String accentColor) {
    }

    private static final List<Seed> DEFAULTS = List.of(new Seed("ADR", "file-check", "#6e3ceb"),
            new Seed("Article", "newspaper", "#1aa6a0"), new Seed("Notes", "notebook-pen", "#3b82de"),
            new Seed("Recording", "circle-play", "#f2603c"), new Seed("Repo", "git-branch", "#1f9d63"),
            new Seed("Slides", "presentation", "#d9871f"));

    private final ArtifactTypeStore repository;

    @Override
    @Transactional
    public void seedFor(String organizationId) {
        DEFAULTS.forEach(seed -> repository.save(StoredArtifactType.builder().organizationId(organizationId)
                .label(seed.label()).icon(seed.icon()).accentColor(seed.accentColor()).build()));
    }
}
