package dev.axonia;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Locates files by their path from the repository root, for the tests that read a file outside their own module: the
 * other half of a contract that lives in another app, or a backend file such as a realm export that no module owns.
 *
 * <p>
 * Surefire runs with the module's own directory as its working directory, which is not the same depth for every module,
 * and an IDE or a differently configured build step may start elsewhere again - so this walks up from wherever the JVM
 * actually started, rather than hard-coding how many {@code ..} segments separate it from the repository root, and
 * stops at the first ancestor that looks like the repository rather than an arbitrary depth.
 */
public final class RepositoryRoot {

    private static final String MARKER = "backend/mvnw";

    private RepositoryRoot() {
    }

    /** The repository root, resolved from the directory the JVM started in. */
    public static Path resolve() {

        return resolve(Path.of("").toAbsolutePath());
    }

    /**
     * {@code backend/mvnw} is the anchor: it sits at the same path from the root in this repository and in every tree
     * exported from it, whichever backend module the JVM starts in - unlike a file only this repository carries at its
     * root, which an export does not carry, and unlike {@code backend/pom.xml}, which the build itself rewrites.
     */
    public static Path resolve(Path start) {

        for (var dir = start; dir != null; dir = dir.getParent()) {
            if (Files.exists(dir.resolve(MARKER))) {
                return dir;
            }
        }
        throw new IllegalStateException("no ancestor of " + start + " contains " + MARKER
                + ", the marker this test uses for the repository root");
    }

    /** The file at the given repository-relative path, failing loudly rather than returning a path to nothing. */
    public static Path file(String relativePath) {

        var root = resolve();
        var file = root.resolve(relativePath);
        if (!Files.exists(file)) {
            throw new IllegalStateException(relativePath + " not found under repository root " + root);
        }
        return file;
    }
}
