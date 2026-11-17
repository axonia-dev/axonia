package dev.axonia.identity.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.identity.application.port.out.NamePatternLock;
import dev.axonia.identity.application.port.out.NamePatternStore;
import dev.axonia.identity.domain.ReadName;
import dev.axonia.identity.domain.UserAccount;
import lombok.RequiredArgsConstructor;

/**
 * Reads a received name through the name patterns its Organization declared. Every writer of a member's name goes
 * through here, so the received name is kept as asserted and the shown and given names always follow the patterns.
 */
@Service
@RequiredArgsConstructor
public class MemberNames {

    private final NamePatternStore patterns;
    private final NamePatternLock lock;

    /**
     * Holds the Organization's patterns shared for the rest of the writer's transaction before reading them, so a save
     * cannot replace them between this read and the write that follows.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public ReadName read(String organizationId, String receivedName) {

        lock.shared(organizationId);
        return patterns.find(organizationId).read(receivedName);
    }

    /** The account carrying {@code receivedName} as asserted, and the shown and given names read out of it. */
    public UserAccount named(UserAccount account, String receivedName) {

        var name = read(account.organizationId(), receivedName);
        return account.toBuilder().receivedName(receivedName).displayName(name.displayName())
                .givenName(name.givenName()).build();
    }
}
