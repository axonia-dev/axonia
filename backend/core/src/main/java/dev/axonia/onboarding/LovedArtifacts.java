package dev.axonia.onboarding;

import java.util.Optional;

/** The one artifact this community appreciated most, or nothing if it has captured none. */
public interface LovedArtifacts {

    Optional<Pick> mostLoved(String slug);
}
