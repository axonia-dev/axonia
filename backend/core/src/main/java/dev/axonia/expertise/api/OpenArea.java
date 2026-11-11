package dev.axonia.expertise.api;

import java.time.Instant;
import java.util.List;

/** One area of a community and the people open to questions on it. */
public record OpenArea(String canonicalTag, Instant latestDeclaredAt, List<OpenPerson> people) {
}
