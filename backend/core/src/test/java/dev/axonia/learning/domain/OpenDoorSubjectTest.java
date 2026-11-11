package dev.axonia.learning.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OpenDoorSubjectTest {

    @Test
    void writesCommunityAreaAndDeclarer() {

        assertThat(new OpenDoorSubject(7L, "kafka", "lea").ref()).isEqualTo("7:kafka:lea");
    }

    @Test
    void parsesWhatItWrites() {

        assertThat(OpenDoorSubject.parse("7:kafka:lea")).isEqualTo(new OpenDoorSubject(7L, "kafka", "lea"));
    }

    @Test
    void aDeclarerIdMayHoldAColon() {

        var subject = new OpenDoorSubject(7L, "kafka", "f:ldap:lea");

        assertThat(OpenDoorSubject.parse(subject.ref())).isEqualTo(subject);
    }

    @Test
    void everySubjectAboutTheDeclarerEndsWithTheirSuffix() {

        assertThat(new OpenDoorSubject(7L, "kafka", "lea").ref()).endsWith(OpenDoorSubject.aboutSuffix("lea"));
    }
}
