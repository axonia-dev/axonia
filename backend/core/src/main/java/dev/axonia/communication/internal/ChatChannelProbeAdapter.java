package dev.axonia.communication.internal;

import java.net.URI;

import org.springframework.stereotype.Component;

import dev.axonia.PublicUrlProperties;
import dev.axonia.communication.ChatChannelProbe;
import lombok.RequiredArgsConstructor;

/**
 * The one fixed post a leader can trigger by hand. Its copy never varies with the community's own content - there is no
 * session, no wrap-up, nothing frozen to read - so unlike every other {@link ChatPost} it is built here, once, at the
 * module boundary a leader's click actually crosses.
 *
 * <p>
 * Lets {@link ChatTransport}'s {@code ChatSendException} propagate as itself: it already extends the public
 * {@code ChatSendFailedException} this port promises, so there is nothing to catch or rewrap here.
 */
@Component
@RequiredArgsConstructor
class ChatChannelProbeAdapter implements ChatChannelProbe {

    private final ChatTransport chatTransport;
    private final PublicUrlProperties publicUrlProperties;

    @Override
    public void postTest(String webhookUrl, String communityName) {

        var post = ChatPost.builder().sender(communityName).headline("Test message from axonia")
                .whenLine("This is only a test, not a real update")
                .body("If you can see this in the channel, the connection is working.").actionLabel("Open axonia")
                .actionUrl(publicUrlProperties.publicBaseUrl()).build();
        chatTransport.post(URI.create(webhookUrl), post);
    }
}
