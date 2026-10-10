package dev.axonia.events.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MeetingPlaceTest {

    private static final MeetingPlace SERIES = new MeetingPlace(null, "https://teams.microsoft.com/l/abc",
            "ID 391 2244, passcode 4471");

    @Test
    @DisplayName("blank parts normalise to null so a cleared field reads as absence")
    void blankNormalisesToNull() {

        var place = new MeetingPlace("  ", "", "\t");

        assertThat(place.location()).isNull();
        assertThat(place.joinUrl()).isNull();
        assertThat(place.joiningDetails()).isNull();
        assertThat(place.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("an occurrence with no part of its own inherits the series' place entire")
    void emptyOverrideInherits() {

        assertThat(MeetingPlace.resolve(MeetingPlace.NONE, SERIES)).isEqualTo(SERIES);
        assertThat(MeetingPlace.resolve(null, SERIES)).isEqualTo(SERIES);
    }

    @Test
    @DisplayName("an occurrence naming only a location inherits none of the series' place")
    void anyPartDetachesTheWholeBlock() {

        var own = new MeetingPlace("Room 4B, Gasperich", null, null);

        var resolved = MeetingPlace.resolve(own, SERIES);

        assertThat(resolved.location()).isEqualTo("Room 4B, Gasperich");
        assertThat(resolved.joinUrl()).isNull();
        assertThat(resolved.joiningDetails()).isNull();
    }

    @Test
    @DisplayName("a passcode cannot outlive the link it belonged to")
    void overridingOnlyTheLinkDropsTheOldCredentials() {

        var own = new MeetingPlace(null, "https://meet.google.com/xyz", null);

        assertThat(MeetingPlace.resolve(own, SERIES).joiningDetails()).isNull();
    }

    @Test
    @DisplayName("nothing anywhere resolves to NONE rather than to null")
    void nothingResolvesToNone() {

        assertThat(MeetingPlace.resolve(null, null)).isEqualTo(MeetingPlace.NONE);
        assertThat(MeetingPlace.resolve(MeetingPlace.NONE, MeetingPlace.NONE)).isEqualTo(MeetingPlace.NONE);
    }
}
