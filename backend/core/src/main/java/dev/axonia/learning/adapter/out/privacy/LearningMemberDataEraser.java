package dev.axonia.learning.adapter.out.privacy;

import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.communication.api.ExpiredSubjects;
import dev.axonia.learning.domain.OpenDoorSubject;
import dev.axonia.shared.api.personaldata.ErasureCounts;
import dev.axonia.shared.api.personaldata.MemberDataEraser;
import dev.axonia.shared.api.personaldata.MemberErasure;
import lombok.RequiredArgsConstructor;

/**
 * Every intent the member named in the Organization goes with them. Leaving each community on the way to erasure
 * deletes the intents there first; this deletes whatever that path did not reach. The record of every expired
 * open-to-questions notice about them goes too, since its subject names them; the live notices go with communication's
 * own erasure of the messages about them.
 */
@Component
@RequiredArgsConstructor
public class LearningMemberDataEraser implements MemberDataEraser {

    private final NamedParameterJdbcTemplate jdbc;
    private final ExpiredSubjects expiredSubjects;

    @Override
    @Transactional
    public ErasureCounts erase(MemberErasure erasure) {

        var deleted = jdbc.update("DELETE FROM learning_intent WHERE organization_id = :org AND user_id = :user",
                Map.of("org", erasure.organizationId(), "user", erasure.userId()));
        deleted += expiredSubjects.forgetEndingWith(erasure.organizationId(), OpenDoorSubject.MESSAGE_TYPE,
                OpenDoorSubject.aboutSuffix(erasure.userId()));
        return new ErasureCounts(deleted, 0, 0, 0);
    }
}
