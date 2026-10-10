package dev.axonia.shared.configuration;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoderInitializationException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationProvider;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;

import dev.axonia.shared.adapter.in.security.BearerRefusals;
import dev.axonia.shared.adapter.in.security.IssuerUnavailableException;
import dev.axonia.shared.adapter.in.security.OwnSignInJwtConverter;
import dev.axonia.shared.api.security.AnonymousPost;
import dev.axonia.shared.api.security.BearerCredentialProvider;
import tools.jackson.databind.json.JsonMapper;

/**
 * Stateless OAuth2 resource server: every request to the API surface carries a Bearer token, issued by the identity
 * provider or by a module that contributes a {@link BearerCredentialProvider}. CSRF stays disabled (no cookies, no
 * session). The only anonymous API calls are the ones a module contributes as an {@link AnonymousPost}.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** A signed JWT is Base64URL-encoded JSON and its header always starts with this. */
    private static final String JWT_PREFIX = "eyJ";

    private final String apiPrefix;

    public SecurityConfig(@Value("${spring.application.name}") String applicationName) {

        this.apiPrefix = "/" + applicationName;
    }

    @Bean
    @Order(3)
    SecurityFilterChain filterChain(HttpSecurity http, AuthenticationEntryPoint problemDetailEntryPoint,
            AuthenticationManagerResolver<HttpServletRequest> bearerAuthenticationManagerResolver,
            ObjectProvider<AnonymousPost> anonymousPosts) throws Exception {

        var anonymous = anonymousPosts.orderedStream().map(post -> apiPrefix + post.path()).toArray(String[]::new);
        return http.csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    if (anonymous.length > 0) {
                        auth.requestMatchers(HttpMethod.POST, anonymous).permitAll();
                    }
                    auth.requestMatchers(apiPrefix + "/**").authenticated().anyRequest().permitAll();
                })
                .oauth2ResourceServer(
                        oauth2 -> oauth2.authenticationManagerResolver(bearerAuthenticationManagerResolver)
                                .authenticationEntryPoint(problemDetailEntryPoint))
                .build();
    }

    /**
     * Routes by bearer prefix: each module-contributed {@link BearerCredentialProvider} claims its own prefix, and
     * every other bearer goes to the JWT manager. The JWT manager's converter is itself an extension point - the
     * Self-hosted edition supplies one to shape claims its own modules need, and this configuration stays oblivious to
     * what it does, apart from refusing a token that acts for somebody before any converter sees it. Staying inside
     * Spring Security's own bearer-token plumbing - rather than adding a servlet filter - is what keeps every failure
     * mode answering through {@link #problemDetailEntryPoint}.
     *
     * <p>
     * A credential provider is met at the framework type on purpose: the module that issues it may live in an internal
     * package this configuration must not reach into. Where none is registered (a slice that boots this chain alone)
     * its prefix simply falls through to the JWT manager and is rejected as an invalid token.
     */
    @Bean
    AuthenticationManagerResolver<HttpServletRequest> bearerAuthenticationManagerResolver(JwtDecoder jwtDecoder,
            ObjectProvider<BearerCredentialProvider> credentials,
            ObjectProvider<Converter<Jwt, ? extends AbstractAuthenticationToken>> jwtConverter,
            ObjectProvider<Clock> clock) {

        return resolverFor(jwtDecoder, credentials.orderedStream().toList(), jwtConverter.getIfAvailable(),
                new BearerRefusals(clock.getIfAvailable(Clock::systemUTC)));
    }

    static AuthenticationManagerResolver<HttpServletRequest> resolverFor(JwtDecoder jwtDecoder,
            List<BearerCredentialProvider> credentials,
            @Nullable Converter<Jwt, ? extends AbstractAuthenticationToken> jwtConverter, BearerRefusals refusals) {

        validatePrefixes(credentials);

        var jwtProvider = new JwtAuthenticationProvider(jwtDecoder);
        jwtProvider.setJwtAuthenticationConverter(new OwnSignInJwtConverter(jwtConverter));
        var jwtManager = answeringForAnUnreachableIssuer(new ProviderManager(jwtProvider), refusals);
        var byPrefix = credentials.stream().collect(Collectors.toMap(c -> "Bearer " + c.prefix(),
                c -> (AuthenticationManager) new ProviderManager(c.provider())));

        return request -> {
            var header = Objects.requireNonNullElse(request.getHeader(HttpHeaders.AUTHORIZATION), "");
            return byPrefix.entrySet().stream().filter(entry -> header.startsWith(entry.getKey()))
                    .map(Map.Entry::getValue).findFirst().orElse(jwtManager);
        };
    }

    /**
     * An issuer that cannot be reached says nothing about the token, whether the decoder could not be built (its
     * discovery document or keys could not be fetched) or a built decoder could not refresh the keys: the request is
     * answered as the issuer being unavailable, through {@link #problemDetailEntryPoint}, rather than escaping the
     * filter chain as a 500. A refused token is said in the log by {@link BearerRefusals} and answered as before; any
     * other failure is left as it is.
     */
    private static AuthenticationManager answeringForAnUnreachableIssuer(AuthenticationManager jwtManager,
            BearerRefusals refusals) {

        return authentication -> {
            try {
                return jwtManager.authenticate(authentication);
            } catch (JwtDecoderInitializationException e) {
                throw new IssuerUnavailableException(e);
            } catch (AuthenticationServiceException e) {
                if (IssuerUnavailableException.isKeyFetchFailure(e)) {
                    throw new IssuerUnavailableException(e);
                }
                throw e;
            } catch (AuthenticationException e) {
                refusals.refused(authentication, e);
                throw e;
            }
        };
    }

    /**
     * Fails fast rather than let a bad prefix silently misroute or shadow a JWT: a blank prefix would claim every
     * bearer, a prefix starting with {@code eyJ} or starting it could collide with a real JWT from the identity
     * provider (Base64URL-encoded JSON headers all begin that way), and two prefixes where one is a prefix of the
     * other, an exact duplicate included, make the winner depend on unspecified map iteration order.
     */
    private static void validatePrefixes(List<BearerCredentialProvider> credentials) {

        var prefixes = credentials.stream().map(BearerCredentialProvider::prefix).toList();

        rejectBlank(prefixes);
        rejectJwtLike(prefixes);
        rejectOverlapping(prefixes);
    }

    private static void rejectBlank(List<String> prefixes) {

        var blank = prefixes.stream().filter(String::isBlank).toList();
        if (!blank.isEmpty()) {
            throw new IllegalStateException("Bearer credential prefix must not be blank: " + quoted(blank));
        }
    }

    private static void rejectJwtLike(List<String> prefixes) {

        var jwtLike = prefixes.stream().filter(prefix -> prefix.startsWith(JWT_PREFIX) || JWT_PREFIX.startsWith(prefix))
                .toList();
        if (!jwtLike.isEmpty()) {
            throw new IllegalStateException("Bearer credential prefix must neither start with nor be the start of '"
                    + JWT_PREFIX + "', a JWT from the identity provider would match it: " + quoted(jwtLike));
        }
    }

    private static String quoted(List<String> prefixes) {

        return prefixes.stream().map(prefix -> "'" + prefix + "'").collect(Collectors.joining(", "));
    }

    private static void rejectOverlapping(List<String> prefixes) {

        for (var i = 0; i < prefixes.size(); i++) {
            for (var j = i + 1; j < prefixes.size(); j++) {
                rejectIfOverlapping(prefixes.get(i), prefixes.get(j));
            }
        }
    }

    private static void rejectIfOverlapping(String first, String second) {

        if (first.startsWith(second) || second.startsWith(first)) {
            throw new IllegalStateException("Bearer credential prefixes must not be prefixes of each other: '" + first
                    + "' and '" + second + "'");
        }
    }

    /**
     * Missing/expired/invalid token → 401 as an RFC 7807 ProblemDetail, matching the app's error style. The detail
     * stays deliberately uninformative; the machine-readable {@code code} is what tells a contributed bearer's own
     * refusals apart, so the app can say why a session ended rather than only that it did. An issuer that cannot be
     * reached → 503 with the code {@code issuer_unavailable}: the token may be fine, so the client must not take it for
     * a signed-out session.
     */
    @Bean
    AuthenticationEntryPoint problemDetailEntryPoint(JsonMapper jsonMapper) {

        return (request, response, exception) -> {
            var problem = problemFor(exception);
            if (exception instanceof IssuerUnavailableException) {
                response.setHeader(HttpHeaders.RETRY_AFTER, IssuerUnavailableException.RETRY_AFTER_SECONDS);
            }
            response.setStatus(problem.getStatus());
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.getWriter().write(jsonMapper.writeValueAsString(problem));
        };
    }

    private static ProblemDetail problemFor(AuthenticationException exception) {

        if (exception instanceof IssuerUnavailableException) {
            var problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,
                    "The identity provider cannot be reached");
            problem.setProperty("code", IssuerUnavailableException.CODE);
            return problem;
        }
        var problem = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Authentication required");
        if (exception instanceof OAuth2AuthenticationException oauth2) {
            problem.setProperty("code", oauth2.getError().getErrorCode());
        }
        return problem;
    }
}
