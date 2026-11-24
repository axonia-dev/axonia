package dev.axonia.events.domain;

import dev.axonia.events.api.ProposeTopicRequest;
import dev.axonia.shared.api.error.ValidationException;

/**
 * A topic's description is a short plain-text pitch, never rendered as Markdown. The cap keeps a backlog card readable
 * whoever writes it, a member in the form or an agent over MCP.
 */
public final class TopicDescription {

    public static final int MAX_LENGTH = ProposeTopicRequest.DESCRIPTION_MAX_LENGTH;

    private TopicDescription() {
    }

    public static String of(String requested) {

        var description = Descriptions.orEmpty(requested);
        if (description.length() > MAX_LENGTH) {
            throw new ValidationException("TOPIC_DESCRIPTION_TOO_LONG", "A topic description is at most " + MAX_LENGTH
                    + " characters of plain text. Shorten it to a sentence or two and send it again.");
        }
        return description;
    }
}
