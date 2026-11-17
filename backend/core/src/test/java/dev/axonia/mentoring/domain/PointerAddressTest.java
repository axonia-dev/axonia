package dev.axonia.mentoring.domain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import dev.axonia.mentoring.domain.PointerAddress.ParsedPointer;
import dev.axonia.shared.api.error.ValidationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PointerAddressTest {

    private static final String BASE = "https://acme.example.test";

    @ParameterizedTest
    @ValueSource(strings = {"https://acme.example.test/communities/platform/sessions/42",
            "https://acme.example.test/communities/platform/sessions/42/",
            "https://ACME.example.test:443/communities/platform/sessions/42?tab=agenda#feedback",
            "  https://acme.example.test/communities/platform/sessions/42  "})
    void aSessionPageIsASession(String url) {

        // when
        var actual = PointerAddress.parse(url, BASE);

        // then
        assertThat(actual).isEqualTo(new ParsedPointer(PointerKind.SESSION, 42L, "/communities/platform/sessions/42"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://acme.example.test/communities/data/library?focus=7",
            "https://acme.example.test/communities/data/library/?sort=mostAppreciated&focus=7",
            "https://acme.example.test/communities/data/library?focus=7&q=kafka"})
    void aFocusedLibraryArtifactIsAnArtifact(String url) {

        // when
        var actual = PointerAddress.parse(url, BASE);

        // then
        assertThat(actual).isEqualTo(new ParsedPointer(PointerKind.ARTIFACT, 7L, "/communities/data/library?focus=7"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://example.com/communities/platform/sessions/42",
            "http://acme.example.test/communities/platform/sessions/42",
            "https://acme.example.test:8443/communities/platform/sessions/42",
            "https://acme.example.test/communities/platform/sessions/42/edit",
            "https://acme.example.test/communities/platform/sessions/kafka",
            "https://acme.example.test/communities/data/library?q=kafka",
            "https://acme.example.test/communities/data/library?prefocus=7",
            "https://acme.example.test/communities/platform/schedule", "https://acme.example.test"})
    void anythingElseIsALinkKeptVerbatim(String url) {

        // when
        var actual = PointerAddress.parse(url, BASE);

        // then
        assertThat(actual).isEqualTo(new ParsedPointer(PointerKind.LINK, null, url));
    }

    @Test
    void aLocalOriginOnPlainHttpIsMatchedByItsPort() {

        // when
        var session = PointerAddress.parse("http://localhost:5173/communities/p/sessions/3", "http://localhost:5173");
        var defaultPort = PointerAddress.parse("http://intranet/communities/p/sessions/3", "http://intranet:80");

        // then
        assertThat(session.kind()).isEqualTo(PointerKind.SESSION);
        assertThat(defaultPort.kind()).isEqualTo(PointerKind.SESSION);
    }

    @ParameterizedTest
    @ValueSource(strings = {"javascript:alert(1)", "ftp://example.com/file", "mailto:someone@example.com",
            "/communities/platform/sessions/42", "https://", "https:///communities/platform/sessions/42",
            "not an address", "https://exa mple.com", " ", "https://example.test@evil.com/communities/x/sessions/1",
            "https://user:secret@example.com/page", "https://my_host.example/page"})
    void onlyWebAddressesAreTaken(String url) {

        assertThatThrownBy(() -> PointerAddress.parse(url, BASE)).isInstanceOfSatisfying(ValidationException.class,
                e -> assertThat(e.getCode()).isEqualTo("field_invalid"));
    }

    @Test
    void aMissingAddressIsRefused() {

        assertThatThrownBy(() -> PointerAddress.parse(null, BASE)).isInstanceOfSatisfying(ValidationException.class,
                e -> assertThat(e.getCode()).isEqualTo("field_invalid"));
    }

    @Test
    void anAddressIsAtMost2000Characters() {

        // given
        var longest = "https://example.com/" + "a".repeat(PointerAddress.MAX_URL_LENGTH - 20);

        // when / then
        assertThat(PointerAddress.parse(longest, BASE).url()).hasSize(2000);
        assertThatThrownBy(() -> PointerAddress.parse(longest + "a", BASE)).isInstanceOfSatisfying(
                ValidationException.class, e -> assertThat(e.getCode()).isEqualTo("MENTORING_TOO_LONG"));
    }
}
