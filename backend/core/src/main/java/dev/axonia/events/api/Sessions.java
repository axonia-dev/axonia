package dev.axonia.events.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface Sessions {

    Session get(Long id);

    Page<Session> upcoming(String slug, Pageable pageable);

    Page<Session> past(PastSessionsRequest request);

    Session createOneOff(String slug, CreateOneOffRequest request);
}
