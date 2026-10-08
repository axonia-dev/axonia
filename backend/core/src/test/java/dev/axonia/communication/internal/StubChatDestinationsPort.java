package dev.axonia.communication.internal;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import dev.axonia.communication.ChatChannelStatus;
import dev.axonia.communication.ChatDestinationsPort;
import dev.axonia.communication.MessageType;

/**
 * Stands in for the real {@code events.internal} adapter this module has no dependency on. Defaults to an empty
 * {@link #channelTypes()}, matching production before any message type is wired up to chat, so every existing
 * {@code CommunicationJdbcTestSupport} subclass keeps its pre-chat behaviour unless it opts in via
 * {@link CommunicationJdbcTestSupport#whenChannelTypesAre}. Reset after every test - see that method - because this is
 * a singleton bean shared across the whole cached {@code @DataJdbcTest} context, not a fresh instance per test.
 *
 * <p>
 * Deliberately not {@code @Component}: this repo's classpath scan of {@code dev.axonia} covers
 * {@code target/test-classes} too, so a stereotype here would register it as a second, competing
 * {@link ChatDestinationsPort} bean in every plain {@code @SpringBootTest} in the suite - not just this package's own
 * {@code @DataJdbcTest} slice - masking the absence of a real production adapter today and colliding with one the day
 * {@code events.internal} adds it. {@link CommunicationJdbcTestSupport}'s
 * {@code @Import(StubChatDestinationsPort.class)} is enough on its own: {@code @Import} registers a plain class as a
 * bean without needing a stereotype, and a slice performs no component scan of its own to duplicate it.
 */
class StubChatDestinationsPort implements ChatDestinationsPort {

    private Set<MessageType> channelTypes = Set.of();

    void useChannelTypes(Set<MessageType> types) {

        channelTypes = types;
    }

    @Override
    public Set<MessageType> channelTypes() {

        return channelTypes;
    }

    @Override
    public List<Long> destinationsFor(String organizationId, String subjectRef) {

        return List.of();
    }

    @Override
    public ChatChannelStatus statusFor(String organizationId, long destinationId) {

        return new ChatChannelStatus.NotConfigured();
    }

    @Override
    public void recordDelivered(String organizationId, long destinationId, Instant at) {

        // unused by any test in this package
    }

    @Override
    public void recordFailure(String organizationId, long destinationId, ChatFailure failure) {

        // unused by any test in this package
    }
}
