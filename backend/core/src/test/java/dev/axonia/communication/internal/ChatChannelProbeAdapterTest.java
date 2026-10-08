package dev.axonia.communication.internal;

import java.net.URI;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import dev.axonia.PublicUrlProperties;
import dev.axonia.communication.ChatFailureKind;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ChatChannelProbeAdapterTest {

    private static final URI HOOK = URI.create("https://hooks.slack.com/services/x");

    private final ChatTransport chatTransport = mock(ChatTransport.class);
    private final ChatChannelProbeAdapter sut = new ChatChannelProbeAdapter(chatTransport,
            new PublicUrlProperties("https://axonia.example"));

    @Test
    void postTest_sendsAFixedNonBlankPostThroughTheTransport() {

        sut.postTest(HOOK.toString(), "Backend Guild");

        var captor = ArgumentCaptor.forClass(ChatPost.class);
        verify(chatTransport).post(eq(HOOK), captor.capture());
        var post = captor.getValue();
        assertThat(post.sender()).isEqualTo("Backend Guild");
        assertThat(post.headline()).isNotBlank();
        assertThat(post.actionLabel()).isNotBlank();
        assertThat(post.actionUrl()).isEqualTo("https://axonia.example");
    }

    @Test
    void postTest_letsATransportFailurePropagateAsItself() {

        var failure = new ChatSendException(ChatFailureKind.GONE, "404 from hooks.slack.com");
        doThrow(failure).when(chatTransport).post(eq(HOOK), any());

        assertThatThrownBy(() -> sut.postTest(HOOK.toString(), "Backend Guild")).isSameAs(failure);
    }
}
