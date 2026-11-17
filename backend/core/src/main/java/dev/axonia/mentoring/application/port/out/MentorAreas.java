package dev.axonia.mentoring.application.port.out;

import java.util.List;
import java.util.Map;

public interface MentorAreas {

    /** Member id to the canonical areas they declared, a paused member included. Needs the caller's membership. */
    Map<String, List<String>> declaredIn(String slug);
}
