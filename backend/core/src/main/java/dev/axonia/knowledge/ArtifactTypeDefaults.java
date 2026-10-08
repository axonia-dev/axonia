package dev.axonia.knowledge;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.knowledge.internal.ArtifactTypeEntity;
import dev.axonia.knowledge.internal.ArtifactTypeRepository;
import lombok.RequiredArgsConstructor;

/** Seeds a fresh organization's artifact-type vocabulary — the defaults V1 seeded for the {@code default} org. */
@Service
@RequiredArgsConstructor
public class ArtifactTypeDefaults {

    private record Seed(String label, String icon, String accentColor) {
    }

    private static final List<Seed> DEFAULTS = List.of(new Seed("ADR", "file-check", "#6e3ceb"),
            new Seed("Article", "newspaper", "#1aa6a0"), new Seed("Notes", "notebook-pen", "#3b82de"),
            new Seed("Recording", "circle-play", "#f2603c"), new Seed("Repo", "git-branch", "#1f9d63"),
            new Seed("Slides", "presentation", "#d9871f"));

    private final ArtifactTypeRepository repository;

    @Transactional
    public void seedFor(String organizationId) {
        DEFAULTS.forEach(seed -> repository.save(ArtifactTypeEntity.builder().organizationId(organizationId)
                .label(seed.label()).icon(seed.icon()).accentColor(seed.accentColor()).build()));
    }
}
