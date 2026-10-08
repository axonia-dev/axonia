package dev.axonia;

import java.util.function.Supplier;

import org.springframework.security.core.context.SecurityContextHolder;

import lombok.experimental.UtilityClass;

/**
 * Runs an action inside a person's RLS scope with no HTTP request and no JWT.
 *
 * <p>
 * MUST wrap the {@code @Transactional} entry point, never sit inside an open transaction: the
 * {@code app.current_subject} GUC is chosen when the transaction acquires its connection, so a scope entered after that
 * point is ignored. This is the same trap {@link OrganizationScope} documents, and it has already been fallen into once
 * on the organization axis.
 */
@UtilityClass
public class SubjectScope {

    public <T> T runAs(String subject, Supplier<T> action) {

        var context = SecurityContextHolder.getContext();
        var previous = context.getAuthentication();
        context.setAuthentication(new SubjectAuthentication(subject));
        try {
            return action.get();
        } finally {
            context.setAuthentication(previous);
        }
    }

    public void runAs(String subject, Runnable action) {
        runAs(subject, () -> {
            action.run();
            return null;
        });
    }
}
