package dev.axonia.identity.scim;

import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin endpoints for SCIM bearer-token management. Protected by the JWT security chain ({@code /axonia/**});
 * permission enforcement is delegated to {@link ScimTokenAdminService}.
 */
@RestController
@RequestMapping("/${spring.application.name}/scim-tokens")
class ScimTokenAdminController {

    private final ScimTokenAdminService service;

    ScimTokenAdminController(ScimTokenAdminService service) {
        this.service = service;
    }

    /** Mints a new labeled SCIM token for the caller's own Organization; returns the plaintext ONCE. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    GenerateResponse generate(@Valid @RequestBody GenerateRequest request) {
        return new GenerateResponse(service.generate(request.label(), request.expiresAt()));
    }

    /** Revokes a single SCIM token by id within the caller's own Organization. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revoke(@PathVariable Long id) {
        service.revoke(id);
    }

    /** Lists SCIM token metadata for the caller's own Organization; never exposes the secret. */
    @GetMapping
    List<ScimTokenView> list() {
        return service.list();
    }

    // ── Controller-local DTOs ──────────────────────────────────────────────────

    record GenerateRequest(@NotBlank @Size(max = 100) String label, @NotNull @Future Instant expiresAt) {
    }

    record GenerateResponse(String token) {
    }
}
