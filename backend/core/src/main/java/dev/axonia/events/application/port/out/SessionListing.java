package dev.axonia.events.application.port.out;

import java.util.List;

import dev.axonia.events.api.Session;
import dev.axonia.events.application.SessionPageQuery;

/** A page of sessions read one query per level of the aggregate, rather than one query per parent row. */
public interface SessionListing {

    List<Session> page(SessionPageQuery query);
}
