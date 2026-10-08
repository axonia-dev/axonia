package dev.axonia;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

class SubjectAuthenticationTest {

    @Test
    void namesTheSubjectTheWorkRunsAs() {
        var authentication = new SubjectAuthentication("subject-alice");

        assertThat(authentication.subject()).isEqualTo("subject-alice");
    }

    @Test
    void isAuthenticatedButGrantsNoAuthorities() {
        var authentication = new SubjectAuthentication("subject-alice");

        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getAuthorities()).isEmpty();
    }

    @Test
    void carriesNoCredentialsAndASystemPrincipal() {
        var authentication = new SubjectAuthentication("subject-alice");

        assertThat(authentication.getCredentials()).isNull();
        assertThat(authentication.getPrincipal()).isEqualTo("subject");
    }
}
