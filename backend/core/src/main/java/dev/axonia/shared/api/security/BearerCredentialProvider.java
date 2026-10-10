package dev.axonia.shared.api.security;

import org.springframework.security.authentication.AuthenticationProvider;

/**
 * An axonia-issued bearer credential with a prefix a JWT can never start with (a JWT is Base64URL and begins
 * {@code eyJ}). A module that issues one contributes a bean, and the customer chain routes the prefix to it.
 */
public interface BearerCredentialProvider {

    String prefix();

    AuthenticationProvider provider();
}
