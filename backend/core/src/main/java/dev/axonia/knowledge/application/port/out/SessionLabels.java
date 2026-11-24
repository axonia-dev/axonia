package dev.axonia.knowledge.application.port.out;

import java.util.Collection;
import java.util.Map;

import dev.axonia.knowledge.domain.SessionLabel;

/** The labels of the sessions artifacts point at, in one lookup; a session that no longer resolves is absent. */
public interface SessionLabels {

    Map<Long, SessionLabel> of(Collection<Long> sessionIds);
}
