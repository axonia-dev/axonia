package dev.axonia.access.api;

import java.util.Set;
import java.util.regex.Pattern;

import com.fasterxml.jackson.annotation.JsonValue;

import dev.axonia.access.api.spi.PermissionContribution;

/**
 * A permission, named by its stable code ({@code "role:write"}): two permissions with the same code are the same
 * permission, which is also how one persists and travels the wire.
 *
 * <p>
 * The constants here are the open core's permissions, and {@link #OPEN} is all of them. A closed module declares its
 * own beside the code that checks them and contributes them through {@link PermissionContribution}, so the permissions
 * a running edition knows are {@code KnownPermissions}, never this class alone.
 */
public record Permission(String code) {

    private static final Pattern RESOURCE_AND_ACTION = Pattern.compile("[a-z_]+:[a-z_]+");

    public static final Permission ORGANIZATION_WRITE = new Permission("organization:write");
    public static final Permission ORGANIZATION_DELETE = new Permission("organization:delete");
    public static final Permission OWNERSHIP_TRANSFER = new Permission("ownership:transfer");
    public static final Permission MEMBER_READ = new Permission("member:read");
    public static final Permission MEMBER_WRITE = new Permission("member:write");
    public static final Permission ROLE_READ = new Permission("role:read");
    public static final Permission ROLE_ASSIGN = new Permission("role:assign");
    public static final Permission ROLE_WRITE = new Permission("role:write");
    public static final Permission SCIM_READ = new Permission("scim:read");
    public static final Permission SCIM_WRITE = new Permission("scim:write");
    public static final Permission COMMUNITY_KIND_READ = new Permission("community_kind:read");
    public static final Permission COMMUNITY_KIND_WRITE = new Permission("community_kind:write");
    public static final Permission LIBRARY_TYPE_READ = new Permission("library_type:read");
    public static final Permission LIBRARY_TYPE_WRITE = new Permission("library_type:write");
    public static final Permission SECTION_TYPE_READ = new Permission("section_type:read");
    public static final Permission SECTION_TYPE_WRITE = new Permission("section_type:write");
    public static final Permission COMMUNITY_CREATE = new Permission("community:create");
    public static final Permission ORGANIZATION_EXPORT = new Permission("organization:export");
    public static final Permission COMMUNICATION_WRITE = new Permission("communication:write");
    public static final Permission REACTION_READ = new Permission("reaction:read");
    public static final Permission REACTION_WRITE = new Permission("reaction:write");

    public static final Set<Permission> OPEN = Set.of(ORGANIZATION_WRITE, ORGANIZATION_DELETE, OWNERSHIP_TRANSFER,
            MEMBER_READ, MEMBER_WRITE, ROLE_READ, ROLE_ASSIGN, ROLE_WRITE, SCIM_READ, SCIM_WRITE, COMMUNITY_KIND_READ,
            COMMUNITY_KIND_WRITE, LIBRARY_TYPE_READ, LIBRARY_TYPE_WRITE, SECTION_TYPE_READ, SECTION_TYPE_WRITE,
            COMMUNITY_CREATE, ORGANIZATION_EXPORT, COMMUNICATION_WRITE, REACTION_READ, REACTION_WRITE);

    public Permission {
        if (code == null || !RESOURCE_AND_ACTION.matcher(code).matches()) {
            throw new IllegalArgumentException(
                    "A permission code is a resource and an action, such as role:write: " + code);
        }
    }

    @JsonValue
    @Override
    public String code() {
        return code;
    }
}
