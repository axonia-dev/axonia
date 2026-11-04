package dev.axonia.selfhosted.adapter.out.identity;

import java.util.Locale;

import org.junit.jupiter.api.Test;

import dev.axonia.selfhosted.configuration.SelfHostedPropertiesMother;

import static org.assertj.core.api.Assertions.assertThat;

class ObjectIdSubjectsTest {

    private static final String OBJECT_ID = "3f2b8c1e-7d4a-4e9b-a6c2-1d5e8f0b9a73";

    @Test
    void anObjectIdIsTheSubjectOfAnInstallationMatchingAccountsOnIt() {

        // given
        var subjects = new ObjectIdSubjects(SelfHostedPropertiesMother.matchingAccountsOn("oid"));

        // when
        var actual = subjects.subjectOf(OBJECT_ID);

        // then
        assertThat(actual).contains(OBJECT_ID);
    }

    @Test
    void anObjectIdInCapitalsIsTheSubjectInTheLowerCaseATokenCarries() {

        // given
        var subjects = new ObjectIdSubjects(SelfHostedPropertiesMother.matchingAccountsOn("oid"));

        // when
        var actual = subjects.subjectOf(OBJECT_ID.toUpperCase(Locale.ROOT));

        // then
        assertThat(actual).contains(OBJECT_ID);
    }

    @Test
    void aMailNicknameIsNoSubject() {

        // given
        var subjects = new ObjectIdSubjects(SelfHostedPropertiesMother.matchingAccountsOn("oid"));

        // when
        var actual = subjects.subjectOf("M0793512");

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void noExternalIdIsNoSubject() {

        // given
        var subjects = new ObjectIdSubjects(SelfHostedPropertiesMother.matchingAccountsOn("oid"));

        // when
        var actual = subjects.subjectOf(null);

        // then
        assertThat(actual).isEmpty();
    }

    @Test
    void anInstallationMatchingAccountsOnAnotherClaimTakesNoDirectoryKeyForASubject() {

        // given
        var subjects = new ObjectIdSubjects(SelfHostedPropertiesMother.matchingAccountsOn("sub"));

        // when
        var actual = subjects.subjectOf(OBJECT_ID);

        // then
        assertThat(actual).isEmpty();
    }
}
