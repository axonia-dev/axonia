package dev.axonia.mentoring.application.port.out;

import java.util.List;
import java.util.Map;

public interface MenteeAreas {

    /** Member id to the canonical areas they want to learn. Needs the caller's membership. */
    Map<String, List<String>> wantedIn(String slug);
}
