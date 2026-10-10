package dev.axonia.installation.adapter.out.access;

import org.junit.jupiter.api.Test;

import dev.axonia.access.api.Permission;
import dev.axonia.access.api.spi.ClasspathPermissions;
import dev.axonia.installation.application.MailServerPermissions;

import static org.assertj.core.api.Assertions.assertThat;

class InstallationPermissionsTest {

    @Test
    void contributesReadingAndChangingTheMailServer() {

        assertThat(new InstallationPermissions().permissions())
                .containsExactlyInAnyOrder(new Permission("mail_server:read"), new Permission("mail_server:write"));
    }

    @Test
    void theSelfHostedEditionKnowsTheOpenPermissionsAndTheTwoOfTheInstallation() {

        assertThat(ClasspathPermissions.known()).containsAll(Permission.OPEN)
                .contains(MailServerPermissions.MAIL_SERVER_READ, MailServerPermissions.MAIL_SERVER_WRITE)
                .hasSize(Permission.OPEN.size() + 2);
    }
}
