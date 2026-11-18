package dev.axonia.mentoring.domain;

import org.junit.jupiter.api.Test;

import dev.axonia.shared.api.error.ConflictException;

import static dev.axonia.mentoring.domain.Role.MENTEE;
import static dev.axonia.mentoring.domain.Role.MENTOR;
import static dev.axonia.mentoring.domain.ShowAnswer.NO;
import static dev.axonia.mentoring.domain.ShowAnswer.UNSAID;
import static dev.axonia.mentoring.domain.ShowAnswer.YES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ShowingTest {

    @Test
    void bothYesShows() {

        // when
        var actual = Showing.ASKING.answer(MENTOR, YES).answer(MENTEE, YES);

        // then
        assertThat(actual).isEqualTo(Showing.SHOWN);
    }

    @Test
    void oneYesWaits() {

        // when
        var actual = Showing.ASKING.answer(MENTEE, YES);

        // then
        assertThat(actual.visibility()).isEqualTo(Visibility.ASKING);
        assertThat(actual.saidYes(MENTEE)).isTrue();
        assertThat(actual.saidYes(MENTOR)).isFalse();
    }

    @Test
    void theMentorsYesWaitsForTheMentee() {

        // when
        var actual = Showing.ASKING.answer(MENTOR, YES);

        // then
        assertThat(actual).isEqualTo(new Showing(Visibility.ASKING, true, false));
        assertThat(actual.saidYes(MENTOR)).isTrue();
    }

    @Test
    void noIsPrivateForGood() {

        // when
        var actual = Showing.ASKING.answer(MENTOR, YES).answer(MENTEE, NO);

        // then
        assertThat(actual.visibility()).isEqualTo(Visibility.PRIVATE);
        assertThat(actual).isEqualTo(Showing.PRIVATE);
    }

    @Test
    void unsaidTakesAYesBack() {

        // when
        var actual = Showing.ASKING.answer(MENTOR, YES).answer(MENTOR, UNSAID);

        // then
        assertThat(actual).isEqualTo(Showing.ASKING);
    }

    @Test
    void unsaidLeavesThePartnersYes() {

        // when
        var actual = Showing.ASKING.answer(MENTOR, YES).answer(MENTEE, UNSAID);

        // then
        assertThat(actual).isEqualTo(new Showing(Visibility.ASKING, true, false));
    }

    @Test
    void shownAndPrivateTakeNoAnswer() {

        // when / then
        assertThatThrownBy(() -> Showing.SHOWN.answer(MENTOR, NO)).isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("MENTORING_SHOWING_SETTLED");
        assertThatThrownBy(() -> Showing.PRIVATE.answer(MENTEE, YES)).isInstanceOf(ConflictException.class)
                .extracting("code").isEqualTo("MENTORING_SHOWING_SETTLED");
    }
}
