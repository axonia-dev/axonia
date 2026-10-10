package dev.axonia.community.domain;

import lombok.experimental.UtilityClass;

/**
 * Community-scoped slug helper — delegates to the shared {@link dev.axonia.shared.api.text.Slugs} with a
 * community-specific fallback.
 */
@UtilityClass
public class Slugs {

    public String slugify(String name) {
        return dev.axonia.shared.api.text.Slugs.slugify(name, "community");
    }
}
