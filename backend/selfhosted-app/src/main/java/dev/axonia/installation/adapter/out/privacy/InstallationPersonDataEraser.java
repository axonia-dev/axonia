package dev.axonia.installation.adapter.out.privacy;

import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.shared.api.personaldata.ErasureCounts;
import dev.axonia.shared.api.personaldata.PersonDataEraser;
import dev.axonia.shared.api.personaldata.PersonErasure;
import lombok.RequiredArgsConstructor;

/** A person eraser, not a member one: the history belongs to the installation, not to an Organization. */
@Component
@RequiredArgsConstructor
class InstallationPersonDataEraser implements PersonDataEraser {

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    @Transactional
    public ErasureCounts erase(PersonErasure erasure) {

        var cleared = jdbc.update(
                "UPDATE installation_settings_change SET actor_user_id = NULL WHERE actor_user_id = :subject",
                Map.of("subject", erasure.subject()));
        return new ErasureCounts(0, cleared, 0, 0);
    }
}
