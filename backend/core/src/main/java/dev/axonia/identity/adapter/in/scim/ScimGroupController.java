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

import dev.axonia.identity.application.scim.ScimGroup;
import dev.axonia.identity.application.scim.ScimGroupService;
import dev.axonia.identity.application.scim.ScimListResponse;
import dev.axonia.identity.application.scim.ScimPatchRequest;
import dev.axonia.identity.application.scim.ScimPrincipal;

@RestController
@RequestMapping(path = "/scim/v2/Groups", produces = "application/scim+json")
class ScimGroupController {

    private static final String SCIM_JSON = "application/scim+json";
    private static final String APPLICATION_JSON = MediaType.APPLICATION_JSON_VALUE;

    private final ScimGroupService scimGroupService;

    ScimGroupController(ScimGroupService scimGroupService) {
        this.scimGroupService = scimGroupService;
    }

    @PostMapping(consumes = {SCIM_JSON, APPLICATION_JSON})
    @ResponseStatus(HttpStatus.CREATED)
    ScimGroup create(@RequestBody ScimGroup group) {
        return scimGroupService.create(principal(), group);
    }

    @GetMapping("/{id}")
    ScimGroup get(@PathVariable String id) {
        return scimGroupService.get(principal(), id);
    }

    @GetMapping
    ScimListResponse<ScimGroup> list(@RequestParam(required = false) String filter) {
        return scimGroupService.list(principal(), filter);
    }

    @PutMapping(path = "/{id}", consumes = {SCIM_JSON, APPLICATION_JSON})
    ScimGroup replace(@PathVariable String id, @RequestBody ScimGroup group) {
        return scimGroupService.replace(principal(), id, group);
    }

    @PatchMapping(path = "/{id}", consumes = {SCIM_JSON, APPLICATION_JSON})
    ScimGroup patch(@PathVariable String id, @RequestBody ScimPatchRequest patch) {
        return scimGroupService.patch(principal(), id, patch);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@PathVariable String id) {
        scimGroupService.delete(principal(), id);
    }

    private static ScimPrincipal principal() {
        var auth = Objects.requireNonNull(SecurityContextHolder.getContext().getAuthentication());
        return (ScimPrincipal) auth.getPrincipal();
    }
}
