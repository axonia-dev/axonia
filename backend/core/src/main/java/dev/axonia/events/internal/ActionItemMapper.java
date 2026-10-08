package dev.axonia.events.internal;

import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class ActionItemMapper {

    public List<String> tags(ActionItemEntity e) {

        return TagRefs.sorted(e.getTags(), ActionItemTagRef::getTag);
    }
}
