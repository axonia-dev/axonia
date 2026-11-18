package dev.axonia.media.adapter.out.thumbnail;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import dev.axonia.CoreJdbcTestSupport;
import dev.axonia.media.api.AbsoluteMediaLinks;
import dev.axonia.media.api.HeroThumbnailRequest;
import dev.axonia.media.api.MediaContent;
import dev.axonia.media.api.MediaKeys;
import dev.axonia.media.api.MediaStorage;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcHeroThumbnailsTest extends CoreJdbcTestSupport {

    private static final String ORG = "default";
    private static final String HERO_KEY = "communities/7/hero/3f2b8c1e-5d4a-4b6c-9e8f-0a1b2c3d4e5f.png";
    private static final String MEDIA_BASE = "https://media.axonia.example";

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Autowired
    private Clock clock;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private FakeMediaStorage storage;

    private JdbcHeroThumbnails thumbnails;

    @BeforeEach
    void buildThumbnails() throws IOException {

        storage = new FakeMediaStorage();
        storage.objects.put(HERO_KEY, png(new BufferedImage(400, 800, BufferedImage.TYPE_INT_RGB)));
        thumbnails = new JdbcHeroThumbnails(jdbc, storage, new AbsoluteMediaLinks(MEDIA_BASE),
                new HeroThumbnailComposer(), clock, transactionManager);
    }

    @Test
    void composesAndStoresAThumbnailOnceThenReusesIt() {

        // when
        var first = thumbnails.urlFor(request(0, "#de3b78"));
        var second = thumbnails.urlFor(request(0, "#de3b78"));

        // then
        assertThat(first).hasValueSatisfying(url -> assertThat(url).startsWith(MEDIA_BASE + "/communities/7/roundup/"));
        assertThat(second).isEqualTo(first);
        assertThat(storage.stored).singleElement().satisfies(key -> {
            assertThat(MediaKeys.isServable(key)).isTrue();
            assertThat(first).contains(MEDIA_BASE + "/" + key);
        });
        assertThat(storage.contentTypes).containsExactly("image/jpeg");
        assertThat(rows()).isEqualTo(1);
    }

    @Test
    void anotherAccentOrSliceMintsAnotherThumbnail() {

        // when
        var pink = thumbnails.urlFor(request(0, "#de3b78"));
        var green = thumbnails.urlFor(request(0, "#1f9d63"));
        var nextSlice = thumbnails.urlFor(request(1, "#de3b78"));

        // then
        assertThat(List.of(pink.orElseThrow(), green.orElseThrow(), nextSlice.orElseThrow())).doesNotHaveDuplicates();
        assertThat(storage.stored).hasSize(3);
        assertThat(rows()).isEqualTo(3);
    }

    @Test
    void aHeroTheStoreNoLongerHoldsGivesNoThumbnail() {

        // given
        storage.objects.clear();

        // when / then
        assertThat(thumbnails.urlFor(request(0, "#de3b78"))).isEmpty();
        assertThat(storage.stored).isEmpty();
        assertThat(rows()).isZero();
    }

    @Test
    void aHeroThatIsNoImageGivesNoThumbnail() {

        // given
        storage.objects.put(HERO_KEY, "not an image".getBytes());

        // when / then
        assertThat(thumbnails.urlFor(request(0, "#de3b78"))).isEmpty();
        assertThat(rows()).isZero();
    }

    @Test
    void aStorageThatFailsGivesNoThumbnail() {

        // given
        storage.failOnStore = true;

        // when / then
        assertThat(thumbnails.urlFor(request(0, "#de3b78"))).isEmpty();
        assertThat(rows()).isZero();
    }

    @Test
    void aThumbnailRecordedMeanwhileWinsAndTheNewImageIsDiscarded() {

        // given
        storage.onStore = () -> jdbc.update("INSERT INTO hero_thumbnail (organization_id, community_id, hero_key, "
                + "slice_index, accent_hex, media_key, created_at) VALUES (:org, 7, :hero, 0, '#de3b78', "
                + "'communities/7/roundup/earlier.jpg', now())", Map.of("org", ORG, "hero", HERO_KEY));

        // when
        var url = thumbnails.urlFor(request(0, "#de3b78"));

        // then
        assertThat(url).contains(MEDIA_BASE + "/communities/7/roundup/earlier.jpg");
        assertThat(storage.deleted).isEqualTo(storage.stored);
        assertThat(rows()).isEqualTo(1);
    }

    @Test
    void aDiscardThatFailsStillAnswersTheRecordedThumbnail() {

        // given
        storage.failOnDelete = true;
        storage.onStore = () -> jdbc.update("INSERT INTO hero_thumbnail (organization_id, community_id, hero_key, "
                + "slice_index, accent_hex, media_key, created_at) VALUES (:org, 7, :hero, 0, '#de3b78', "
                + "'communities/7/roundup/earlier.jpg', now())", Map.of("org", ORG, "hero", HERO_KEY));

        // when / then
        assertThat(thumbnails.urlFor(request(0, "#de3b78")))
                .contains(MEDIA_BASE + "/communities/7/roundup/earlier.jpg");
    }

    @Test
    void aRecordThatCannotBeWrittenGivesNoThumbnailAndLeavesTheCallersTransactionUsable() {

        // given - a key longer than the column holds fails the INSERT inside the test's own transaction
        var tooLong = "communities/7/hero/" + "x".repeat(600) + ".png";
        storage.objects.put(tooLong, storage.objects.get(HERO_KEY));
        var request = HeroThumbnailRequest.builder().organizationId(ORG).communityId(7L).heroKey(tooLong).heroFocusX(50)
                .heroFocusY(30).sliceIndex(0).accentHex("#de3b78").build();

        // when / then
        assertThat(thumbnails.urlFor(request)).isEmpty();
        assertThat(rows()).isZero();
        assertThat(thumbnails.urlFor(request(0, "#de3b78"))).isPresent();
    }

    private static HeroThumbnailRequest request(int slice, String accentHex) {

        return HeroThumbnailRequest.builder().organizationId(ORG).communityId(7L).heroKey(HERO_KEY).heroFocusX(50)
                .heroFocusY(30).sliceIndex(slice).accentHex(accentHex).build();
    }

    private long rows() {

        return jdbc.queryForObject("SELECT count(*) FROM hero_thumbnail WHERE organization_id = :org",
                Map.of("org", ORG), Long.class);
    }

    private static byte[] png(BufferedImage image) throws IOException {

        var bytes = new ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return bytes.toByteArray();
    }

    private static final class FakeMediaStorage implements MediaStorage {

        private final Map<String, byte[]> objects = new HashMap<>();
        private final List<String> stored = new ArrayList<>();
        private final List<String> contentTypes = new ArrayList<>();
        private final List<String> deleted = new ArrayList<>();
        private Runnable onStore = () -> {
        };
        private boolean failOnStore;
        private boolean failOnDelete;

        @Override
        public void store(String key, InputStream data, String contentType, long size) {

            if (failOnStore) {
                throw new IllegalStateException("storage is down");
            }
            try {
                objects.put(key, data.readAllBytes());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            stored.add(key);
            contentTypes.add(contentType);
            onStore.run();
        }

        @Override
        public void delete(String key) {

            if (failOnDelete) {
                throw new IllegalStateException("storage is down");
            }
            objects.remove(key);
            deleted.add(key);
        }

        @Override
        public Optional<MediaContent> open(String key) {

            return Optional.ofNullable(objects.get(key))
                    .map(bytes -> new MediaContent(new ByteArrayInputStream(bytes), bytes.length));
        }

        @Override
        public Optional<Long> length(String key) {

            return Optional.ofNullable(objects.get(key)).map(bytes -> (long) bytes.length);
        }
    }
}
