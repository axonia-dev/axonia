package dev.axonia.media.adapter.out.thumbnail;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.Objects;
import java.util.Optional;

import javax.imageio.ImageIO;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import dev.axonia.media.adapter.out.thumbnail.HeroThumbnailComposer.Slice;
import dev.axonia.media.api.AbsoluteMediaLinks;
import dev.axonia.media.api.HeroThumbnailRequest;
import dev.axonia.media.api.HeroThumbnails;
import dev.axonia.media.api.MediaKeys;
import dev.axonia.media.api.MediaStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Composes a thumbnail once per (hero, slice, accent) and records it, so a preview and the send share one stored image.
 * A hero that cannot be read, decoded, composed or recorded answers empty and the band falls back to the accent
 * gradient. The image is stored before the row is written, so a rollback leaves an orphaned blob.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JdbcHeroThumbnails implements HeroThumbnails {

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
    private final PlatformTransactionManager transactionManager;

    /**
     * Runs on a savepoint of the caller's transaction, so a failed read or write of the record rolls back only itself
     * and the caller's transaction stays usable.
     */
    @Override
    @Transactional
    public Optional<String> urlFor(HeroThumbnailRequest request) {

        var savepoint = new TransactionTemplate(transactionManager);
        savepoint.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
        try {
            return Objects.requireNonNullElse(savepoint.execute(status -> recorded(request)
                    .or(() -> composed(request).map(key -> record(request, key))).map(absoluteMediaLinks::urlFor)),
                    Optional.empty());
        } catch (DataAccessException e) {
            log.warn("Hero thumbnail skipped: the record for hero '{}' of community {} failed", request.heroKey(),
                    request.communityId(), e);
            return Optional.empty();
        }
    }

    private Optional<String> recorded(HeroThumbnailRequest request) {

        return jdbc.queryForList("SELECT media_key FROM hero_thumbnail" + WHERE_REQUESTED, parameters(request),
                String.class).stream().findFirst();
    }

    private Optional<String> composed(HeroThumbnailRequest request) {

        try {
            var hero = decoded(request.heroKey());
            if (hero.isEmpty()) {
                log.warn("Hero thumbnail skipped: hero '{}' of community {} could not be read as an image",
                        request.heroKey(), request.communityId());
                return Optional.empty();
            }
            var jpeg = composer.compose(hero.get(), slice(request));
            var key = MediaKeys.heroThumbnail(request.communityId());
            mediaStorage.store(key, new ByteArrayInputStream(jpeg), CONTENT_TYPE, jpeg.length);
            return Optional.of(key);
        } catch (IOException | RuntimeException e) {
            log.warn("Hero thumbnail skipped: hero '{}' of community {} could not be composed", request.heroKey(),
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
    private String record(HeroThumbnailRequest request, String key) {

        var recorded = jdbc.queryForObject("""
                INSERT INTO hero_thumbnail (organization_id, community_id, hero_key, slice_index, accent_hex,
                                               media_key, created_at)
                VALUES (:org, :community, :hero, :slice, :accent, :key, :createdAt)
                ON CONFLICT (organization_id, community_id, hero_key, slice_index, accent_hex)
                    DO UPDATE SET media_key = hero_thumbnail.media_key
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
            log.warn("Orphaned hero thumbnail '{}': storage delete failed", key, e);
        }
    }

    private static MapSqlParameterSource parameters(HeroThumbnailRequest request) {

        return new MapSqlParameterSource().addValue("org", request.organizationId())
                .addValue("community", request.communityId()).addValue("hero", request.heroKey())
                .addValue("slice", request.sliceIndex()).addValue("accent", request.accentHex());
    }

    private static Slice slice(HeroThumbnailRequest request) {

        return Slice.builder().focusX(request.heroFocusX()).focusY(request.heroFocusY()).index(request.sliceIndex())
                .accent(Color.decode(request.accentHex())).build();
    }
}
