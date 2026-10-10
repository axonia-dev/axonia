package dev.axonia.agent.adapter.out.persistence;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.agent.domain.ToolCall;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@MockitoSettings
class JdbcToolCallAuditTest {

    private static final Instant AT = Instant.parse("2026-07-07T10:00:00Z");

    @Mock
    AgentToolCallRepository repository;

    @Test
    void record_savesEveryFieldOfTheCall() {

        // given
        var sut = new JdbcToolCallAudit(repository);
        var call = ToolCall.builder().organizationId("acme").userId("u-1").tokenId(3L).connectionId(5L)
                .toolName("topic.propose").outcome("OK").occurredAt(AT).build();

        // when
        sut.record(call);

        // then
        var captor = ArgumentCaptor.forClass(AgentToolCallEntity.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue()).satisfies(row -> {
            assertThat(row.getId()).isNull();
            assertThat(row.getOrganizationId()).isEqualTo("acme");
            assertThat(row.getUserId()).isEqualTo("u-1");
            assertThat(row.getTokenId()).isEqualTo(3L);
            assertThat(row.getConnectionId()).isEqualTo(5L);
            assertThat(row.getToolName()).isEqualTo("topic.propose");
            assertThat(row.getOutcome()).isEqualTo("OK");
            assertThat(row.getOccurredAt()).isEqualTo(AT);
        });
    }
}
