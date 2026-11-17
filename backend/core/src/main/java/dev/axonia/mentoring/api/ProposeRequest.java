package dev.axonia.mentoring.api;

import java.util.List;

/** A proposal to {@code toUserId}; {@code asRole} is the proposer's own side, MENTOR or MENTEE. */
public record ProposeRequest(String toUserId, String asRole, List<String> topics, String message) {
}
