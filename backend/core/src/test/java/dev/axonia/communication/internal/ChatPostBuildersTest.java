package dev.axonia.communication.internal;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import dev.axonia.communication.MessageType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ChatPostBuildersTest {

    private static final Map<String, Object> SENDER = Map.of("name", "Backend Guild", "initials", "BG",
            "tintBackground", "#3b1e54", "tintForeground", "#ffffff");

    private final ChatPostBuilders sut = new ChatPostBuilders();

    @Test
    void upcoming_leadsWithTheSessionAndInvitesToTheOpenSlots() {

        var post = sut.build(readBack("session_upcoming"),
                Map.of("sessionTitle", "Event sourcing in anger", "scheduledAt", Instant.parse("2026-09-01T16:00:00Z"),
                        "sessionUrl", "https://axonia.example/s/1", "openSlotCount", 2, "talks",
                        List.of(Map.of("title", "Event sourcing in anger", "presenter", "Ada")), "sender", SENDER));

        assertThat(post.sender()).isEqualTo("Backend Guild");
        assertThat(post.headline()).isEqualTo("Event sourcing in anger");
        assertThat(post.whenLine()).contains("1 September 2026");
        assertThat(post.details()).contains(Map.entry("Talks", "Event sourcing in anger, with Ada"));
        assertThat(post.details()).contains(Map.entry("Open slots", "2"));
        assertThat(post.actionLabel()).isEqualTo("View the session");
        assertThat(post.actionUrl()).isEqualTo("https://axonia.example/s/1");
    }

    @Test
    void wrapUp_carriesTheFollowUpNoteTheRecordingAndTheAttendance() {

        var post = sut.build(readBack("session_wrap_up"),
                Map.of("sessionTitle", "Event sourcing in anger", "scheduledAt", Instant.parse("2026-09-01T16:00:00Z"),
                        "sessionUrl", "https://axonia.example/s/1", "followUpNotes", "We agreed to spike it.",
                        "recordingUrl", "https://rec.example/1", "attendeeCount", 24, "talks",
                        List.of(Map.of("title", "Event sourcing in anger", "presenter", "Ada")), "sender", SENDER));

        assertThat(post.body()).isEqualTo("We agreed to spike it.");
        assertThat(post.details()).contains(Map.entry("Recording", "https://rec.example/1"));
        assertThat(post.details()).contains(Map.entry("Attended", "24"));
    }

    @Test
    void aFactThatIsAbsentProducesNoDetailRatherThanAnEmptyOne() {

        var post = sut.build(readBack("session_wrap_up"),
                Map.of("sessionTitle", "Untitled", "scheduledAt", Instant.parse("2026-09-01T16:00:00Z"), "sessionUrl",
                        "https://axonia.example/s/1", "talks", List.of(), "sender", SENDER));

        assertThat(post.body()).isNull();
        assertThat(post.details()).doesNotContainKey("Recording");
        assertThat(post.details()).doesNotContainKey("Attended");
        assertThat(post.details()).doesNotContainKey("Resources");
    }

    @Test
    void wrapUp_carriesSessionLevelResourcesAsATitledLine() {

        var post = sut.build(readBack("session_wrap_up"),
                Map.of("sessionTitle", "Event sourcing in anger", "scheduledAt", Instant.parse("2026-09-01T16:00:00Z"),
                        "sessionUrl", "https://axonia.example/s/1", "talks", List.of(), "sender", SENDER, "resources",
                        List.of(Map.of("title", "Session notes", "url", "https://axonia.example/notes/1"),
                                Map.of("url", "https://axonia.example/notes/2"))));

        assertThat(post.details()).contains(Map.entry("Resources",
                "Session notes: https://axonia.example/notes/1; https://axonia.example/notes/2"));
    }

    @Test
    void aResourceWithNoUrlIsDroppedAndLeavesNoResourcesRowWhenItWasTheOnlyOne() {

        var post = sut.build(readBack("session_wrap_up"),
                Map.of("sessionTitle", "Event sourcing in anger", "scheduledAt", Instant.parse("2026-09-01T16:00:00Z"),
                        "sessionUrl", "https://axonia.example/s/1", "talks", List.of(), "sender", SENDER, "resources",
                        List.of(Map.of("title", "x"))));

        assertThat(post.details()).doesNotContainKey("Resources");
    }

    @Test
    void aFactsMapWithNoResourcesEntryProducesNoResourcesDetail() {

        var post = sut.build(readBack("session_wrap_up"),
                Map.of("sessionTitle", "Event sourcing in anger", "scheduledAt", Instant.parse("2026-09-01T16:00:00Z"),
                        "sessionUrl", "https://axonia.example/s/1", "talks", List.of(), "sender", SENDER));

        assertThat(post.details()).doesNotContainKey("Resources");
    }

    @Test
    void aSessionWithNoTitleAnywhereFallsBackToTheSameCopyTheMailShellUses() {

        var post = sut.build(readBack("session_upcoming"), Map.of("scheduledAt", Instant.parse("2026-09-01T16:00:00Z"),
                "sessionUrl", "https://axonia.example/s/1", "talks", List.of(), "sender", SENDER));

        assertThat(post.headline()).isEqualTo("This session");
    }

    @Test
    void aFactsMapWithNoTalksEntryProducesNoTalksDetail() {

        var post = sut.build(readBack("session_upcoming"),
                Map.of("sessionTitle", "Event sourcing in anger", "scheduledAt", Instant.parse("2026-09-01T16:00:00Z"),
                        "sessionUrl", "https://axonia.example/s/1", "sender", SENDER));

        assertThat(post.details()).doesNotContainKey("Talks");
    }

    @Test
    void aTalkWhoseTitleIsAbsentIsDroppedFromTheTalksLine() {

        var post = sut.build(readBack("session_upcoming"), Map.of("sessionTitle", "Event sourcing in anger",
                "scheduledAt", Instant.parse("2026-09-01T16:00:00Z"), "sessionUrl", "https://axonia.example/s/1",
                "talks",
                List.of(Map.of("title", "Event sourcing in anger", "presenter", "Ada"), Map.of("presenter", "Grace")),
                "sender", SENDER));

        assertThat(post.details()).contains(Map.entry("Talks", "Event sourcing in anger, with Ada"));
    }

    /**
     * The guard inside the shared when-line read, exercised from this side. A post is built from whatever a stored
     * outbox row happens to carry, and a row with no {@code scheduledAt} must produce a card with no when line at all
     * rather than a dangling "Held " with nothing after it, and certainly rather than a throw on the drain tick.
     */
    @Test
    void aPostWithNoScheduledInstantHasNoWhenLineRatherThanADanglingPrefix() {

        var wrapUp = sut.build(readBack("session_wrap_up"), Map.of("sessionTitle", "Event sourcing in anger",
                "sessionUrl", "https://axonia.example/s/1", "sender", SENDER));
        var upcoming = sut.build(readBack("session_upcoming"), Map.of("sessionTitle", "Event sourcing in anger",
                "sessionUrl", "https://axonia.example/s/1", "sender", SENDER));

        assertThat(wrapUp.whenLine()).isNull();
        assertThat(upcoming.whenLine()).isNull();
        assertThat(wrapUp.headline()).isEqualTo("Event sourcing in anger");
    }

    /**
     * An untimed occurrence of a series with no zone fact on the row. The instant is read on UTC, which is what a
     * series carrying no zone of its own was backfilled to, so the day named is still the day on the schedule.
     */
    @Test
    void anUntimedPostWithNoZoneFactNamesTheDayOnUtc() {

        var post = sut.build(readBack("session_upcoming"),
                Map.of("sessionTitle", "Event sourcing in anger", "scheduledAt", Instant.parse("2026-09-01T00:00:00Z"),
                        "hasTime", false, "sessionUrl", "https://axonia.example/s/1", "sender", SENDER));

        assertThat(post.whenLine()).isEqualTo("Happening 1 September 2026");
    }

    @Test
    void build_rejectsAMessageTypeThatNeverPostsToAChannel() {

        assertThatThrownBy(() -> sut.build(readBack("session_awaiting_close"), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("session_awaiting_close does not post to a channel");
        assertThatThrownBy(() -> sut.build(readBack("organization_roundup"), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("organization_roundup does not post to a channel");
    }

    /** The type as every production caller hands it over: parsed from a stored or signed code, not the constant. */
    private static MessageType readBack(String code) {

        return MessageType.parse(code).orElseThrow();
    }
}
