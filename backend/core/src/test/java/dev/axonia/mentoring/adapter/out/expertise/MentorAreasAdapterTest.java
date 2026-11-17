package dev.axonia.mentoring.adapter.out.expertise;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;

import dev.axonia.expertise.api.OpenArea;
import dev.axonia.expertise.api.OpenDoors;
import dev.axonia.expertise.api.OpenPerson;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.when;

@MockitoSettings
class MentorAreasAdapterTest {

    private static final Instant AT = Instant.parse("2026-11-10T09:00:00Z");

    @Mock
    private OpenDoors openDoors;

    @Test
    void eachMemberCarriesTheAreasTheyDeclared() {

        // given
        when(openDoors.declaredIn("platform"))
                .thenReturn(List.of(
                        new OpenArea("kafka", AT,
                                List.of(new OpenPerson("lea", "Lea", null, AT),
                                        new OpenPerson("ben", "Ben", null, AT))),
                        new OpenArea("java", AT, List.of(new OpenPerson("lea", "Lea", null, AT)))));

        // when
        var actual = new MentorAreasAdapter(openDoors).declaredIn("platform");

        // then
        assertThat(actual).containsOnly(entry("lea", List.of("kafka", "java")), entry("ben", List.of("kafka")));
    }
}
