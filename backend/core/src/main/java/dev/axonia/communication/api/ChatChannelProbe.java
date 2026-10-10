package dev.axonia.communication.api;

/**
 * Sends one trivial, fixed post to a webhook right now, outside the outbox, so a leader can find out whether a channel
 * works without waiting for a session. Declared here and implemented in {@code adapter.out.chat} because
 * {@code community} - which knows the webhook URL and the community's name, and nothing else about how a post is
 * rendered or sent - cannot reach anything but {@code communication.api} to call the transport itself.
 */
public interface ChatChannelProbe {

    /**
     * @throws ChatSendFailedException
     *             if the post did not arrive
     */
    void postTest(String webhookUrl, String communityName);
}
