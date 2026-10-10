package dev.axonia.identity.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CredentialRecordsTest {

    private static final String DIGEST = "digest-that-must-not-be-printed";

    @Test
    void aScimTokenNeverPrintsItsDigest() {

        var token = ScimToken.builder().id(1L).organizationId("org").tokenHash(DIGEST).label("sync").build();

        assertThat(token.toString()).doesNotContain(DIGEST).contains("label=sync", "organizationId=org");
    }

    @Test
    void aPersonalAccessTokenNeverPrintsItsDigest() {

        var token = PersonalAccessToken.builder().id(1L).organizationId("org").userId("u-1").tokenHash(DIGEST)
                .name("cli").scopes("read").build();

        assertThat(token.toString()).doesNotContain(DIGEST).contains("name=cli", "scopes=read", "userId=u-1");
    }

    @Test
    void anAgentConnectionTokenNeverPrintsItsDigest() {

        var token = AgentConnectionToken.builder().id(1L).organizationId("org").connectionId(2L)
                .kind(AgentConnectionTokenKind.ACCESS).tokenHash(DIGEST).build();

        assertThat(token.toString()).doesNotContain(DIGEST).contains("connectionId=2", "kind=ACCESS");
    }

    @Test
    void anAgentConnectionNeverPrintsItsCodeChallenge() {

        var connection = AgentConnection.builder().id(1L).organizationId("org").clientId("client").codeChallenge(DIGEST)
                .build();

        assertThat(connection.toString()).doesNotContain(DIGEST).contains("clientId=client");
    }
}
