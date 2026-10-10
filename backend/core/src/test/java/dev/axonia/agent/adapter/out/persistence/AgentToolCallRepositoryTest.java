package dev.axonia.agent.adapter.out.persistence;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import dev.axonia.CoreJdbcTestSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentToolCallRepositoryTest extends CoreJdbcTestSupport {

    private static final Instant AT = Instant.parse("2026-07-07T10:00:00Z");

    @Autowired
    AgentToolCallRepository repository;

    @Test
    void saveAndFindByOrganization() {
        repository.save(AgentToolCallEntity.builder().organizationId("default").userId("u-1").tokenId(3L)
                .toolName("topic.propose").outcome("OK").occurredAt(AT).build());

        assertThat(repository.findByOrganizationId("default")).hasSize(1)
                .allSatisfy(e -> assertThat(e.getToolName()).isEqualTo("topic.propose"));
    }

    @Test
    void aCallMadeThroughAConnectionNamesTheConnectionOnly() {

        // when
        var saved = repository.save(AgentToolCallEntity.builder().organizationId("default").userId("u-1")
                .connectionId(5L).toolName("community.join").outcome("OK").occurredAt(AT).build());

        // then
        assertThat(repository.findById(saved.getId())).get().satisfies(call -> {
            assertThat(call.getConnectionId()).isEqualTo(5L);
            assertThat(call.getTokenId()).isNull();
        });
    }

    @ParameterizedTest
    @CsvSource(value = {"3, 5", "null, null"}, nullValues = "null")
    void aCallNamesExactlyOneCredential(Long tokenId, Long connectionId) {

        var call = AgentToolCallEntity.builder().organizationId("default").userId("u-1").tokenId(tokenId)
                .connectionId(connectionId).toolName("community.join").outcome("OK").occurredAt(AT).build();

        assertThatThrownBy(() -> repository.save(call)).isInstanceOf(DataIntegrityViolationException.class);
    }
}
