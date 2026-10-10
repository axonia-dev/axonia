package dev.axonia.bookmarks.adapter.out.privacy;

import java.sql.Timestamp;
import java.util.Map;

import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.shared.api.personaldata.ErasureCounts;
import dev.axonia.shared.api.personaldata.MemberDataEraser;
import dev.axonia.shared.api.personaldata.MemberErasure;
import lombok.RequiredArgsConstructor;

/**
 * The member's own saves go with them; their authorship on somebody else's save is anonymised in place, because that
 * row belongs to another member and must survive. Order matters: delete first, then anonymise, or the delete would also
 * catch rows the member merely authored.
 * <p>
 * Each UPDATE bumps {@code version} and stamps {@code updated_at} from the erasure's own instant. Without that, a
 * request that loaded the row just before the erasure still matches its own optimistic-lock predicate afterwards and
 * writes the whole entity back - including the pre-erasure subject - silently resurrecting an identifier a legal
 * erasure just removed. The instant is a plain named parameter from the caller's Clock, never SQL {@code now()}.
 */
@Component
@RequiredArgsConstructor
public class BookmarksMemberDataEraser implements MemberDataEraser {

    private final NamedParameterJdbcTemplate jdbc;

    @Override
    @Transactional
    public ErasureCounts erase(MemberErasure erasure) {

        var params = Map.of("org", erasure.organizationId(), "user", erasure.userId(), "surrogate",
                erasure.surrogateId(), "erasedAt", Timestamp.from(erasure.erasedAt()));
        var deleted = jdbc.update("DELETE FROM bookmark WHERE organization_id = :org AND member_user_id = :user",
                params);
        var anonymised = jdbc.update(
                "UPDATE bookmark SET source_author_user_id = :surrogate, version = version + 1, "
                        + "updated_at = :erasedAt WHERE organization_id = :org AND source_author_user_id = :user",
                params);
        return new ErasureCounts(deleted, 0, anonymised, 0);
    }
}
