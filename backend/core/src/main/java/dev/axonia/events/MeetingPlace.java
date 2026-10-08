package dev.axonia.events;

import jakarta.validation.constraints.Size;

/**
 * Where a session meets, as one value rather than three fields.
 *
 * <p>
 * The three parts travel together because they describe one place, and because a credential that outlives the room it
 * belongs to is worse than no credential at all. {@link #resolve} therefore inherits all of a series' standing place or
 * none of it: an occurrence that names any part of its own is stating where it meets, and the parts it left blank are
 * blank rather than inherited. A series holding a Teams link and its passcode, and an occurrence overriding only the
 * link, would otherwise resolve to the new link beside the old passcode.
 *
 * <p>
 * Only {@code joinUrl} is validated as an address. It is handed on verbatim to an {@code href} and to an ICS
 * {@code CONFERENCE} URI, so a {@code javascript:} value there is stored cross-site scripting: see
 * {@link ValidMeetingUrl}. {@code location} and {@code joiningDetails} are free text that is never rendered as a link
 * and never carries a scheme anywhere, so no format is asserted about them beyond a length.
 *
 * <p>
 * Blank normalises to null in the canonical constructor, so clearing a field in a form returns the occurrence to
 * inheritance rather than storing an empty string that would read as a place of its own.
 */
public record MeetingPlace(@Size(max = 512) String location, @Size(max = 2048) @ValidMeetingUrl String joinUrl,
        @Size(max = 2048) String joiningDetails) {

    /** No place at all. Returned rather than null, so a caller never has to guard the resolution's answer. */
    public static final MeetingPlace NONE = new MeetingPlace(null, null, null);

    public MeetingPlace {
        location = blankToNull(location);
        joinUrl = blankToNull(joinUrl);
        joiningDetails = blankToNull(joiningDetails);
    }

    public boolean isEmpty() {

        return location == null && joinUrl == null && joiningDetails == null;
    }

    /**
     * The place a session actually meets in: its own when it names any part of one, otherwise its series' standing one.
     */
    public static MeetingPlace resolve(MeetingPlace own, MeetingPlace standing) {

        var self = own == null ? NONE : own;
        if (!self.isEmpty()) {
            return self;
        }
        return standing == null ? NONE : standing;
    }

    private static String blankToNull(String value) {

        return value == null || value.isBlank() ? null : value;
    }
}
