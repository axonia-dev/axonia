package dev.axonia.shared.api.tenancy;

import org.junit.jupiter.api.*;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.*;

class SubjectScopeTest {

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void runAsBindsTheSubjectForTheDurationOfTheAction() {
        var seen = SubjectScope.runAs("subject-alice", () -> SecurityContextHolder.getContext().getAuthentication());

        assertThat(seen).isInstanceOf(SubjectAuthentication.class);
        assertThat(((SubjectAuthentication) seen).subject()).isEqualTo("subject-alice");
    }

    @Test
    void runAsRestoresThePreviousAuthentication() {
        var previous = new UsernamePasswordAuthenticationToken("alice", "n/a");
        SecurityContextHolder.getContext().setAuthentication(previous);

        SubjectScope.runAs("subject-alice", () -> "ignored");

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(previous);
    }

    @Test
    void runAsRestoresThePreviousAuthenticationEvenWhenTheActionThrows() {
        var previous = new UsernamePasswordAuthenticationToken("alice", "n/a");
        SecurityContextHolder.getContext().setAuthentication(previous);

        assertThatThrownBy(() -> SubjectScope.runAs("subject-alice", () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(previous);
    }

    /**
     * The lambda is a block, not an expression: an expression lambda that yields a value is value-compatible and binds
     * to the {@code Supplier} overload instead, leaving the {@code Runnable} one unexercised.
     */
    @Test
    void runnableOverloadBindsTheSubjectToo() {
        var seen = new String[1];

        SubjectScope.runAs("subject-bob", () -> {
            seen[0] = subjectFromContext();
        });

        assertThat(seen[0]).isEqualTo("subject-bob");
    }

    @Test
    void runnableOverloadRestoresThePreviousAuthentication() {
        var previous = new UsernamePasswordAuthenticationToken("alice", "n/a");
        SecurityContextHolder.getContext().setAuthentication(previous);

        SubjectScope.runAs("subject-bob", () -> {
            subjectFromContext();
        });

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(previous);
    }

    private static String subjectFromContext() {
        return ((SubjectAuthentication) SecurityContextHolder.getContext().getAuthentication()).subject();
    }
}
