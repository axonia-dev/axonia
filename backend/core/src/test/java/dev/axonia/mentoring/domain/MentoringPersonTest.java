package dev.axonia.mentoring.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MentoringPersonTest {

    @Test
    void aPersonIsGreetedByTheGivenNameAPatternRead() {

        assertThat(new MentoringPerson("u", "Aurélia Rochat", "Aurélia", null).shortName()).isEqualTo("Aurélia");
    }

    @Test
    void aPersonWithNoGivenNameIsGreetedByTheWholeName() {

        assertThat(new MentoringPerson("u", "Aurélia Rochat", null, null).shortName()).isEqualTo("Aurélia Rochat");
    }
}
