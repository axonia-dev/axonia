package dev.axonia.access.application;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import dev.axonia.access.api.Permission;
import dev.axonia.access.api.spi.PermissionContribution;

/**
 * Every permission the running edition knows: the open ones, and those its modules contribute. A code nothing here
 * declares grants nothing, is never shown on a role, and is refused when a role is written.
 */
@Component
public final class KnownPermissions {

    private final Map<String, Permission> byCode;

    public KnownPermissions(List<PermissionContribution> contributions) {

        var declared = new HashMap<String, Permission>();
        Stream.concat(Stream.of(Permission.OPEN), contributions.stream().map(PermissionContribution::permissions))
                .flatMap(Set::stream).forEach(permission -> declareOnce(declared, permission));
        this.byCode = Map.copyOf(declared);
    }

    public Set<Permission> all() {

        return Set.copyOf(byCode.values());
    }

    public Optional<Permission> byCode(String code) {

        return Optional.ofNullable(byCode.get(code));
    }

    private static void declareOnce(Map<String, Permission> declared, Permission permission) {

        if (declared.putIfAbsent(permission.code(), permission) != null) {
            throw new IllegalStateException(permission.code()
                    + " is declared twice. Each permission is declared once, by the module that gates on it.");
        }
    }
}
