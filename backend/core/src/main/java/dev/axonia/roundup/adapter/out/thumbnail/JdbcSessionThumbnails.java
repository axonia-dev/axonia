package dev.axonia.roundup.adapter.out.thumbnail;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.Optional;

import javax.imageio.ImageIO;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.media.api.AbsoluteMediaLinks;
import dev.axonia.media.api.MediaKeys;
import dev.axonia.media.api.MediaStorage;
import dev.axonia.roundup.adapter.out.thumbnail.HeroThumbnailComposer.Slice;
import dev.axonia.roundup.application.port.out.SessionThumbnails;
import dev.axonia.roundup.domain.ThumbnailRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Composes a thumbnail once per (hero, slice, accent) and records it, so a preview and the send share one stored image.
 * A hero that cannot be read, decoded or composed answers empty and the band falls back to the accent gradient. The
 * image is stored before the row joins the caller's transaction, so a rollback there leaves an orphaned blob.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JdbcSessionThumbnails implements SessionThumbnails {

    private static final String CONTENT_TYPE = "image/jpeg";

    private static final String WHERE_REQUESTED = " WHERE organization_id = :org AND community_id = :community"
            + " AND hero_key = :hero AND slice_index = :slice AND accent_hex = :accent";

    static {
        // A packaged jar registers the WebP reader only once the plugins are scanned on the application class loader.
        ImageIO.scanForPlugins();
    }

    private final NamedParameterJdbcTemplate jdbc;
    private final MediaStorage mediaStorage;
    private final AbsoluteMediaLinks absoluteMediaLinks;
    private final HeroThumbnailComposer composer;
    private final Clock clock;

    @Override
    @Transactional
    public Optional<String> urlFor(ThumbnailRequest request) {

        return recorded(request).or(() -> composed(request).map(key -> record(request, key)))
                .map(absoluteMediaLinks::urlFor);
    }

    private Optional<String> recorded(ThumbnailRequest request) {

        return jdbc.queryForList("SELECT media_key FROM roundup_thumbnail" + WHERE_REQUESTED, parameters(request),
                String.class).stream().findFirst();
    }

    private Optional<String> composed(ThumbnailRequest request) {

        try {
            var hero = decoded(request.heroKey());
            if (hero.isEmpty()) {
                log.warn("Roundup thumbnail skipped: hero '{}' of community {} could not be read as an image",
                        request.heroKey(), request.communityId());
                return Optional.empty();
            }
            var jpeg = composer.compose(hero.get(), slice(request));
            var key = MediaKeys.roundupThumbnail(request.communityId());
            mediaStorage.store(key, new ByteArrayInputStream(jpeg), CONTENT_TYPE, jpeg.length);
            return Optional.of(key);
        } catch (IOException | RuntimeException e) {
            log.warn("Roundup thumbnail skipped: hero '{}' of community {} could not be composed", request.heroKey(),
                    request.communityId(), e);
            return Optional.empty();
        }
    }

    private Optional<BufferedImage> decoded(String heroKey) throws IOException {

        var content = mediaStorage.open(heroKey);
        if (content.isEmpty()) {
            return Optional.empty();
        }
        try (var stream = content.get().stream()) {
            return Optional.ofNullable(ImageIO.read(stream));
        }
    }

    /** The key recorded for the request: this one, or the one a concurrent composition recorded first. */
    private String record(ThumbnailRequest request, String key) {

        var recorded = jdbc.queryForObject("""
                INSERT INTO roundup_thumbnail (organization_id, community_id, hero_key, slice_index, accent_hex,
                                               media_key, created_at)
                VALUES (:org, :community, :hero, :slice, :accent, :key, :createdAt)
                ON CONFLICT (organization_id, community_id, hero_key, slice_index, accent_hex)
                    DO UPDATE SET media_key = roundup_thumbnail.media_key
                RETURNING media_key
                """, parameters(request).addValue("key", key).addValue("createdAt", Timestamp.from(clock.instant())),
                String.class);
        if (!key.equals(recorded)) {
            discard(key);
        }
        return recorded;
    }

    private void discard(String key) {

        try {
            mediaStorage.delete(key);
        } catch (RuntimeException e) {
            log.warn("Orphaned roundup thumbnail '{}': storage delete failed", key, e);
        }
    }

    private static MapSqlParameterSource parameters(ThumbnailRequest request) {

        return new MapSqlParameterSource().addValue("org", request.organizationId())
                .addValue("community", request.communityId()).addValue("hero", request.heroKey())
                .addValue("slice", request.sliceIndex()).addValue("accent", request.accentHex());
    }

    private static Slice slice(ThumbnailRequest request) {

        return Slice.builder().focusX(request.heroFocusX()).focusY(request.heroFocusY()).index(request.sliceIndex())
                .accent(Color.decode(request.accentHex())).build();
    }
}
