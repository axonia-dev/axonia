package dev.axonia.events.internal;

import dev.axonia.events.TopicKind;
import dev.axonia.events.TopicStatus;

public record TopicFilter(TopicKind kind, TopicStatus status) {
}
