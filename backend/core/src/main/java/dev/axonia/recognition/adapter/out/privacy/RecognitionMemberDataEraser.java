package dev.axonia.recognition.adapter.out.privacy;

import java.sql.Timestamp;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.recognition.adapter.out.persistence.ReactionDefinitionRepository;
import dev.axonia.recognition.adapter.out.persistence.ReactionRepository;
import dev.axonia.shared.api.personaldata.ErasureCounts;
import dev.axonia.shared.api.personaldata.MemberDataEraser;
import dev.axonia.shared.api.personaldata.MemberErasure;
import lombok.RequiredArgsConstructor;

/**
 * A love credits the contribution it rides, not the person who gave it, so {@code contribution_love.lover_user_id} is
 * anonymised in place: the contribution's love count must survive the lover's erasure. {@code recognized_contribution}
 * has carried no personal column since V4 - contributor credit is derived from the owning bounded context at read time
 * - so there is nothing else here to erase.
 * <p>
 * Each UPDATE bumps {@code version} and stamps {@code updated_at} from the erasure's own instant. Without that, a
 * request that loaded the row just before the erasure still matches its own optimistic-lock predicate afterwards and
 * writes the whole entity back - including the pre-erasure subject - silently resurrecting an identifier a legal
 * erasure just removed. The instant is a plain named parameter from the caller's Clock, never SQL {@code now()}.
 * <p>
 * A member's placements go with them: {@code contribution_reaction} rows the member left are a thing they said, and
 * they are deleted outright rather than anonymised, exactly as {@link ReactionRepository#deleteByReactor} names it. A
 * proposal in {@code reaction_definition} is the opposite: once approved it belongs to the Organization, not to the
 * member who proposed or approved it, so it is cleared in place through
 * {@link ReactionDefinitionRepository#anonymiseProposer} rather than taken down with its author.
 */
@Component
@RequiredArgsConstructor
public class RecognitionMemberDataEraser implements MemberDataEraser {

    private final NamedParameterJdbcTemplate jdbc;

    private final ReactionRepository reactionRepository;

    private final ReactionDefinitionRepository reactionDefinitionRepository;

    @Override
    @Transactional
    public ErasureCounts erase(MemberErasure erasure) {

        var params = Map.of("org", erasure.organizationId(), "user", erasure.userId(), "surrogate",
                erasure.surrogateId(), "erasedAt", Timestamp.from(erasure.erasedAt()));
        var anonymised = jdbc.update("UPDATE contribution_love SET lover_user_id = :surrogate, version = version + 1, "
                + "updated_at = :erasedAt WHERE organization_id = :org AND lover_user_id = :user", params);

        var reactionsRemoved = reactionRepository.deleteByReactor(erasure.organizationId(), erasure.userId());
        var proposalsCleared = reactionDefinitionRepository.anonymiseProposer(erasure.organizationId(),
                erasure.userId(), erasure.erasedAt());

        return new ErasureCounts(reactionsRemoved, proposalsCleared, anonymised, 0);
    }
}
