package dev.axonia.access;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import dev.axonia.RepositoryRoot;
import lombok.experimental.UtilityClass;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reads a frontend catalog of permissions, a hand-written mirror of the permissions an edition knows, and holds it to
 * them. Public so that each edition's build checks its own: core the open file, cloud the open file plus the groups its
 * closed slices contribute.
 */
@UtilityClass
public class FrontendPermissionCatalog {

    public static final String CATALOG = "frontend/src/features/admin/domain/permission-catalog.ts";

    /**
     * Permissions the gate enforces that no roles matrix offers. {@code community:create} is granted only by the seeded
     * system roles today; surfacing it is a product decision, not a mirroring one. Listing it here is the escape hatch,
     * and it is deliberately narrow: an omission has to be written down and reviewed rather than merely happening. The
     * frontend's privilege ceiling still measures these, so {@link #CATALOG} declares them again as its own
     * {@code NOT_SURFACED}, held to this one by {@code FrontendPermissionCatalogTest}.
     */
    public static final Set<Permission> NOT_SURFACED = Set.of(Permission.COMMUNITY_CREATE);

    static final Pattern DECLARATION = Pattern.compile("(?:export )?const [A-Z_]+: ReadonlyArray<PermissionDef> = \\[");

    /** Entries are flat object literals, so a brace pair containing no brace is exactly one of them. */
    private static final Pattern ENTRY = Pattern.compile("\\{([^{}]*)}");

    private static final Pattern CODE = Pattern.compile("code:\\s*\"([^\"]+)\"");

    private static final Pattern NOT_SURFACED_DECLARATION = Pattern
            .compile("export const NOT_SURFACED: ReadonlyArray<string> = \\[([^\\]]*)]");

    private static final Pattern QUOTED = Pattern.compile("\"([^\"]+)\"");

    public record CatalogEntry(String code, boolean enterpriseOnly) {
    }

    public List<CatalogEntry> entries() {

        return entriesIn(CATALOG);
    }

    public List<CatalogEntry> entriesIn(String path) {

        try {
            return parse(path, Files.readString(RepositoryRoot.file(path)));
        } catch (IOException e) {
            throw new UncheckedIOException(path + " is unreadable", e);
        }
    }

    /** The codes the frontend's privilege ceiling knows although no roles matrix offers them. */
    public Set<String> notSurfaced() {

        try {
            return parseNotSurfaced(CATALOG, Files.readString(RepositoryRoot.file(CATALOG)));
        } catch (IOException e) {
            throw new UncheckedIOException(CATALOG + " is unreadable", e);
        }
    }

    Set<String> parseNotSurfaced(String path, String source) {

        var declarations = NOT_SURFACED_DECLARATION.matcher(source).results().toList();
        if (declarations.size() != 1) {
            throw new IllegalStateException(path + " no longer declares exactly one `export const NOT_SURFACED: "
                    + "ReadonlyArray<string> = [ ... ];`. This test parses that declaration and has to be taught the "
                    + "new shape");
        }
        return QUOTED.matcher(declarations.getFirst().group(1)).results().map(match -> match.group(1))
                .collect(Collectors.toSet());
    }

    /**
     * Fails unless {@code frontend} offers every permission in {@code known} except {@link #NOT_SURFACED}, once each,
     * and nothing else.
     */
    public void assertMirrors(List<CatalogEntry> frontend, Set<Permission> known, String source) {

        var offered = frontend.stream().map(CatalogEntry::code).toList();
        var knownCodes = known.stream().map(Permission::code).collect(Collectors.toSet());
        assertThat(offered).as("%s names a permission twice", source).doesNotHaveDuplicates();
        assertThat(known.stream().filter(p -> !NOT_SURFACED.contains(p)).map(Permission::code).sorted().toList())
                .allSatisfy(code -> assertThat(offered).as(
                        "no administrator can grant '%s': it is a real, enforced permission that %s never "
                                + "describes. Add an entry for it in the slice of the module that declares it (label, "
                                + "description, group, and enterpriseOnly when it does not apply to a Community Hub), "
                                + "or, if no roles matrix is to offer it, add it to NOT_SURFACED with the reason",
                        code, source).contains(code));
        assertThat(offered).allSatisfy(code -> assertThat(knownCodes).as(
                "%s offers '%s', which this edition does not know. An administrator can toggle it onto a role where it "
                        + "grants nothing: remove the entry, or move it to the slice of the module that declares it",
                source, code).contains(code));
        assertThat(NOT_SURFACED).allSatisfy(permission -> assertThat(offered)
                .as("'%s' is listed as deliberately not offered, but %s now offers it: drop it from NOT_SURFACED",
                        permission.code(), source)
                .doesNotContain(permission.code()));
    }

    List<CatalogEntry> parse(String path, String source) {

        return ENTRY.matcher(declaration(path, source)).results().map(match -> entry(path, match.group(1))).toList();
    }

    private String declaration(String path, String source) {

        var declarations = DECLARATION.matcher(source).results().toList();
        var end = declarations.size() == 1 ? source.indexOf("\n];", declarations.getFirst().end()) : -1;
        if (end < 0) {
            throw new IllegalStateException(path + " no longer declares exactly one `const NAME: "
                    + "ReadonlyArray<PermissionDef> = [ ... ];`. This test parses that declaration and has to be taught "
                    + "the new shape");
        }
        return source.substring(declarations.getFirst().end(), end);
    }

    private CatalogEntry entry(String path, String body) {

        var code = CODE.matcher(body);
        if (!code.find()) {
            throw new IllegalStateException("an entry in " + path + " has no `code:` field: " + body);
        }
        return new CatalogEntry(code.group(1), body.contains("enterpriseOnly: true"));
    }
}
