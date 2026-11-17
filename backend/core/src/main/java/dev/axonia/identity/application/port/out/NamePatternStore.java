package dev.axonia.identity.application.port.out;

import dev.axonia.identity.domain.NamePatterns;

/** An Organization's ordered name patterns. An Organization that declared none holds {@link NamePatterns#NONE}. */
public interface NamePatternStore {

    NamePatterns find(String organizationId);

    void replace(String organizationId, NamePatterns patterns);
}
