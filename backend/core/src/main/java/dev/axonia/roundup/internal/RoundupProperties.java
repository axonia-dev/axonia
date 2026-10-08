package dev.axonia.roundup.internal;

import java.time.Duration;

import jakarta.validation.constraints.Min;

import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * The three numbers the roundup cannot derive from anything it reads.
 *
 * <p>
 * {@code settleLag} is how long after a quarter closes the producer waits before deciding what happened in it. A
 * session held on the last day of the quarter is routinely written up a day or two later, and the write-up carries the
 * presenters the roundup names, so producing the instant the quarter ends reports it as it looked before its own last
 * week had been closed out. It bounds nothing and defends against no runaway - it only trades latency, once a quarter,
 * for a period that has stopped changing.
 *
 * <p>
 * It is bounded at both ends because both ends fail silently rather than loudly. A negative lag produces the quarter
 * before it has closed, reporting a period that is still being written into. A lag anywhere near a quarter's own length
 * makes the producer permanently silent: the most recently closed quarter's end is never more than about 92 days behind
 * now, and the shortest quarter is 90 days, so a lag past that expires only after the quarter it refers to has already
 * been superseded - every tick returns at the settle guard, forever, with no error and nothing in a log. The ceiling is
 * a month rather than the 90-day cliff itself, with margin: past that, this is no longer letting a quarter settle, it
 * is postponing the roundup into the middle of the next one. Both bounds fail the deployment at boot instead.
 *
 * <p>
 * {@code communityCap} is how many communities the roundup names individually before it says how many more were active.
 * A fifteen-guild tenant's roundup has to stay readable, and a list nobody finishes reading is a list that names
 * nobody.
 *
 * <p>
 * {@code sessionsPerCommunityCap} is how many of a community's sessions the card lists before it says how many more it
 * held. It is per community rather than per letter for the reason the community cap exists at all: a tenant with one
 * very busy guild and four quiet ones should not have the quiet four pushed out by the busy one.
 *
 * <p>
 * {@code topicCap} bounds the tag cloud. A cloud is a picture rather than a list, and past a dozen words it stops being
 * one - the long tail is all rendered at the smallest size, where it adds length and no information.
 */
@Validated
@ConfigurationProperties(prefix = "axonia.roundup")
record RoundupProperties(@DefaultValue("2d") @DurationMin(days = 0) @DurationMax(days = 30) Duration settleLag,
        @DefaultValue("8") @Min(1) int communityCap, @DefaultValue("4") @Min(1) int sessionsPerCommunityCap,
        @DefaultValue("12") @Min(1) int topicCap) {
}
