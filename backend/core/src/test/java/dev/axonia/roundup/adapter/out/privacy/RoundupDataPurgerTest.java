package dev.axonia.roundup.adapter.out.privacy;

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

class RoundupDataPurgerTest extends PurgerTestSupport {

    private static final String ORG = "default";

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Autowired
    private MediaStorage mediaStorage;

    @Autowired
    private RoundupDataPurger purger;

    @BeforeEach
    void resetMediaStorageStub() {
        reset(mediaStorage);
    }

    @Test
    void purgeRemovesTheOrganizationsSetting() {

        jdbc.update("INSERT INTO roundup_settings (organization_id, enabled, created_at, updated_at, version) "
                + "VALUES (:org, true, now(), now(), 0)", Map.of("org", ORG));

        purger.purge(ORG);

        assertThat(count("roundup_settings")).isZero();
    }

    @Test
    void purgeRemovesEveryThumbnailAndItsStoredImage() {

        // given
        seedThumbnail(0, "communities/7/roundup/a.jpg");
        seedThumbnail(1, "communities/7/roundup/b.jpg");

        // when
        purger.purge(ORG);

        // then
        assertThat(count("roundup_thumbnail")).isZero();
        verify(mediaStorage).delete("communities/7/roundup/a.jpg");
        verify(mediaStorage).delete("communities/7/roundup/b.jpg");
    }

    @Test
    void purgeSurvivesAMediaStorageFailure() {

        // given
        seedThumbnail(0, "communities/7/roundup/a.jpg");
        doThrow(new IllegalStateException("storage is down")).when(mediaStorage).delete(anyString());

        // when
        purger.purge(ORG);

        // then
        assertThat(count("roundup_thumbnail")).isZero();
    }

    private void seedThumbnail(int slice, String mediaKey) {

        jdbc.update("INSERT INTO roundup_thumbnail (organization_id, community_id, hero_key, slice_index, accent_hex, "
                + "media_key, created_at) VALUES (:org, 7, 'communities/7/hero/h.png', :slice, '#de3b78', :key, now())",
                Map.of("org", ORG, "slice", slice, "key", mediaKey));
    }

    private long count(String table) {

        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE organization_id = :org",
                Map.of("org", ORG), Long.class);
    }
}
