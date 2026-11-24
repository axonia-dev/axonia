package dev.axonia.mentoring.api;

import java.util.List;

/** The first of the community's mentors, in the roster's order, and how many there are in all. */
public record OtherMentors(List<MentorFace> faces, long total) {

    public OtherMentors {
        faces = List.copyOf(faces);
    }
}
