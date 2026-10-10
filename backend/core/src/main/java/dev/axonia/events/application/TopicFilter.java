package dev.axonia.events.application;

import dev.axonia.events.api.TopicKind;
import dev.axonia.events.api.TopicStatus;

public record TopicFilter(TopicKind kind, TopicStatus status) {
}
