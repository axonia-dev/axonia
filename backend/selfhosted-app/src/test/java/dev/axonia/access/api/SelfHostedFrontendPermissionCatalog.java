package dev.axonia.access.api;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.util.List;
import java.util.stream.Stream;

import dev.axonia.RepositoryRoot;
import dev.axonia.access.api.FrontendPermissionCatalog.CatalogEntry;
import lombok.experimental.UtilityClass;

/**
 * The Self-hosted edition's roles matrix as its frontend declares it: the open catalog, plus the
 * {@code permission-group.ts} of every Self-hosted slice that contributes one.
 */
@UtilityClass
public class SelfHostedFrontendPermissionCatalog {

    private static final String SLICES = "frontend/src/selfhosted/features";

    private static final String CONTRIBUTION = "permission-group.ts";

    public List<CatalogEntry> entries() {

        return Stream.concat(FrontendPermissionCatalog.entries().stream(),
                contributionFiles().stream().flatMap(path -> FrontendPermissionCatalog.entriesIn(path).stream()))
                .toList();
    }

    public List<String> contributionFiles() {

        try (var slices = Files.list(RepositoryRoot.file(SLICES))) {
            return slices.filter(slice -> Files.isRegularFile(slice.resolve(CONTRIBUTION)))
                    .map(slice -> SLICES + "/" + slice.getFileName() + "/" + CONTRIBUTION).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(SLICES + " is unreadable", e);
        }
    }
}
