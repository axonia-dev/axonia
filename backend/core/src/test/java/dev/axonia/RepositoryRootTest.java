package dev.axonia;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RepositoryRootTest {

    @Test
    void resolvesTheRootFromWhereverSurefireStarted() {

        assertThat(RepositoryRoot.resolve()).isDirectory();
        assertThat(RepositoryRoot.resolve().resolve("backend/mvnw")).exists();
        assertThat(RepositoryRoot.resolve().resolve("frontend/package.json")).exists();
    }

    @Test
    void resolveFailsClearlyWhenNoAncestorLooksLikeTheRepository() throws IOException {

        var outsideTheRepository = Files.createTempDirectory("repository-root-test");
        try {
            assertThatThrownBy(() -> RepositoryRoot.resolve(outsideTheRepository))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("backend/mvnw");
        } finally {
            Files.delete(outsideTheRepository);
        }
    }

    @Test
    void fileResolvesAgainstTheRoot() {

        assertThat(RepositoryRoot.file("backend/pom.xml"))
                .isEqualTo(RepositoryRoot.resolve().resolve(Path.of("backend", "pom.xml")));
    }

    @Test
    void fileFailsClearlyWhenTheFileIsGone() {

        assertThatThrownBy(() -> RepositoryRoot.file("no-such-file.json")).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no-such-file.json");
    }
}
