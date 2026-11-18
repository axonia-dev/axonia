package dev.axonia.media.adapter.out.privacy;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.PurgerTestSupport;
import dev.axonia.media.api.MediaStorage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class HeroThumbnailPurgerTest extends PurgerTestSupport {

    private static final String ORG = "default";
    private static final String OTHER_ORG = "other";

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Autowired
    private MediaStorage mediaStorage;

    @Autowired
    private HeroThumbnailPurger purger;

    @BeforeEach
    void resetMediaStorageStub() {
        reset(mediaStorage);
    }

    @Test
    void purgeRemovesEveryThumbnailAndItsStoredImage() {

        // given
        seedThumbnail(ORG, 0, "communities/7/roundup/a.jpg");
        seedThumbnail(ORG, 1, "communities/7/roundup/b.jpg");

        // when
        purger.purge(ORG);

        // then
        assertThat(count(ORG)).isZero();
        verify(mediaStorage).delete("communities/7/roundup/a.jpg");
        verify(mediaStorage).delete("communities/7/roundup/b.jpg");
    }

    @Test
    void purgeLeavesAnotherOrganizationsThumbnailsAndImages() {

        // given
        seedThumbnail(ORG, 0, "communities/7/roundup/a.jpg");
        seedThumbnail(OTHER_ORG, 0, "communities/8/roundup/c.jpg");

        // when
        purger.purge(ORG);

        // then
        assertThat(count(ORG)).isZero();
        assertThat(count(OTHER_ORG)).isEqualTo(1);
        verify(mediaStorage).delete("communities/7/roundup/a.jpg");
        verifyNoMoreInteractions(mediaStorage);
    }

    @Test
    void purgeSurvivesAMediaStorageFailure() {

        // given
        seedThumbnail(ORG, 0, "communities/7/roundup/a.jpg");
        doThrow(new IllegalStateException("storage is down")).when(mediaStorage).delete(anyString());

        // when
        purger.purge(ORG);

        // then
        assertThat(count(ORG)).isZero();
    }

    private void seedThumbnail(String org, int slice, String mediaKey) {

        jdbc.update("INSERT INTO hero_thumbnail (organization_id, community_id, hero_key, slice_index, accent_hex, "
                + "media_key, created_at) VALUES (:org, 7, 'communities/7/hero/h.png', :slice, '#de3b78', :key, now())",
                Map.of("org", org, "slice", slice, "key", mediaKey));
    }

    private long count(String org) {

        return jdbc.queryForObject("SELECT count(*) FROM hero_thumbnail WHERE organization_id = :org",
                Map.of("org", org), Long.class);
    }
}
