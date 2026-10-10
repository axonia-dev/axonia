package dev.axonia.installation.application;

import dev.axonia.access.api.Permission;
import lombok.experimental.UtilityClass;

/** Who may read and who may change the installation's mail server. */
@UtilityClass
public class MailServerPermissions {

    public final Permission MAIL_SERVER_READ = new Permission("mail_server:read");
    public final Permission MAIL_SERVER_WRITE = new Permission("mail_server:write");
}
