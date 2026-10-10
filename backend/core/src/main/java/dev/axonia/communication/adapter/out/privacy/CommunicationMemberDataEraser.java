package dev.axonia.communication.adapter.out.privacy;

import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.shared.api.personaldata.ErasureCounts;
import dev.axonia.shared.api.personaldata.MemberDataEraser;
import dev.axonia.shared.api.personaldata.MemberErasure;
import lombok.RequiredArgsConstructor;

/**
 * A member's preference row and their deliveries exist only to describe that one person's relationship with the spine,
 * so both go with them outright rather than being anonymised. The message they point at does not: it is equally the
 * record of what every other recipient received, so it survives its erased recipient and is removed only once nothing
 * references it at all.
 */
@Component
@RequiredArgsConstructor
public class CommunicationMemberDataEraser implements MemberDataEraser {

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    @Transactional
    public ErasureCounts erase(MemberErasure erasure) {

        var params = Map.of("org", erasure.organizationId(), "user", erasure.userId());
        var deleted = jdbc.update(
                "DELETE FROM communication_preference WHERE organization_id = :org AND user_id = :user", params);
        deleted += jdbc.update("DELETE FROM communication_delivery WHERE organization_id = :org AND user_id = :user",
                params);
        deleted += jdbc.update("""
                DELETE FROM communication_message m
                 WHERE m.organization_id = :org
                   AND NOT EXISTS (SELECT 1 FROM communication_delivery d WHERE d.message_id = m.id)
                """, params);
        return new ErasureCounts(deleted, 0, 0, 0);
    }
}
