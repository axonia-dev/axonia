package dev.axonia.identity.adapter.in.scim;

import java.util.Objects;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.axonia.identity.application.scim.ScimListResponse;
import dev.axonia.identity.application.scim.ScimPatchRequest;
import dev.axonia.identity.application.scim.ScimPrincipal;
import dev.axonia.identity.application.scim.ScimUser;
import dev.axonia.identity.application.scim.ScimUserService;

@RestController
@RequestMapping(path = "/scim/v2/Users", produces = "application/scim+json")
class ScimUserController {

    private static final String SCIM_JSON = "application/scim+json";
    private static final String APPLICATION_JSON = MediaType.APPLICATION_JSON_VALUE;

    private final ScimUserService scimUserService;

    ScimUserController(ScimUserService scimUserService) {
        this.scimUserService = scimUserService;
    }

    @PostMapping(consumes = {SCIM_JSON, APPLICATION_JSON})
    @ResponseStatus(HttpStatus.CREATED)
    ScimUser create(@RequestBody ScimUser user) {
        return scimUserService.create(principal(), user);
    }

    @GetMapping("/{id}")
    ScimUser get(@PathVariable String id) {
        return scimUserService.get(principal(), id);
    }

    @GetMapping
    ScimListResponse<ScimUser> list(@RequestParam(required = false) String filter) {
        return scimUserService.list(principal(), filter);
    }

    @PutMapping(path = "/{id}", consumes = {SCIM_JSON, APPLICATION_JSON})
    ScimUser replace(@PathVariable String id, @RequestBody ScimUser user) {
        return scimUserService.replace(principal(), id, user);
    }

    @PatchMapping(path = "/{id}", consumes = {SCIM_JSON, APPLICATION_JSON})
    ScimUser patch(@PathVariable String id, @RequestBody ScimPatchRequest patch) {
        return scimUserService.patch(principal(), id, patch);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable String id) {
        scimUserService.delete(principal(), id);
    }

    private static ScimPrincipal principal() {
        var auth = Objects.requireNonNull(SecurityContextHolder.getContext().getAuthentication());
        return (ScimPrincipal) auth.getPrincipal();
    }
}
