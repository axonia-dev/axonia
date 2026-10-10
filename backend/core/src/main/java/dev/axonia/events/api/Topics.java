package dev.axonia.events.api;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface Topics {

    Topic propose(String slug, ProposeTopicRequest request);

    Page<Topic> list(String slug, TopicQuery query, Pageable pageable);
}
