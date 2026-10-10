package dev.axonia.events.application;

import java.util.List;

import org.springframework.stereotype.Component;

import dev.axonia.events.domain.ActionItemRecord;
import dev.axonia.events.domain.ActionItemTag;

@Component
public class ActionItemMapper {

    public List<String> tags(ActionItemRecord e) {

        return TagRefs.sorted(e.getTags(), ActionItemTag::getTag);
    }
}
