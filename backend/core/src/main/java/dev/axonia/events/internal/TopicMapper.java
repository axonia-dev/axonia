package dev.axonia.events.internal;

import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class TopicMapper {

    public List<String> tags(TopicEntity e) {

        return TagRefs.sorted(e.getTags(), TopicTagRef::getTag);
    }
}
