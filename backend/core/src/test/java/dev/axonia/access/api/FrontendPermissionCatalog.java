package dev.axonia.access.api;

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

    static final Pattern DECLARATION = Pattern.compile("(?:export )?const [A-Z_]+: ReadonlyArray<PermissionDef> = \\[");

    /** Entries are flat object literals, so a brace pair containing no brace is exactly one of them. */
    private static final Pattern ENTRY = Pattern.compile("\\{([^{}]*)}");

    private static final Pattern CODE = Pattern.compile("code:\\s*\"([^\"]+)\"");

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

    /**
     * Fails unless {@code frontend} offers every permission in {@code known}, once each, and nothing else.
     */
    public void assertMirrors(List<CatalogEntry> frontend, Set<Permission> known, String source) {

        var offered = frontend.stream().map(CatalogEntry::code).toList();
        var knownCodes = known.stream().map(Permission::code).collect(Collectors.toSet());
        assertThat(offered).as("%s names a permission twice", source).doesNotHaveDuplicates();
        assertThat(knownCodes.stream().sorted().toList()).allSatisfy(code -> assertThat(offered).as(
                "no administrator can grant '%s': it is a real, enforced permission that %s never "
                        + "describes. Add an entry for it in the slice of the module that declares it (label, "
                        + "description, group, and enterpriseOnly when it does not apply to a Community Hub)",
                code, source).contains(code));
        assertThat(offered).allSatisfy(code -> assertThat(knownCodes).as(
                "%s offers '%s', which this edition does not know. An administrator can toggle it onto a role where it "
                        + "grants nothing: remove the entry, or move it to the slice of the module that declares it",
                source, code).contains(code));
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
