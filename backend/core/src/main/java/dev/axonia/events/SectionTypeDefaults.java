package dev.axonia.events;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.events.internal.SectionTypeEntity;
import dev.axonia.events.internal.SectionTypeRepository;
import lombok.RequiredArgsConstructor;

/** Seeds a fresh organization's section-type vocabulary — the defaults V4 seeded for the {@code default} org. */
@Service
@RequiredArgsConstructor
public class SectionTypeDefaults {

    private record Seed(String label, boolean expectsPresenter, String icon, String accentColor) {
    }

    private static final List<Seed> DEFAULTS = List.of(new Seed("Ice Breaker", false, "snowflake", "#3b82de"),
            new Seed("Lightning Talk", true, "zap", "#d9871f"), new Seed("Deep Dive", true, "telescope", "#6e3ceb"),
            new Seed("Round Table", false, "messages-square", "#1f9d63"),
            new Seed("Q&A", false, "help-circle", "#3b82de"));

    private final SectionTypeRepository repository;

    @Transactional
    public void seedFor(String organizationId) {
        DEFAULTS.forEach(seed -> repository.save(SectionTypeEntity.builder().organizationId(organizationId)
                .label(seed.label()).expectsPresenter(seed.expectsPresenter()).icon(seed.icon())
                .accentColor(seed.accentColor()).build()));
    }
}
