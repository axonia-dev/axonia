package dev.axonia.mentoring.domain;

import org.junit.jupiter.api.Test;

import static dev.axonia.mentoring.domain.ListingSwitch.DEFAULT;
import static dev.axonia.mentoring.domain.ListingSwitch.OFF;
import static dev.axonia.mentoring.domain.ListingSwitch.ON;
import static dev.axonia.mentoring.domain.MentorRoster.Status.MENTORED_HERE;
import static dev.axonia.mentoring.domain.MentorRoster.Status.MENTORING_NOW;
import static org.assertj.core.api.Assertions.assertThat;

class MentorRosterTest {

    private static final MentorHistory ONGOING_SHOWN = new MentorHistory("mia", true, false, false);
    private static final MentorHistory ENDED_SHOWN = new MentorHistory("mia", false, true, false);
    private static final MentorHistory ENDED_UNSHOWN = new MentorHistory("mia", false, false, true);

    @Test
    void ongoingShownIsMentoringNow() {

        assertThat(MentorRoster.status(ONGOING_SHOWN, DEFAULT)).contains(MENTORING_NOW);
        assertThat(MentorRoster.status(ONGOING_SHOWN, ON)).contains(MENTORING_NOW);
    }

    @Test
    void endedShownIsMentoredHere() {

        assertThat(MentorRoster.status(ENDED_SHOWN, DEFAULT)).contains(MENTORED_HERE);
        assertThat(MentorRoster.status(ENDED_SHOWN, ON)).contains(MENTORED_HERE);
    }

    @Test
    void offHidesEverything() {

        assertThat(MentorRoster.status(ONGOING_SHOWN, OFF)).isEmpty();
        assertThat(MentorRoster.status(ENDED_SHOWN, OFF)).isEmpty();
        assertThat(MentorRoster.status(ENDED_UNSHOWN, OFF)).isEmpty();
        assertThat(MentorRoster.status(new MentorHistory("mia", true, true, true), OFF)).isEmpty();
    }

    @Test
    void endedUnshownNeedsOn() {

        assertThat(MentorRoster.status(ENDED_UNSHOWN, DEFAULT)).isEmpty();
        assertThat(MentorRoster.status(ENDED_UNSHOWN, ON)).contains(MENTORED_HERE);
    }

    @Test
    void mentoringNowWins() {

        assertThat(MentorRoster.status(new MentorHistory("mia", true, true, false), DEFAULT)).contains(MENTORING_NOW);
        assertThat(MentorRoster.status(new MentorHistory("mia", true, false, true), ON)).contains(MENTORING_NOW);
    }

    @Test
    void aMentorWithNothingCountingIsNotListed() {

        assertThat(MentorRoster.status(new MentorHistory("mia", false, false, false), ON)).isEmpty();
    }

    @Test
    void needsAskOnlyForUnshownAlone() {

        assertThat(MentorRoster.needsAsk(ENDED_UNSHOWN, DEFAULT)).isTrue();
        assertThat(MentorRoster.needsAsk(new MentorHistory("mia", false, true, true), DEFAULT)).isFalse();
        assertThat(MentorRoster.needsAsk(new MentorHistory("mia", true, false, true), DEFAULT)).isFalse();
        assertThat(MentorRoster.needsAsk(ENDED_UNSHOWN, ON)).isFalse();
        assertThat(MentorRoster.needsAsk(ENDED_UNSHOWN, OFF)).isFalse();
        assertThat(MentorRoster.needsAsk(ENDED_SHOWN, DEFAULT)).isFalse();
    }
}
