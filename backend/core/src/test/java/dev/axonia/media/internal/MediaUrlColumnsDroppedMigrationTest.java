package dev.axonia.media.internal;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import dev.axonia.CoreJdbcTestSupport;

import static org.assertj.core.api.Assertions.assertThat;

/** An image's address is minted from its key; no table stores one, and nothing still reads a stored one. */
class MediaUrlColumnsDroppedMigrationTest extends CoreJdbcTestSupport {

    @Autowired
    private NamedParameterJdbcTemplate jdbc;

    @Test
    void noTableStoresAnImageAddress() {

        // when
        var actual = jdbc.queryForList("""
                SELECT table_name || '.' || column_name FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND (table_name, column_name) IN (('identity_user', 'avatar_url'),
                      ('identity_user', 'avatar_override_url'), ('reaction_definition', 'image_url'))
                """, Map.of(), String.class);

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void theKeysEveryAddressIsMintedFromAreStillThere() {

        // when
        var actual = jdbc.queryForList("""
                SELECT table_name || '.' || column_name FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND (table_name, column_name) IN (('identity_user', 'avatar_media_key'),
                      ('identity_user', 'avatar_override_key'), ('reaction_definition', 'image_key'))
                """, Map.of(), String.class);

        // then
        assertThat(actual).containsExactlyInAnyOrder("identity_user.avatar_media_key",
                "identity_user.avatar_override_key", "reaction_definition.image_key");
    }

    @Test
    void theTriggerThatRetiredAStoredAddressIsGoneWithItsFunction() {

        // when
        var triggers = jdbc.queryForObject(
                "SELECT count(*) FROM pg_trigger WHERE tgname = 'trg_identity_user_retire_avatar_address'", Map.of(),
                Long.class);
        var functions = jdbc.queryForObject(
                "SELECT count(*) FROM pg_proc WHERE proname = 'identity_user_retire_avatar_address'", Map.of(),
                Long.class);

        // then
        assertThat(triggers).isZero();
        assertThat(functions).isZero();
    }

    @Test
    void anAvatarUpdateSucceedsOnceTheAddressesAreGone() {

        // given
        var id = jdbc.queryForObject("""
                INSERT INTO identity_user (organization_id, user_id, display_name, scim_id, arrival,
                    avatar_media_key, avatar_override_key, created_at, updated_at, version)
                VALUES ('default', 'avatar-user', 'Avatar User', 'avatar-user', 'SYNCED',
                    'avatars/default/old.jpg', 'avatars/default/own-old.png', now(), now(), 0)
                RETURNING id
                """, Map.of(), Long.class);

        // when
        var updated = jdbc.update("""
                UPDATE identity_user
                SET avatar_media_key = 'avatars/default/new.jpg', avatar_override_key = 'avatars/default/own-new.png'
                WHERE id = :id
                """, Map.of("id", id));

        // then
        assertThat(updated).isOne();
        assertThat(jdbc.queryForMap("SELECT avatar_media_key, avatar_override_key FROM identity_user WHERE id = :id",
                Map.of("id", id))).containsEntry("avatar_media_key", "avatars/default/new.jpg")
                .containsEntry("avatar_override_key", "avatars/default/own-new.png");
    }
}
