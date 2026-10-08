package dev.axonia.selfhosted.internal;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import dev.axonia.selfhosted.SelfHostedProperties;
import lombok.RequiredArgsConstructor;

/**
 * Turns an access token of the company's identity provider into the token every open module reads: {@code sub} from the
 * configured subject claim, {@code org} always the installation's Organization, {@code groups}, {@code email} and
 * {@code name} from their configured claims, and the registered time, issuer and audience claims. Nothing else is kept,
 * so a claim the open modules give a meaning to, such as {@code tokenId}, never arrives from outside.
 *
 * <p>
 * An address the token itself calls unverified ({@code email_verified: false}, in any case) is never the email, from
 * {@code email} or from any other configured claim that carries the same address, such as a username echoing it: a
 * person's email is what a profile the directory pushed is adopted by, so an address the person merely typed must never
 * become it. Another configured claim carrying a different address is still read.
 */
@Component
@RequiredArgsConstructor
class SelfHostedJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final String EMAIL = "email";

    private static final String EMAIL_VERIFIED = "email_verified";

    private static final List<String> REGISTERED = List.of(JwtClaimNames.ISS, JwtClaimNames.AUD, JwtClaimNames.EXP,
            JwtClaimNames.IAT, JwtClaimNames.NBF, JwtClaimNames.JTI);

    private final SelfHostedProperties properties;

    @Override
    public AbstractAuthenticationToken convert(Jwt source) {

        var oidc = properties.oidc();
        var subject = present(source, oidc.subjectClaim()).orElseThrow(
                () -> new InvalidBearerTokenException("The token carries no " + oidc.subjectClaim() + " claim."));
        var claims = new HashMap<String, Object>();
        REGISTERED.stream().filter(source::hasClaim).forEach(name -> claims.put(name, source.getClaims().get(name)));
        claims.put(JwtClaimNames.SUB, subject);
        claims.put("org", properties.organization().id());
        claims.put("groups", groups(source.getClaims().get(oidc.groupsClaim())));
        var unverified = unverifiedAddress(source);
        oidc.emailClaims().stream().map(name -> present(source, name)).flatMap(Optional::stream).map(String::strip)
                .filter(email -> unverified.filter(email::equalsIgnoreCase).isEmpty()).findFirst()
                .ifPresent(email -> claims.put(EMAIL, email));
        present(source, oidc.nameClaim()).ifPresent(name -> claims.put("name", name));
        var normalised = new Jwt(source.getTokenValue(), source.getIssuedAt(), source.getExpiresAt(),
                source.getHeaders(), claims);
        return new JwtAuthenticationToken(normalised, List.of(), subject);
    }

    private static List<String> groups(Object claim) {

        if (claim instanceof String group) {
            return List.of(group);
        }
        if (claim instanceof Collection<?> values) {
            return values.stream().filter(String.class::isInstance).map(String.class::cast).toList();
        }
        return List.of();
    }

    private static Optional<String> unverifiedAddress(Jwt source) {

        var verified = String.valueOf(source.getClaims().get(EMAIL_VERIFIED)).strip();
        return "false".equalsIgnoreCase(verified) ? present(source, EMAIL).map(String::strip) : Optional.empty();
    }

    private static Optional<String> present(Jwt source, String name) {

        return Optional.ofNullable(source.getClaimAsString(name)).filter(value -> !value.isBlank());
    }
}
