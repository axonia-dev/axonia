package dev.axonia.community.internal;

import lombok.experimental.UtilityClass;

/**
 * Community-scoped slug helper — delegates to the shared {@link dev.axonia.Slugs} with a community-specific fallback.
 */
@UtilityClass
public class Slugs {

    public String slugify(String name) {
        return dev.axonia.Slugs.slugify(name, "community");
    }
}
