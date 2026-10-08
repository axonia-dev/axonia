package dev.axonia.roundup.internal;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import dev.axonia.communication.MessageSubjectPolicy;
import dev.axonia.communication.MessageType;
import dev.axonia.identity.UserDirectory;
import dev.axonia.roundup.RoundupSettingsService;
import lombok.RequiredArgsConstructor;

/**
 * The roundup's answers to the delivery gate's second and fourth checks, which is all the gate ever needs from a
 * sender. Its shape is unchanged; only what the two questions mean here differs.
 *
 * <p>
 * Layer two asks the organization rather than a community: a roundup is about every community at once, so no single
 * community's setting could own it. Layer four asks the directory rather than a roster: recipients were never selected
 * by membership of anything, so the only way to stop being a recipient is to stop being in the organization. Both are
 * resolved at drain rather than at fan-out, and that matters more here than for a session - an administrator switching
 * the roundup off part way through a fan-out is stopping the rest of the company from receiving it.
 */
@Component
@RequiredArgsConstructor
class RoundupSubjectPolicy implements MessageSubjectPolicy {

    private final RoundupSettingsService settings;
    private final UserDirectory directory;

    @Override
    public Set<MessageType> handles() {

        return Set.of(MessageType.ORGANIZATION_ROUNDUP);
    }

    @Override
    public boolean isEnabledFor(String organizationId, MessageType type, String subjectRef) {

        return settings.enabledFor(organizationId);
    }

    @Override
    public boolean isStillAMember(String organizationId, String subjectRef, String userId) {

        return directory.presentOf(organizationId, List.of(userId)).contains(userId);
    }
}
