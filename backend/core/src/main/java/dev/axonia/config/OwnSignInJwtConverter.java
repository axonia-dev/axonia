package dev.axonia.config;

import org.jspecify.annotations.Nullable;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import dev.axonia.CurrentToken;

/**
 * Refuses a token from the identity provider that carries the RFC 8693 {@code act} claim, then converts it with the
 * edition's converter, or as the resource server's default would where an edition supplies none. Only a session an
 * edition mints in-process, never decoded, may act for somebody, which is what {@link CurrentToken#isDelegated()}
 * relies on.
 */
final class OwnSignInJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final Converter<Jwt, ? extends AbstractAuthenticationToken> delegate;

    OwnSignInJwtConverter(@Nullable Converter<Jwt, ? extends AbstractAuthenticationToken> delegate) {

        this.delegate = delegate == null ? new JwtAuthenticationConverter() : delegate;
    }

    @Override
    public AbstractAuthenticationToken convert(Jwt source) {

        if (source.hasClaim(CurrentToken.ACTOR_CLAIM)) {
            throw new InvalidBearerTokenException(
                    "Only a person's own sign-in is accepted, and this token acts for somebody.");
        }
        return delegate.convert(source);
    }
}
