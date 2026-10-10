package dev.axonia.privacy.adapter.in.web;

import java.io.IOException;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.axonia.access.api.AuthorizationGate;
import dev.axonia.access.api.Permission;
import dev.axonia.privacy.api.spi.MemberDataRightsPolicy;
import dev.axonia.privacy.application.MemberDataRights;
import dev.axonia.shared.api.error.ForbiddenException;
import dev.axonia.shared.api.personaldata.ExportScope;
import dev.axonia.shared.api.security.CurrentToken;
import lombok.RequiredArgsConstructor;

/**
 * The enterprise-wide export: every row of personal data an Organization holds about its members, unfiltered by whose
 * it is, as one ZIP.
 *
 * <p>
 * Deliberately not "everything an Organization holds". Every
 * {@link dev.axonia.shared.api.personaldata.PersonalDataExporter} exists to answer a data-subject request, so what a
 * fragment carries is the tables with a personal column in them - memberships, artifacts, sessions, topics, bindings,
 * signals. The reference tables those rows point at - {@code communities}, {@code community_kind},
 * {@code artifact_type}, {@code event_section}/{@code event_slot}, the tag vocabulary - carry no personal column, are
 * required by nothing in the footprint gate, and are not in the package. A {@code memberships} row therefore reads
 * {@code community_id: 42} with nothing alongside it to resolve, and {@code index.html} renders exactly that. Saying so
 * here is the honest half of the fix; making those ids resolve is a separate, larger change to what an export fragment
 * is for.
 *
 * <p>
 * Deliberately its own controller rather than a third mapping on {@link PrivacyController}: this one needs
 * {@code organization:export}, an enterprise-only permission (see
 * {@link dev.axonia.access.api.spi.PermissionCatalogue}) that a delegated session could otherwise use to download an
 * entire Organization's data under a member's identity - strictly worse than the self-service erasure hole
 * {@link PrivacyOwnSignInGuard} was built to close. The guard refuses this controller's handler the same way it refuses
 * every {@code PrivacyController} handler: matched by package, not by class, so a second controller in this module is
 * covered without anyone remembering to add it to a list.
 *
 * <p>
 * Refuses an Organization whose members exercise their own data rights (see {@link MemberDataRightsPolicy}) even when
 * the caller somehow holds the permission, which such an Organization's catalogue should never offer: there, every
 * member already exports their own data, and the whole-Organization export exists for the tenants whose members have no
 * such escape hatch.
 */
@RestController
@RequestMapping("/${spring.application.name}")
@RequiredArgsConstructor
public class OrganizationExportController {

    private final AuthorizationGate gate;
    private final MemberDataRights memberDataRights;
    private final ExportDownload download;

    @GetMapping("/admin/data-export")
    void export(HttpServletResponse response) throws IOException {

        gate.requirePermission(Permission.ORGANIZATION_EXPORT);
        var organizationId = CurrentToken.organization();
        if (memberDataRights.exercisedByMembers(organizationId)) {
            throw new ForbiddenException(
                    "Organization export is not available where members export their own data themselves.");
        }
        download.writeTo(new ExportScope.WholeOrganization(organizationId), CurrentToken.subject(), response);
    }
}
