package dev.axonia.access.adapter.out.privacy;

import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.shared.api.personaldata.ErasureCounts;
import dev.axonia.shared.api.personaldata.MemberDataEraser;
import dev.axonia.shared.api.personaldata.MemberErasure;
import lombok.RequiredArgsConstructor;

/**
 * A role binding names a subject, and that subject can be a user or a group. {@code subject_id} is only a user id when
 * {@code subject_type} is {@code USER}; for a {@code GROUP} binding it is a group name, which could collide with the
 * erased member's user id. The {@code subject_type} predicate is therefore load-bearing, not defensive noise.
 */
@Component
@RequiredArgsConstructor
public class AccessMemberDataEraser implements MemberDataEraser {

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    @Transactional
    public ErasureCounts erase(MemberErasure erasure) {

        var params = Map.of("org", erasure.organizationId(), "user", erasure.userId());
        var deleted = jdbc.update("DELETE FROM role_binding WHERE organization_id = :org "
                + "AND subject_type = 'USER' AND subject_id = :user", params);
        return new ErasureCounts(deleted, 0, 0, 0);
    }
}
