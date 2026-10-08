package dev.axonia;

import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.*;

class OrganizationScopeTest {

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void runAsBindsTheOrganizationForTheDurationOfTheAction() {
        var seen = OrganizationScope.runAs("northwind", () -> SecurityContextHolder.getContext().getAuthentication());

        assertThat(seen).isInstanceOf(OrganizationBoundAuthentication.class);
        assertThat(((OrganizationBoundAuthentication) seen).organizationId()).isEqualTo("northwind");
    }

    @Test
    void runAsRestoresThePreviousAuthentication() {
        var previous = new UsernamePasswordAuthenticationToken("alice", "n/a");
        SecurityContextHolder.getContext().setAuthentication(previous);

        OrganizationScope.runAs("northwind", () -> "ignored");

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(previous);
    }

    @Test
    void runAsRestoresThePreviousAuthenticationEvenWhenTheActionThrows() {
        var previous = new UsernamePasswordAuthenticationToken("alice", "n/a");
        SecurityContextHolder.getContext().setAuthentication(previous);

        assertThatThrownBy(() -> OrganizationScope.runAs("northwind", () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(previous);
    }

    /**
     * The lambda is a block, not an expression: an expression lambda that yields a value is value-compatible and binds
     * to the {@code Supplier} overload instead, leaving the {@code Runnable} one unexercised.
     */
    @Test
    void runnableOverloadBindsTheOrganizationToo() {
        var seen = new String[1];

        OrganizationScope.runAs("acme", () -> {
            seen[0] = organizationFromContext();
        });

        assertThat(seen[0]).isEqualTo("acme");
    }

    @Test
    void runnableOverloadRestoresThePreviousAuthentication() {
        var previous = new UsernamePasswordAuthenticationToken("alice", "n/a");
        SecurityContextHolder.getContext().setAuthentication(previous);

        OrganizationScope.runAs("acme", () -> {
            organizationFromContext();
        });

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(previous);
    }

    private static String organizationFromContext() {
        return ((OrganizationBoundAuthentication) SecurityContextHolder.getContext().getAuthentication())
                .organizationId();
    }
}
