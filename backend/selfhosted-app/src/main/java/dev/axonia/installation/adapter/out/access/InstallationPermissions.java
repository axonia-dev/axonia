package dev.axonia.installation.adapter.out.access;

import java.util.Set;

import org.springframework.stereotype.Component;

import dev.axonia.access.api.Permission;
import dev.axonia.access.api.spi.PermissionContribution;
import dev.axonia.installation.application.MailServerPermissions;

/** The permissions of a Self-hosted installation's own settings, contributed to the edition's catalogue. */
@Component
public class InstallationPermissions implements PermissionContribution {

    @Override
    public Set<Permission> permissions() {

        return Set.of(MailServerPermissions.MAIL_SERVER_READ, MailServerPermissions.MAIL_SERVER_WRITE);
    }
}
