package dev.axonia.media;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import dev.axonia.ControllerTestSupport;
import dev.axonia.CoreWebMvcTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;

/**
 * The media route is public by URL: no session, no organization, and the unguessable file name is the capability. What
 * stands between it and the object store is the key's shape, so everything that is not a key the backend itself writes
 * is refused before the store is asked.
 */
@CoreWebMvcTest(controllers = MediaController.class)
class MediaControllerTest extends ControllerTestSupport {

    private static final String FILE = "3f2b8c1e-5d4a-4b6c-9e8f-0a1b2c3d4e5f";
    private static final String KEY = "avatars/acme/" + FILE + ".png";
    private static final String URL = "/public/media/" + KEY;
    private static final String ETAG = "\"" + KEY + "\"";
    private static final byte[] BYTES = {1, 2, 3, 4};

    @MockitoBean
    private MediaStorage mediaStorage;

    @Test
    void servesAnImageToACallerWithNoSessionUnderHeadersThatMakeItSafeToCache() throws IOException {

        // given
        var stream = new Tracked(BYTES);
        when(mediaStorage.open(KEY)).thenReturn(Optional.of(new MediaContent(stream, BYTES.length)));

        // when
        var actual = mvc.get().uri(URL).with(anonymous()).exchange().getResponse();

        // then
        assertThat(actual.getStatus()).isEqualTo(200);
        assertThat(actual.getContentAsByteArray()).isEqualTo(BYTES);
        assertThat(actual.getContentType()).isEqualTo("image/png");
        assertThat(actual.getHeader(HttpHeaders.CONTENT_LENGTH)).isEqualTo("4");
        assertThat(actual.getHeader(HttpHeaders.CACHE_CONTROL))
                .isEqualTo("public, max-age=31536000, s-maxage=86400, immutable");
        assertThat(actual.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(actual.getHeader("Content-Security-Policy")).isEqualTo("default-src 'none'; sandbox");
        assertThat(actual.getHeader(HttpHeaders.ETAG)).isEqualTo(ETAG);
        assertThat(actual.getHeaders(HttpHeaders.CACHE_CONTROL)).hasSize(1);
        assertThat(actual.getHeader(HttpHeaders.PRAGMA)).isNull();
        assertThat(stream.closed).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"reactions/acme/" + FILE + ".webp,image/webp", "communities/7/hero/" + FILE + ".jpg,image/jpeg",
            "communities/7/badge/" + FILE + ".png,image/png"})
    void theContentTypeComesFromTheKeyNeverFromTheStore(String key, String contentType) {

        // given
        when(mediaStorage.open(key)).thenReturn(Optional.of(new MediaContent(new Tracked(BYTES), BYTES.length)));

        // when
        var actual = mvc.get().uri("/public/media/" + key).with(anonymous()).exchange().getResponse();

        // then
        assertThat(actual.getStatus()).isEqualTo(200);
        assertThat(actual.getContentType()).isEqualTo(contentType);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/public/media", "/public/media/", "/public/media/avatars/acme/photo.png",
            "/public/media/avatars/acme/" + FILE + ".svg", "/public/media/avatars/acme/" + FILE + ".html",
            "/public/media/exports/acme/" + FILE + ".png", "/public/media/avatars/" + FILE + ".png",
            "/public/media/avatars/acme/extra/" + FILE + ".png", "/public/media/communities/x/badge/" + FILE + ".png",
            "/public/media/communities/7/logo/" + FILE + ".png", "/public/media/avatars/acme/" + FILE + ".png/",
            "/public/media/avatars/.hidden/" + FILE + ".png", "/public/media/avatars/ac%20me/" + FILE + ".png"})
    void refusesAKeyTheBackendNeverWrites(String uri) {

        // when
        var actual = mvc.get().uri(URI.create(uri)).with(anonymous()).exchange().getResponse();

        // then
        assertThat(actual.getStatus()).isEqualTo(404);
        assertThat(actual.getHeader(HttpHeaders.ETAG)).isNull();
        assertThat(actual.getHeaders(HttpHeaders.CACHE_CONTROL)).noneMatch(value -> value.contains("immutable"));
        verifyNoInteractions(mediaStorage);
    }

    /** Spring Security's firewall refuses these before any handler runs. */
    @ParameterizedTest
    @ValueSource(strings = {"/public/media/avatars/../../etc/passwd", "/public/media/avatars/acme/..%2F..%2Fsecret.png",
            "/public/media/avatars/acme/%2e%2e/" + FILE + ".png", "/public/media/avatars/acme;x=1/" + FILE + ".png",
            "/public/media/avatars//" + FILE + ".png"})
    void aPathThatTriesToLeaveItsPrefixIsRefusedAsABadRequest(String uri) {

        // when
        var actual = mvc.get().uri(URI.create(uri)).with(anonymous()).exchange().getResponse();

        // then
        assertThat(actual.getStatus()).isEqualTo(400);
        verifyNoInteractions(mediaStorage);
    }

    @Test
    void anObjectTheStoreDoesNotHoldIs404AndCarriesNoLongLivedCaching() {

        // given
        when(mediaStorage.open(KEY)).thenReturn(Optional.empty());

        // when
        var actual = mvc.get().uri(URL).with(anonymous()).exchange().getResponse();

        // then
        assertThat(actual.getStatus()).isEqualTo(404);
        assertThat(actual.getHeader(HttpHeaders.ETAG)).isNull();
        assertThat(actual.getHeaders(HttpHeaders.CACHE_CONTROL)).noneMatch(value -> value.contains("immutable"));
    }

    @Test
    void headAnswersTheSameHeadersAndNeverOpensTheObject() {

        // given
        when(mediaStorage.length(KEY)).thenReturn(Optional.of(4L));

        // when
        var actual = mvc.head().uri(URL).with(anonymous()).exchange().getResponse();

        // then
        assertThat(actual.getStatus()).isEqualTo(200);
        assertThat(actual.getContentAsByteArray()).isEmpty();
        assertThat(actual.getContentType()).isEqualTo("image/png");
        assertThat(actual.getHeader(HttpHeaders.CONTENT_LENGTH)).isEqualTo("4");
        assertThat(actual.getHeader(HttpHeaders.ETAG)).isEqualTo(ETAG);
        assertThat(actual.getHeader(HttpHeaders.CACHE_CONTROL)).contains("immutable");
        verify(mediaStorage, never()).open(anyString());
    }

    @Test
    void headForAnObjectTheStoreDoesNotHoldIs404() {

        // given
        when(mediaStorage.length(KEY)).thenReturn(Optional.empty());

        // when
        var actual = mvc.head().uri(URL).with(anonymous()).exchange().getResponse();

        // then
        assertThat(actual.getStatus()).isEqualTo(404);
        assertThat(actual.getHeader(HttpHeaders.ETAG)).isNull();
        verify(mediaStorage, never()).open(anyString());
    }

    @ParameterizedTest
    @ValueSource(strings = {ETAG, "W/" + ETAG, "\"other\", " + ETAG})
    void aCallerThatAlreadyHoldsTheImageIsAnswered304AndTheObjectIsNeverOpened(String ifNoneMatch) {

        // given
        when(mediaStorage.length(KEY)).thenReturn(Optional.of(4L));

        // when
        var actual = mvc.get().uri(URL).header(HttpHeaders.IF_NONE_MATCH, ifNoneMatch).with(anonymous()).exchange()
                .getResponse();

        // then
        assertThat(actual.getStatus()).isEqualTo(304);
        assertThat(actual.getContentAsByteArray()).isEmpty();
        assertThat(actual.getHeader(HttpHeaders.ETAG)).isEqualTo(ETAG);
        assertThat(actual.getHeader(HttpHeaders.CACHE_CONTROL)).contains("immutable");
        verify(mediaStorage, never()).open(anyString());
    }

    @Test
    void aCallerHoldingSomethingElseUnderThatAddressIsSentTheImage() throws IOException {

        // given
        when(mediaStorage.length(KEY)).thenReturn(Optional.of(4L));
        when(mediaStorage.open(KEY)).thenReturn(Optional.of(new MediaContent(new Tracked(BYTES), BYTES.length)));

        // when
        var actual = mvc.get().uri(URL).header(HttpHeaders.IF_NONE_MATCH, "\"something-else\"").with(anonymous())
                .exchange().getResponse();

        // then
        assertThat(actual.getStatus()).isEqualTo(200);
        assertThat(actual.getContentAsByteArray()).isEqualTo(BYTES);
        assertThat(actual.getHeader(HttpHeaders.ETAG)).isEqualTo(ETAG);
    }

    @Test
    void anObjectDeletedBetweenTheTwoStoreCallsIs404AndCarriesNoLongLivedCaching() {

        // given
        when(mediaStorage.length(KEY)).thenReturn(Optional.of(4L));
        when(mediaStorage.open(KEY)).thenReturn(Optional.empty());

        // when
        var actual = mvc.get().uri(URL).header(HttpHeaders.IF_NONE_MATCH, "\"something-else\"").with(anonymous())
                .exchange().getResponse();

        // then
        assertThat(actual.getStatus()).isEqualTo(404);
        assertThat(actual.getHeader(HttpHeaders.ETAG)).isNull();
        assertThat(actual.getHeaders(HttpHeaders.CACHE_CONTROL)).noneMatch(value -> value.contains("immutable"));
    }

    @Test
    void aCallerRevalidatingAnImageThatWasDeletedIsTold404Not304() {

        // given
        when(mediaStorage.length(KEY)).thenReturn(Optional.empty());

        // when
        var actual = mvc.get().uri(URL).header(HttpHeaders.IF_NONE_MATCH, ETAG).with(anonymous()).exchange()
                .getResponse();

        // then
        assertThat(actual.getStatus()).isEqualTo(404);
    }

    @Test
    void aRangeRequestIsAnsweredWithTheWholeImage() throws IOException {

        // given
        when(mediaStorage.open(KEY)).thenReturn(Optional.of(new MediaContent(new Tracked(BYTES), BYTES.length)));

        // when
        var actual = mvc.get().uri(URL).header(HttpHeaders.RANGE, "bytes=0-1").with(anonymous()).exchange()
                .getResponse();

        // then
        assertThat(actual.getStatus()).isEqualTo(200);
        assertThat(actual.getContentAsByteArray()).isEqualTo(BYTES);
        assertThat(actual.getHeader(HttpHeaders.CONTENT_RANGE)).isNull();
        assertThat(actual.getHeader(HttpHeaders.ACCEPT_RANGES)).isNull();
    }

    @Test
    void aStoreThatDoesNotSayTheLengthIsServedWithoutOne() throws IOException {

        // given
        when(mediaStorage.open(KEY)).thenReturn(Optional.of(new MediaContent(new Tracked(BYTES), -1)));

        // when
        var actual = mvc.get().uri(URL).with(anonymous()).exchange().getResponse();

        // then
        assertThat(actual.getStatus()).isEqualTo(200);
        assertThat(actual.getContentAsByteArray()).isEqualTo(BYTES);
        assertThat(actual.getHeader(HttpHeaders.CONTENT_LENGTH)).isNull();
    }

    /**
     * A store failure is tested by calling the handler directly, here and in the three tests below: a request that ends
     * in an exception leaves {@code MockMvcTester} no response to read back, and the response is what they are about.
     */
    @Test
    void aReadThatFailsBeforeAnythingWasSentLeavesNoHeaderTellingACacheToKeepTheError() {

        // given
        var stream = new Tracked(BYTES, 0);
        when(mediaStorage.open(KEY)).thenReturn(Optional.of(new MediaContent(stream, BYTES.length)));
        var response = new MockHttpServletResponse();

        // when
        var actual = catchThrowable(() -> new MediaController(mediaStorage).serve(aGetOfTheImage(), response));

        // then
        assertThat(actual).isInstanceOf(IOException.class);
        assertThat(response.getHeaders(HttpHeaders.CACHE_CONTROL)).noneMatch(value -> value.contains("immutable"));
        assertThat(response.getHeader(HttpHeaders.ETAG)).isNull();
        assertThat(stream.closed).isTrue();
    }

    @Test
    void aReadThatFailsAfterTheResponseStartedStillClosesTheObject() {

        // given
        var stream = new Tracked(new byte[40_000], 20_000);
        when(mediaStorage.open(KEY)).thenReturn(Optional.of(new MediaContent(stream, 40_000)));
        var response = new MockHttpServletResponse();

        // when
        var actual = catchThrowable(() -> new MediaController(mediaStorage).serve(aGetOfTheImage(), response));

        // then
        assertThat(actual).isInstanceOf(IOException.class);
        assertThat(response.isCommitted()).isTrue();
        assertThat(stream.closed).isTrue();
    }

    @Test
    void anObjectTheStoreCannotOpenAfterAMismatchedRevalidationLeavesNoLongLivedCaching() {

        // given
        when(mediaStorage.length(KEY)).thenReturn(Optional.of(4L));
        when(mediaStorage.open(KEY)).thenThrow(new UncheckedIOException(new IOException("the store went away")));
        var request = aGetOfTheImage();
        request.addHeader(HttpHeaders.IF_NONE_MATCH, "\"something-else\"");
        var response = new MockHttpServletResponse();

        // when
        var actual = catchThrowable(() -> new MediaController(mediaStorage).serve(request, response));

        // then
        assertThat(actual).isInstanceOf(UncheckedIOException.class);
        assertThat(response.getHeaders(HttpHeaders.CACHE_CONTROL)).noneMatch(value -> value.contains("immutable"));
        assertThat(response.getHeader(HttpHeaders.ETAG)).isNull();
    }

    @Test
    void aCloseThatFailsBeforeTheResponseIsCommittedLeavesNoLongLivedCaching() {

        // given
        var stream = new ByteArrayInputStream(BYTES) {

            @Override
            public void close() throws IOException {

                throw new IOException("the store went away");
            }
        };
        when(mediaStorage.open(KEY)).thenReturn(Optional.of(new MediaContent(stream, BYTES.length)));
        var response = new MockHttpServletResponse();

        // when
        var actual = catchThrowable(() -> new MediaController(mediaStorage).serve(aGetOfTheImage(), response));

        // then
        assertThat(actual).isInstanceOf(IOException.class);
        assertThat(response.isCommitted()).isFalse();
        assertThat(response.getHeaders(HttpHeaders.CACHE_CONTROL)).noneMatch(value -> value.contains("immutable"));
        assertThat(response.getHeader(HttpHeaders.ETAG)).isNull();
    }

    @Test
    void theRouteOnlyReads() {

        // when
        var actual = mvc.delete().uri(URL).with(anonymous()).exchange().getResponse();

        // then
        assertThat(actual.getStatus()).isEqualTo(405);
        verifyNoInteractions(mediaStorage);
    }

    private static MockHttpServletRequest aGetOfTheImage() {

        return new MockHttpServletRequest("GET", URL);
    }

    /** Counts what was read, remembers being closed, and can fail once a number of bytes has been handed out. */
    private static final class Tracked extends InputStream {

        private final ByteArrayInputStream bytes;
        private final int failAfter;
        private int read;
        private boolean closed;

        Tracked(byte[] content) {

            this(content, Integer.MAX_VALUE);
        }

        Tracked(byte[] content, int failAfter) {

            this.bytes = new ByteArrayInputStream(content);
            this.failAfter = failAfter;
        }

        @Override
        public int read() throws IOException {

            if (read >= failAfter) {
                throw new IOException("the store went away");
            }
            var next = bytes.read();
            if (next >= 0) {
                read++;
            }
            return next;
        }

        @Override
        public void close() {

            closed = true;
        }
    }
}
