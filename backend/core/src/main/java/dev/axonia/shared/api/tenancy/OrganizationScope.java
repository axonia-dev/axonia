package dev.axonia.shared.api.tenancy;

import java.util.function.Supplier;

import org.springframework.security.core.context.SecurityContextHolder;

import lombok.experimental.UtilityClass;

/**
 * Runs an action inside an Organization's RLS scope with no HTTP request and no JWT.
 *
 * <p>
 * MUST wrap the {@code @Transactional} entry point, never sit inside an open transaction: the {@code app.current_org}
 * GUC is chosen when the transaction acquires its connection, so a scope entered after that point is ignored.
 */
@UtilityClass
public class OrganizationScope {

    public <T> T runAs(String organizationId, Supplier<T> action) {

        var context = SecurityContextHolder.getContext();
        var previous = context.getAuthentication();
        context.setAuthentication(new ProvisioningAuthentication(organizationId));
        try {
            return action.get();
        } finally {
            context.setAuthentication(previous);
        }
    }

    public void runAs(String organizationId, Runnable action) {
        runAs(organizationId, () -> {
            action.run();
            return null;
        });
    }
}
