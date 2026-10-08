package dev.axonia.access;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.axonia.CurrentToken;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/${spring.application.name}")
@RequiredArgsConstructor
public class AccessController {

    private final RoleService roleService;
    private final RoleBindingService bindingService;
    private final AuthorizationGate gate;

    @GetMapping("/roles")
    List<Role> roles() {

        return roleService.list();
    }

    @PostMapping("/roles")
    @ResponseStatus(HttpStatus.CREATED)
    Role createRole(@Valid @RequestBody CreateRoleRequest r) {

        return roleService.create(r);
    }

    @PutMapping("/roles/{id}")
    Role updateRole(@PathVariable Long id, @Valid @RequestBody UpdateRoleRequest r) {

        return roleService.update(id, r);
    }

    @DeleteMapping("/roles/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void deleteRole(@PathVariable Long id) {

        roleService.delete(id);
    }

    @GetMapping("/role-bindings")
    List<RoleBinding> bindings() {

        return bindingService.list();
    }

    @PostMapping("/role-bindings")
    @ResponseStatus(HttpStatus.CREATED)
    RoleBinding grant(@Valid @RequestBody CreateRoleBindingRequest r) {

        return bindingService.create(r);
    }

    @DeleteMapping("/role-bindings/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void revoke(@PathVariable Long id) {

        bindingService.delete(id);
    }

    @PostMapping("/role-bindings/{id}/confirm-group")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void confirmGroup(@PathVariable Long id) {

        bindingService.confirmGroup(id);
    }

    /**
     * A person whose token names no Organization holds no permission and no role, because both are granted inside one;
     * they get that empty answer rather than a 401, which the browser would read as a lost session. Decided on the
     * token's own claim, never on the development fallback to a default Organization.
     */
    @GetMapping("/access/me")
    AccessMe me() {

        if (!CurrentToken.hasOrganization()) {
            return new AccessMe(List.of(), List.of());
        }
        return new AccessMe(gate.currentPermissions().stream().map(Permission::code).sorted().toList(),
                roleService.myRoles());
    }

    record AccessMe(List<String> permissions, List<MyRole> roles) {
        AccessMe {
            permissions = List.copyOf(permissions);
            roles = List.copyOf(roles);
        }
    }
}
