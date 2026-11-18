package dev.axonia.mentoring.api;

import java.time.LocalDate;

/**
 * An ongoing pairing the community sees on its Mentoring page: who mentors whom, and since when. {@code startedOn} is
 * the pairing's first day in the viewer's zone.
 */
public record ShownPairing(DirectoryFace mentor, DirectoryFace mentee, LocalDate startedOn) {

    public record DirectoryFace(String userId, String displayName, String avatarUrl) {
    }
}
