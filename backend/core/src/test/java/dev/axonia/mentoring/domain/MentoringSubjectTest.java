package dev.axonia.mentoring.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MentoringSubjectTest {

    @Test
    void aProposalRefRoundTrips() {

        // given
        var subject = MentoringSubject.proposal(40L, "user:with-colon");

        // when
        var actual = MentoringSubject.parse(subject.ref());

        // then
        assertThat(subject.ref()).isEqualTo("proposal:40:user:with-colon");
        assertThat(actual).isEqualTo(subject);
    }

    @Test
    void aPairingRefRoundTrips() {

        // given
        var subject = MentoringSubject.pairing(90L, "mia");

        // when
        var actual = MentoringSubject.parse(subject.ref());

        // then
        assertThat(subject.ref()).isEqualTo("pairing:90:mia");
        assertThat(actual).isEqualTo(new MentoringSubject(MentoringSubject.Kind.PAIRING, 90L, null, "mia"));
    }

    @Test
    void aMeetingRefRoundTrips() {

        // given
        var subject = MentoringSubject.meeting(12L, "1794844318000", "noah");

        // when
        var actual = MentoringSubject.parse(subject.ref());

        // then
        assertThat(subject.ref()).isEqualTo("meeting:12@1794844318000:noah");
        assertThat(actual).isEqualTo(new MentoringSubject(MentoringSubject.Kind.MEETING, 12L, "1794844318000", "noah"));
        assertThat(subject.ref()).endsWith(MentoringSubject.aboutSuffix("noah"));
    }

    @Test
    void roundTripsAnOccasion() {

        // given
        var instant = MentoringSubject.pairing(90L, "1794844318000", "user:with-colon");
        var day = MentoringSubject.proposal(40L, "2026-11-19", "mia");

        // when / then
        assertThat(instant.ref()).isEqualTo("pairing:90@1794844318000:user:with-colon");
        assertThat(MentoringSubject.parse(instant.ref())).isEqualTo(instant);
        assertThat(day.ref()).isEqualTo("proposal:40@2026-11-19:mia");
        assertThat(MentoringSubject.parse(day.ref())).isEqualTo(day);
    }

    @Test
    void stillParsesARefWithoutOne() {

        // when
        var actual = MentoringSubject.parse("pairing:90:mia");

        // then
        assertThat(actual).isEqualTo(MentoringSubject.pairing(90L, "mia"));
        assertThat(actual.occasion()).isNull();
    }

    @Test
    void everyRefAboutAMemberEndsWithTheirSuffix() {

        assertThat(MentoringSubject.pairing(1L, "mia").ref()).endsWith(MentoringSubject.aboutSuffix("mia"));
        assertThat(MentoringSubject.pairing(1L, "2026-11-19", "mia").ref())
                .endsWith(MentoringSubject.aboutSuffix("mia"));
        assertThat(MentoringSubject.proposal(2L, "mia").ref()).endsWith(MentoringSubject.aboutSuffix("mia"));
    }

    @Test
    void anUnknownKindIsNoSubject() {

        assertThatThrownBy(() -> MentoringSubject.parse("session:1:mia")).isInstanceOf(IllegalArgumentException.class);
    }
}
