package dev.axonia.events.application;

import java.util.List;

import org.springframework.stereotype.Component;

import dev.axonia.events.domain.TopicRecord;
import dev.axonia.events.domain.TopicTag;

@Component
public class TopicMapper {

    public List<String> tags(TopicRecord e) {

        return TagRefs.sorted(e.getTags(), TopicTag::getTag);
    }
}
