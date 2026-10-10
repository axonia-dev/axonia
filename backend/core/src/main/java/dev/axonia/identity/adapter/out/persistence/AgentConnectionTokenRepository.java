package dev.axonia.identity.adapter.out.persistence;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jdbc.repository.query.Modifying;
import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.data.repository.query.Param;

public interface AgentConnectionTokenRepository extends CrudRepository<AgentConnectionTokenEntity, Long> {

    Optional<AgentConnectionTokenEntity> findByTokenHash(String tokenHash);

    /** Marks a code or refresh token used. Answers 0 when it already was, which is how single use is enforced. */
    @Modifying
    @Query("UPDATE agent_connection_token SET consumed_at = :now, updated_at = :now, version = version + 1 "
            + "WHERE id = :id AND consumed_at IS NULL")
    int consume(@Param("id") Long id, @Param("now") Instant now);

    @Modifying
    @Query("DELETE FROM agent_connection_token WHERE connection_id = :connectionId AND kind = 'ACCESS'")
    void deleteAccessTokens(@Param("connectionId") Long connectionId);

    @Modifying
    @Query("DELETE FROM agent_connection_token WHERE organization_id = :org AND expires_at < :now")
    int deleteExpired(@Param("org") String org, @Param("now") Instant now);
}
