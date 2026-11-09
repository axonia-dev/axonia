package dev.axonia.events.adapter.out.identity;

import java.util.Collection;
import java.util.Set;

import org.springframework.stereotype.Component;

import dev.axonia.events.application.port.out.PresentPeople;
import dev.axonia.identity.api.UserDirectory;
import lombok.RequiredArgsConstructor;

/** The same "still here" rule a named speaker or presenter is resolved by. */
@Component
@RequiredArgsConstructor
public class UserDirectoryPresentPeople implements PresentPeople {

    private final UserDirectory userDirectory;

    @Override
    public Set<String> presentAmong(String organizationId, Collection<String> userIds) {

        return userDirectory.presentOf(organizationId, userIds);
    }
}
