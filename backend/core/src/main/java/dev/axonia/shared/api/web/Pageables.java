package dev.axonia.shared.api.web;

import java.util.Set;

import org.springframework.data.domain.Pageable;

import dev.axonia.shared.api.error.ValidationException;

/**
 * Transport-level guards for client-supplied {@link Pageable}s. Derived Spring Data queries splice the sort property
 * into SQL identifier position, so paged endpoints allowlist the properties they can order by instead of relying on
 * framework identifier quoting.
 */
public final class Pageables {

    private Pageables() {
    }

    /**
     * Returns the pageable unchanged when every sort property is allowlisted; rejects anything else.
     */
    public static Pageable requireSortIn(Pageable pageable, Set<String> allowedProperties) {

        pageable.getSort().forEach(order -> {
            if (!allowedProperties.contains(order.getProperty())) {
                throw new ValidationException("unsupported_sort", "Unsupported sort property: " + order.getProperty());
            }
        });
        return pageable;
    }
}
