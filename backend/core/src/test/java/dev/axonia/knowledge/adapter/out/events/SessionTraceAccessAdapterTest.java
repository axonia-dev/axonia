package dev.axonia.knowledge.adapter.out.events;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import dev.axonia.CoreJdbcTestSupport;
import dev.axonia.events.api.spi.ArtifactTrace;
import dev.axonia.knowledge.adapter.out.persistence.ArtifactEntityMother;
import dev.axonia.knowledge.adapter.out.persistence.ArtifactRepository;
import dev.axonia.knowledge.api.ArtifactStatus;

import static org.assertj.core.api.Assertions.assertThat;

class SessionTraceAccessAdapterTest extends CoreJdbcTestSupport {

    @Autowired
    private ArtifactRepository artifactRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private SessionTraceAccessAdapter sut;

    @BeforeEach
    void setUp() {

        sut = new SessionTraceAccessAdapter(artifactRepository);
    }

    private void save(String organizationId, Long communityId, Long sessionId, ArtifactStatus status, int loves) {

        var id = artifactRepository.save(ArtifactEntityMother.any().organizationId(organizationId)
                .communityId(communityId).provenanceSessionId(sessionId).status(status.name()).searchDocument("x")
                .tags(Set.of()).build()).getId();
        jdbcTemplate.update("UPDATE artifact SET love_count = ? WHERE id = ?", loves, id);
    }

    @Test
    void countsActiveArtifactsAndSumsTheirLovesPerSession() {

        // given
        save("default", 1L, 100L, ArtifactStatus.ACTIVE, 3);
        save("default", 1L, 100L, ArtifactStatus.ACTIVE, 2);
        save("default", 1L, 100L, ArtifactStatus.ARCHIVED, 9);
        save("default", 1L, 101L, ArtifactStatus.ACTIVE, 0);
        save("default", 2L, 100L, ArtifactStatus.ACTIVE, 4);
        save("other", 1L, 100L, ArtifactStatus.ACTIVE, 7);
        save("default", 1L, null, ArtifactStatus.ACTIVE, 5);

        // when
        var actual = sut.artifactTraces("default", 1L, List.of(100L, 101L, 102L));

        // then
        assertThat(actual).containsExactlyInAnyOrderEntriesOf(
                Map.of(100L, new ArtifactTrace(2, 5), 101L, new ArtifactTrace(1, 0)));
    }

    @Test
    void noSessionsAskNothing() {

        assertThat(sut.artifactTraces("default", 1L, List.of())).isEmpty();
    }
}
