package dev.axonia.agent.adapter.in.web;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.axonia.agent.application.AgentAccess;
import dev.axonia.agent.domain.AgentScope;
import dev.axonia.identity.api.pat.MintPersonalAccessToken;
import dev.axonia.identity.api.pat.PersonalAccessTokens;
import dev.axonia.identity.api.pat.PersonalTokenSummary;
import dev.axonia.identity.api.pat.UpdatePersonalAccessToken;
import dev.axonia.shared.api.error.ActionableForbiddenException;
import dev.axonia.shared.api.error.ValidationException;
import dev.axonia.shared.api.security.CurrentToken;
import lombok.RequiredArgsConstructor;

/**
 * Self-service management of the caller's own personal access tokens (the "bring your own agent" API keys screen). Not
 * RBAC-gated: a {@code /me/*} surface scoped to the caller's own credentials, not an admin capability. Every operation
 * filters by {@link CurrentToken#subject()} + {@link CurrentToken#organization()} so a member can only see, mint,
 * update, or revoke their own tokens.
 */
@RestController
@RequestMapping("/${spring.application.name}/me/tokens")
@RequiredArgsConstructor
public class PersonalTokenController {

    private final PersonalAccessTokens tokens;
    private final AgentAccess access;

    /**
     * An expiry is optional: a token minted without one lasts until it is revoked. Revocation is the control that
     * matters here, and it takes effect immediately, whereas a mandatory expiry mostly buys a date the holder picked
     * arbitrarily and then has to keep renewing.
     *
     * <p>
     * A past expiry is still refused, because it mints a credential that is already dead. {@code @Future} permits a
     * null value by specification, so dropping {@code @NotNull} is the whole of what makes the field optional.
     */
    public record CreateTokenRequest(@NotBlank @Size(max = 100) String name, Set<String> scopes,
            @Future Instant expiresAt) {
    }

    /** The {@code axp_} plaintext, shown exactly once at mint time; never retrievable again. */
    public record CreateTokenResponse(String token) {
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    CreateTokenResponse create(@Valid @RequestBody CreateTokenRequest request) {

        requireAgentSurface();
        var plaintext = tokens.mint(new MintPersonalAccessToken(CurrentToken.subject(), CurrentToken.organization(),
                request.name(), knownScopes(request.scopes()), request.expiresAt()));
        return new CreateTokenResponse(plaintext);
    }

    @GetMapping
    List<PersonalTokenSummary> list() {

        return tokens.list(CurrentToken.subject(), CurrentToken.organization());
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revoke(@PathVariable Long id) {

        tokens.revoke(CurrentToken.subject(), CurrentToken.organization(), id);
    }

    /** The scope set replaces the token's current one wholesale. The secret is untouched. */
    public record UpdateTokenRequest(@NotBlank String name, Set<String> scopes) {
    }

    @PatchMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void update(@PathVariable Long id, @Valid @RequestBody UpdateTokenRequest request) {

        requireAgentSurface();
        tokens.update(new UpdatePersonalAccessToken(CurrentToken.subject(), CurrentToken.organization(), id,
                request.name(), knownScopes(request.scopes())));
    }

    /**
     * Refuses a caller whose organization may not connect agents at all, and a caller acting in no organization. A 403
     * rather than a 400: the request is well-formed and the caller simply may not make it. The message names the change
     * that makes it succeed, which is what {@link ActionableForbiddenException} is for.
     *
     * <p>
     * Applied to minting and updating only. Listing and revoking stay open so a credential that already exists can
     * still be seen and withdrawn.
     */
    private void requireAgentSurface() {

        if (!access.isAvailableToCaller()) {
            throw new ActionableForbiddenException(access.refusal());
        }
    }

    /** An absent scope set means no scopes, exactly as it does at mint time. An unrecognised one is a 400. */
    private static Set<String> knownScopes(Set<String> requested) {

        var scopes = requested == null ? Set.<String>of() : requested;
        var unknown = scopes.stream().filter(scope -> !AgentScope.isKnown(scope)).toList();
        if (!unknown.isEmpty()) {
            throw new ValidationException("unknown_scope", "Unknown scope(s): " + String.join(", ", unknown));
        }
        return scopes;
    }
}
