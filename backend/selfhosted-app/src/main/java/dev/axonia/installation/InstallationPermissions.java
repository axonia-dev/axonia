package dev.axonia.installation;

import java.util.Set;

import org.springframework.stereotype.Component;

import dev.axonia.access.Permission;
import dev.axonia.access.PermissionContribution;

/** The permissions of a Self-hosted installation's own settings, contributed to the edition's catalogue. */
@Component
public class InstallationPermissions implements PermissionContribution {

    public static final Permission MAIL_SERVER_READ = new Permission("mail_server:read");
    public static final Permission MAIL_SERVER_WRITE = new Permission("mail_server:write");

    @Override
    public Set<Permission> permissions() {

        return Set.of(MAIL_SERVER_READ, MAIL_SERVER_WRITE);
    }
}
