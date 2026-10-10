package dev.axonia.onboarding.application.port.out;

import java.util.Optional;

import dev.axonia.onboarding.domain.Pick;

/** The one artifact this community appreciated most, or nothing if it has captured none. */
public interface LovedArtifacts {

    Optional<Pick> mostLoved(String slug);
}
