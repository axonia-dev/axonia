package dev.axonia.communication.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.axonia.communication.adapter.out.chat.ChatPostBuilders;
import dev.axonia.communication.api.InAppEntryShape;
import dev.axonia.communication.api.MessageType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reads exactly the keys the senders already write - {@code events}' {@code SessionFacts} and {@code roundup}'s
 * {@code RoundupFacts} - the same way {@link ChatPostBuilders} does, and for the same reason: {@code communication}
 * never depends on either, so the coupling is by inspection and has to be pinned by a test.
 */
class NotificationEntryBuildersTest {

    private final NotificationEntryBuilders builders = new NotificationEntryBuilders(List.of());

    @Test
    void anUpcomingSessionSaysWhenItHappens() {

        var entry = builders.build(readBack("session_upcoming"),
                Map.of("sessionTitle", "Frontend Dojo", "sender", Map.of("name", "Frontend Guild"), "scheduledAt",
                        Instant.parse("2026-09-01T14:00:00Z"), "timeZone", "Europe/Paris", "sessionUrl",
                        "https://axonia.example/s/1"))
                .orElseThrow();

        assertThat(entry.headline()).isEqualTo("Frontend Dojo");
        assertThat(entry.sender()).isEqualTo("Frontend Guild");
        // The session's own clock and named as such. The panel entry used to show a bare hour that was silently UTC,
        // so a member in Paris read 14:00 for a session starting at 16:00 where they sit.
        assertThat(entry.line()).isEqualTo("Happening 1 September 2026 at 16:00 in Paris");
        assertThat(entry.actionUrl()).isEqualTo("https://axonia.example/s/1");
    }

    @Test
    void aWrapUpSaysWhenItWasHeld() {

        var entry = builders.build(readBack("session_wrap_up"),
                Map.of("sessionTitle", "Frontend Dojo", "scheduledAt", Instant.parse("2026-08-01T09:00:00Z"),
                        "timeZone", "Europe/Paris", "sessionUrl", "https://axonia.example/s/1"))
                .orElseThrow();

        assertThat(entry.line()).isEqualTo("Held 1 August 2026 at 11:00 in Paris");
    }

    /**
     * The in-app panel is the third member-facing surface that reads this instant, beside the mail and the chat post,
     * and it had the same defect all three started with. An occurrence nobody chose an hour for stores midnight in its
     * SERIES' zone, so a {@code Europe/Paris} occurrence anchored to Thursday 13 August is {@code 2026-08-12T22:00Z}:
     * read on UTC, the panel row said "Happening 12 August 2026 at 22:00" - an hour nobody chose, and the day before
     * the one the session page names.
     */
    @Test
    void anUntimedOccurrenceNamesItsOwnDayAndNoHour() {

        var entry = builders.build(readBack("session_upcoming"),
                Map.of("sessionTitle", "Frontend Dojo", "scheduledAt", Instant.parse("2026-08-12T22:00:00Z"), "hasTime",
                        false, "timeZone", "Europe/Paris", "sessionUrl", "https://axonia.example/s/1"))
                .orElseThrow();

        assertThat(entry.line()).isEqualTo("Happening 13 August 2026");
    }

    @Test
    void anUntimedWrapUpNamesItsOwnDayToo() {

        var entry = builders.build(readBack("session_wrap_up"),
                Map.of("sessionTitle", "Frontend Dojo", "scheduledAt", Instant.parse("2026-08-12T22:00:00Z"), "hasTime",
                        false, "timeZone", "Europe/Paris", "sessionUrl", "https://axonia.example/s/1"))
                .orElseThrow();

        assertThat(entry.line()).isEqualTo("Held 13 August 2026");
    }

    /**
     * The other arm, and the reason the test above cannot stand alone: a fact map with no {@code hasTime} at all - a
     * roundup's, or an outbox row stored before the key existed - keeps the timed line it has always had. That is what
     * the {@code Boolean.FALSE.equals} comparison buys, and a truth test would have silently dropped every stored row's
     * hour the day this shipped.
     */
    @Test
    void aFactMapWithNoHasTimeKeepsTheHourItHasAlwaysShown() {

        var entry = builders.build(readBack("session_upcoming"),
                Map.of("sessionTitle", "Frontend Dojo", "scheduledAt", Instant.parse("2026-09-01T14:00:00Z"),
                        "timeZone", "Europe/Paris", "sessionUrl", "https://axonia.example/s/1"))
                .orElseThrow();

        assertThat(entry.line()).isEqualTo("Happening 1 September 2026 at 16:00 in Paris");
    }

    @Test
    void aNudgeSaysWhatIsWaiting() {

        var entry = builders
                .build(readBack("session_awaiting_close"),
                        Map.of("sessionTitle", "Frontend Dojo", "sessionUrl", "https://axonia.example/s/1"))
                .orElseThrow();

        assertThat(entry.line()).isEqualTo("Waiting to be closed out");
    }

    @Test
    void aRoundupSaysWhichPeriodItCovers() {

        var entry = builders
                .build(readBack("organization_roundup"), Map.of("sessionTitle", "The second quarter of 2026",
                        "periodLabel", "April to June 2026", "communitiesUrl", "https://axonia.example/communities"))
                .orElseThrow();

        assertThat(entry.headline()).isEqualTo("The second quarter of 2026");
        assertThat(entry.line()).isEqualTo("April to June 2026");
        assertThat(entry.actionUrl()).isEqualTo("https://axonia.example/communities");
    }

    @Test
    void aSessionWithNoTitleFallsBackTheSameWayTheMailShellDoes() {

        var entry = builders.build(readBack("session_wrap_up"), Map.of("sessionUrl", "https://axonia.example/s/1"))
                .orElseThrow();

        assertThat(entry.headline()).isEqualTo("This session");
    }

    @Test
    void aCommunityWithNoNameLeavesTheSenderAbsentRatherThanNamingAStandIn() {

        var entry = builders.build(readBack("session_wrap_up"), Map.of("sessionUrl", "https://axonia.example/s/1"))
                .orElseThrow();

        assertThat(entry.sender()).isNull();
    }

    /**
     * Defensive rather than reachable in production: {@code SessionFacts#sender} and {@code RoundupFacts#sender} each
     * either omit the key outright or populate {@code name} unconditionally, so a sender map with a blank name never
     * actually arrives here today. Kept as its own test anyway, because the class's own contract - "sender is null when
     * the community has no name of its own" - describes exactly this shape, not only the key-absent one
     * {@link #aCommunityWithNoNameLeavesTheSenderAbsentRatherThanNamingAStandIn} covers.
     */
    @Test
    void aSenderMapWithNoNameLeavesTheSenderAbsentRatherThanNamingAStandIn() {

        var entry = builders.build(readBack("session_wrap_up"),
                Map.of("sessionUrl", "https://axonia.example/s/1", "sender", Map.of("name", ""))).orElseThrow();

        assertThat(entry.sender()).isNull();
    }

    @Test
    void aWrapUpWithNoDateHasNoLineRatherThanADanglingPrefix() {

        var entry = builders
                .build(readBack("session_wrap_up"),
                        Map.of("sessionTitle", "Frontend Dojo", "sessionUrl", "https://axonia.example/s/1"))
                .orElseThrow();

        assertThat(entry.line()).isNull();
    }

    @Test
    void anUpcomingSessionWithNoDateHasNoLineRatherThanADanglingPrefix() {

        var entry = builders
                .build(readBack("session_upcoming"),
                        Map.of("sessionTitle", "Frontend Dojo", "sessionUrl", "https://axonia.example/s/1"))
                .orElseThrow();

        assertThat(entry.line()).isNull();
    }

    @Test
    void aBlankSessionUrlIsTreatedAsAbsentRatherThanAsALinkToNowhere() {

        var entry = builders
                .build(readBack("session_awaiting_close"), Map.of("sessionTitle", "Frontend Dojo", "sessionUrl", "   "))
                .orElseThrow();

        assertThat(entry.actionUrl()).isNull();
    }

    @Test
    void buildsAContributedTypeFromTheFactsItsShapeNames() {

        var type = MessageType.of("contributed_type");
        var builders = new NotificationEntryBuilders(List.of(new InAppEntryShape(type, "dueOn", "detailUrl")));

        var actual = builders.build(type, Map.of("sender", Map.of("name", "Axonia"), "sessionTitle", "Headline",
                "dueOn", "in 3 days", "detailUrl", "https://axonia.example/x"));

        assertThat(actual).contains(new NotificationEntryBuilders.PartialEntry("Axonia", "Headline", "in 3 days",
                "https://axonia.example/x"));
    }

    @Test
    void buildsNothingForATypeNoModuleShaped() {

        assertThat(new NotificationEntryBuilders(List.of()).build(MessageType.of("unknown_type"), Map.of())).isEmpty();
    }

    /** The type as every production caller hands it over: parsed from a stored or signed code, not the constant. */
    private static MessageType readBack(String code) {

        return MessageType.parse(code).orElseThrow();
    }
}
