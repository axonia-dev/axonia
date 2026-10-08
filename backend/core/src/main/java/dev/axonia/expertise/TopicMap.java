package dev.axonia.expertise;

import java.util.List;

/**
 * The community's areas and the links between them, projected from the signals that survive the visibility controls,
 * and the named areas a leader grouped them into, each holding only tags the map draws.
 */
public record TopicMap(List<TopicNode> topics, List<TopicLink> links, List<NamedArea> namedAreas) {
}
