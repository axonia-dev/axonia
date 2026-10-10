package dev.axonia.shared.api.tenancy;

import java.util.List;

import org.springframework.security.authentication.AbstractAuthenticationToken;

/**
 * The authentication a system-initiated action carries when there is no request and no JWT, such as a scheduled job
 * acting on a person's own row. It grants no authorities: its only job is to name the subject whose RLS scope the work
 * runs in, which {@code PersistenceConfig.subjectResolver()} reads when the transaction acquires its connection.
 */
public final class SubjectAuthentication extends AbstractAuthenticationToken {

    private final String subject;

    public SubjectAuthentication(String subject) {
        super(List.of());
        this.subject = subject;
        setAuthenticated(true);
    }

    public String subject() {
        return subject;
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return "subject";
    }
}
