package dev.axonia.access.api;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import dev.axonia.access.api.FrontendPermissionCatalog.CatalogEntry;
import dev.axonia.access.api.spi.ClasspathPermissions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The open roles matrix cannot silently stop offering a permission the open edition enforces, nor offer one it does not
 * know.
 *
 * <p>
 * The frontend's {@code permission-catalog.ts} is a hand-written mirror of the open permissions that adds the labels,
 * descriptions and grouping an administrator reads. Nothing linked the two once: when a permission was added, seeded
 * onto every enterprise Owner and enforced by an endpoint, the roles matrix never learned it existed, and both apps'
 * gates stayed green because each asserted its own hand-written list.
 *
 * <p>
 * The guard lives in the backend gate deliberately. The drift is introduced by a backend change, and CI filters per
 * app, so a backend-only change never runs the frontend suite. A closed module's permissions are mirrored by its own
 * frontend slice and held there by {@code CloudFrontendPermissionCatalogTest}, against the Cloud's classpath.
 *
 * <p>
 * <strong>The limit, stated rather than implied:</strong> this proves the two catalogs name the same permissions. Which
 * of them are enterprise-only is decided where a narrower catalogue is contributed, and asserted there. It proves
 * nothing about whether a label or description is accurate, and nothing about whether the surface a permission exists
 * to govern actually gates on it.
 */
class FrontendPermissionCatalogTest {

    private static final Set<Permission> KNOWN = Set.of(Permission.MEMBER_READ, Permission.ROLE_WRITE,
            Permission.COMMUNITY_CREATE);

    @Test
    void theOpenRolesMatrixMirrorsEveryPermissionTheOpenEditionKnows() {

        FrontendPermissionCatalog.assertMirrors(FrontendPermissionCatalog.entries(), ClasspathPermissions.known(),
                FrontendPermissionCatalog.CATALOG);
    }

    @Test
    void theFrontendCeilingKnowsEveryPermissionNoMatrixOffers() {

        // when
        var actual = FrontendPermissionCatalog.notSurfaced();

        // then - a code missing here is one the frontend ceiling would ignore while the server still refuses it
        assertThat(actual).containsExactlyInAnyOrderElementsOf(
                FrontendPermissionCatalog.NOT_SURFACED.stream().map(Permission::code).toList());
    }

    @Test
    void parseNotSurfacedReadsEveryQuotedCode() {

        // when
        var actual = FrontendPermissionCatalog.parseNotSurfaced("x.ts",
                "export const NOT_SURFACED: ReadonlyArray<string> = [\"community:create\", \"x:y\"];\n");

        // then
        assertThat(actual).containsExactlyInAnyOrder("community:create", "x:y");
    }

    @Test
    void parseNotSurfacedFailsClearlyWhenTheDeclarationIsGone() {

        // when / then
        assertThatThrownBy(() -> FrontendPermissionCatalog.parseNotSurfaced("x.ts", "const OTHER = [];"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("NOT_SURFACED");
    }

    @Test
    void failsOnAnEnforcedPermissionTheMatrixNeverOffers() {

        assertThatThrownBy(() -> FrontendPermissionCatalog.assertMirrors(entries("member:read"), KNOWN, "test.ts"))
                .isInstanceOf(AssertionError.class).hasMessageContaining("no administrator can grant 'role:write'");
    }

    @Test
    void failsOnAnOfferedPermissionTheEditionDoesNotKnow() {

        assertThatThrownBy(() -> FrontendPermissionCatalog
                .assertMirrors(entries("member:read", "role:write", "sso:read"), KNOWN, "test.ts"))
                .isInstanceOf(AssertionError.class).hasMessageContaining("offers 'sso:read'");
    }

    @Test
    void failsWhenAPermissionKeptOutOfTheMatrixIsOffered() {

        assertThatThrownBy(() -> FrontendPermissionCatalog
                .assertMirrors(entries("member:read", "role:write", "community:create"), KNOWN, "test.ts"))
                .isInstanceOf(AssertionError.class).hasMessageContaining("NOT_SURFACED");
    }

    @Test
    void failsOnAPermissionOfferedTwice() {

        assertThatThrownBy(() -> FrontendPermissionCatalog
                .assertMirrors(entries("member:read", "role:write", "role:write"), KNOWN, "test.ts"))
                .isInstanceOf(AssertionError.class).hasMessageContaining("names a permission twice");
    }

    @Test
    void parseFailsClearlyWhenTheDeclarationIsGone() {

        assertThatThrownBy(
                () -> FrontendPermissionCatalog.parse("x.ts", "const SOMETHING_ELSE = [{ code: \"member:read\" }];"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("exactly one");
    }

    @Test
    void parseFailsClearlyOnTwoDeclarations() {

        var twice = "const A: ReadonlyArray<PermissionDef> = [\n];\nconst B: ReadonlyArray<PermissionDef> = [\n];\n";

        assertThatThrownBy(() -> FrontendPermissionCatalog.parse("x.ts", twice))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("exactly one");
    }

    @Test
    void parseFailsClearlyWhenAnEntryCarriesNoCode() {

        assertThatThrownBy(() -> FrontendPermissionCatalog.parse("x.ts",
                "export const PERMISSION_CATALOG: ReadonlyArray<PermissionDef> = [\n  { label: \"View members\" },\n];\n"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("no `code:`");
    }

    @Test
    void parseReadsCodesAndTheEnterpriseOnlyFlagOfAnUnexportedDeclaration() {

        var entries = FrontendPermissionCatalog.parse("x.ts",
                "const SSO_PERMISSIONS: ReadonlyArray<PermissionDef> = [\n  { code: \"member:read\" },\n"
                        + "  { code: \"scim:read\", enterpriseOnly: true },\n];\nconst AFTER = [{ code: \"ignored\" }];\n");

        assertThat(entries).containsExactly(new CatalogEntry("member:read", false),
                new CatalogEntry("scim:read", true));
    }

    private static List<CatalogEntry> entries(String... codes) {

        return Arrays.stream(codes).map(code -> new CatalogEntry(code, false)).toList();
    }
}
