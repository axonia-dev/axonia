package dev.axonia.installation;

import org.junit.jupiter.api.Test;

import dev.axonia.access.ClasspathPermissions;
import dev.axonia.access.Permission;

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
                .contains(InstallationPermissions.MAIL_SERVER_READ, InstallationPermissions.MAIL_SERVER_WRITE)
                .hasSize(Permission.OPEN.size() + 2);
    }
}
