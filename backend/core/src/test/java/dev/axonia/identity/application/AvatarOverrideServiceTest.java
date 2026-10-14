package dev.axonia.identity.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.identity.api.ClaimsReader;
import dev.axonia.identity.api.IdentityClaims;
import dev.axonia.identity.application.port.out.UserStore;
import dev.axonia.identity.domain.AvatarOverrideUpdate;
import dev.axonia.identity.domain.UserAccountMother;
import dev.axonia.media.api.ImageHeaders;
import dev.axonia.media.api.ImageUpload;
import dev.axonia.media.api.MediaStorage;
import dev.axonia.shared.api.error.NotFoundException;
import dev.axonia.shared.api.error.ValidationException;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@MockitoSettings
class AvatarOverrideServiceTest {

    private static final String ORG = "acme";
    private static final String USER = "alice";
    private static final Instant NOW = Instant.parse("2026-07-07T12:00:00Z");

    @Mock
    private UserStore userStore;

    @Mock
    private ClaimsReader claimsReader;

    @Mock
    private MediaStorage mediaStorage;

    @Mock
    private Clock clock;

    private AvatarOverrideService service;

    @BeforeEach
    void setUp() {

        lenient().when(claimsReader.read())
                .thenReturn(IdentityClaims.builder().organizationId(ORG).userId(USER).build());
        lenient().when(clock.instant()).thenReturn(NOW);
        service = new AvatarOverrideService(userStore, claimsReader, mediaStorage, clock);
    }

    private void seedUser(String overrideKey) {

        when(userStore.findByOrganizationIdAndUserId(ORG, USER)).thenReturn(Optional
                .of(UserAccountMother.any().organizationId(ORG).userId(USER).avatarOverrideKey(overrideKey).build()));
    }

    @Test
    void storingAnOverridePointsTheProfileAtIt() {

        seedUser(null);

        var previousKey = service.set(new ImageUpload(pngBytes(), "image/png", "me.png"));

        var updateCaptor = ArgumentCaptor.forClass(AvatarOverrideUpdate.class);
        verify(userStore).updateAvatarOverride(updateCaptor.capture(), eq(NOW));
        var update = updateCaptor.getValue();
        assertThat(update.organizationId()).isEqualTo(ORG);
        assertThat(update.userId()).isEqualTo(USER);
        assertThat(update.key()).startsWith("avatars/" + ORG + "/").endsWith(".png");
        assertThat(previousKey).isNull();
        verify(mediaStorage, never()).delete(anyString());
    }

    /**
     * The realistic caller shape (see UserController): the transactional write returns the key it superseded, and only
     * once that call has returned - its transaction already committed - does the caller hand the key to
     * {@link AvatarOverrideService#deletePreviousBlob(String)}.
     */
    @Test
    void replacingAnOverrideDeletesTheBlobItReplacedOnceTheWriteHasCommitted() {

        seedUser("avatars/" + ORG + "/old.png");

        var previousKey = service.set(new ImageUpload(pngBytes(), "image/png", "me.png"));
        verify(mediaStorage, never()).delete(anyString());

        service.deletePreviousBlob(previousKey);

        assertThat(previousKey).isEqualTo("avatars/" + ORG + "/old.png");
        verify(mediaStorage).delete("avatars/" + ORG + "/old.png");
    }

    @Test
    void deletePreviousBlobWithNoKeyDeletesNothing() {

        service.deletePreviousBlob(null);

        verifyNoInteractions(mediaStorage);
    }

    @Test
    void clearingDeletesTheBlobAndTheColumnsOnceTheWriteHasCommitted() {

        seedUser("avatars/" + ORG + "/own.png");

        var previousKey = service.clear();
        verify(mediaStorage, never()).delete(anyString());

        service.deletePreviousBlob(previousKey);

        verify(mediaStorage).delete("avatars/" + ORG + "/own.png");
        verify(userStore).clearAvatarOverride(ORG, USER, NOW);
    }

    @Test
    void clearingWithNoOverrideDeletesNothing() {

        seedUser(null);

        var previousKey = service.clear();
        service.deletePreviousBlob(previousKey);

        verify(userStore).clearAvatarOverride(ORG, USER, NOW);
        verifyNoInteractions(mediaStorage);
    }

    @Test
    void aFailedBlobDeleteIsSwallowedRatherThanFailingTheCall() {

        seedUser("avatars/" + ORG + "/own.png");
        doThrow(new RuntimeException("object store down")).when(mediaStorage).delete("avatars/" + ORG + "/own.png");

        var previousKey = service.clear();

        assertThatCode(() -> service.deletePreviousBlob(previousKey)).doesNotThrowAnyException();
        verify(userStore).clearAvatarOverride(ORG, USER, NOW);
    }

    @Test
    void aCallerWithNoOrganizationIsRefused() {

        // given - a fresh social sign-in before any per-org profile exists: IdentityClaims documents
        // organizationId as null for exactly this caller shape
        when(claimsReader.read()).thenReturn(IdentityClaims.builder().organizationId(null).userId(USER).build());

        assertThatThrownBy(() -> service.set(new ImageUpload(pngBytes(), "image/png", "me.png")))
                .isInstanceOf(ValidationException.class).hasMessageContaining("organization");
        verifyNoInteractions(mediaStorage);
    }

    @Test
    void aCallerWithNoProfileRowIsRefused() {

        when(userStore.findByOrganizationIdAndUserId(ORG, USER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.set(new ImageUpload(pngBytes(), "image/png", "me.png")))
                .isInstanceOf(NotFoundException.class).hasMessageContaining(USER);
        verifyNoInteractions(mediaStorage);
    }

    @Test
    void aFileWhoseBytesContradictItsTypeIsRefused() {

        var notReallyPng = new ImageUpload("plain text".getBytes(UTF_8), "image/png", "me.png");

        assertThatThrownBy(() -> service.set(notReallyPng)).isInstanceOf(ValidationException.class)
                .hasMessageContaining("does not match its declared type");
        verifyNoInteractions(mediaStorage);
    }

    @Test
    void aTypeTheProductDoesNotServeIsRefused() {

        var svg = new ImageUpload("<svg/>".getBytes(UTF_8), "image/svg+xml", "me.svg");

        assertThatThrownBy(() -> service.set(svg)).isInstanceOf(ValidationException.class)
                .hasMessageContaining("Use PNG, JPEG or WebP");
        verifyNoInteractions(mediaStorage);
    }

    @Test
    void anImageOverTheBoundIsRefused() {

        var oversized = new ImageUpload(pngBytesOfLength(3 * 1024 * 1024), "image/png", "big.png");

        assertThatThrownBy(() -> service.set(oversized)).isInstanceOf(ValidationException.class)
                .hasMessageContaining("2 MB");
        verifyNoInteractions(mediaStorage);
    }

    @Test
    void anImageWiderThanTheEdgeIsRefused() {

        var tooWide = new ImageUpload(ImageHeaders.jpeg(513, 512), "image/jpeg", "wide.jpg");

        assertThatThrownBy(() -> service.set(tooWide)).isInstanceOf(ValidationException.class)
                .hasMessage("Image exceeds the maximum of 512 pixels on its longest side");
        verifyNoInteractions(mediaStorage);
    }

    @Test
    void anImageExactlyAtTheEdgeIsStored() {

        seedUser(null);

        assertThatCode(() -> service.set(new ImageUpload(ImageHeaders.jpeg(512, 512), "image/jpeg", "me.jpg")))
                .doesNotThrowAnyException();
    }

    /**
     * A real PNG header padded out to the requested length: the size check must be what rejects this, not the
     * magic-byte check.
     */
    private static byte[] pngBytesOfLength(int length) {

        var header = pngBytes();
        var padded = new byte[length];
        System.arraycopy(header, 0, padded, 0, header.length);
        return padded;
    }

    /** A 1x1 PNG: the magic bytes are what ImageValidator checks, so a real header is required. */
    private static byte[] pngBytes() {

        return Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg==");
    }
}
