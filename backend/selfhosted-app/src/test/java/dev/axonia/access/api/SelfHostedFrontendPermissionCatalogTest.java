package dev.axonia.access.api;

import org.junit.jupiter.api.Test;

import dev.axonia.access.api.FrontendPermissionCatalog.CatalogEntry;
import dev.axonia.access.api.spi.ClasspathPermissions;
import dev.axonia.installation.application.MailServerPermissions;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Self-hosted roles matrix is the open catalog plus the permission group each Self-hosted slice contributes, and it
 * must mirror every permission the Self-hosted backend knows: the open ones and those its {@code installation} module
 * contributes. The open half alone is held by {@code FrontendPermissionCatalogTest}, the Cloud's by
 * {@code CloudFrontendPermissionCatalogTest}.
 */
class SelfHostedFrontendPermissionCatalogTest {

    @Test
    void theSelfHostedRolesMatrixMirrorsEveryPermissionTheSelfHostedEditionKnows() {

        FrontendPermissionCatalog.assertMirrors(SelfHostedFrontendPermissionCatalog.entries(),
                ClasspathPermissions.known(), "the Self-hosted roles matrix (" + FrontendPermissionCatalog.CATALOG
                        + " and " + SelfHostedFrontendPermissionCatalog.contributionFiles() + ")");
    }

    @Test
    void readsTheGroupOfEverySliceThatContributesOne() {

        assertThat(SelfHostedFrontendPermissionCatalog.contributionFiles())
                .containsExactly("frontend/src/selfhosted/features/mail-server/permission-group.ts");
    }

    @Test
    void theMailServerGroupOffersBothPermissionsToEveryOrganizationKind() {

        assertThat(
                FrontendPermissionCatalog.entriesIn("frontend/src/selfhosted/features/mail-server/permission-group.ts"))
                .containsExactly(new CatalogEntry(MailServerPermissions.MAIL_SERVER_READ.code(), false),
                        new CatalogEntry(MailServerPermissions.MAIL_SERVER_WRITE.code(), false));
    }
}
