package dev.axonia.learning.adapter.out.identity;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import dev.axonia.identity.api.MemberProfiles;
import dev.axonia.learning.application.port.out.PersonDirectory;
import dev.axonia.learning.domain.LearningPerson;
import lombok.RequiredArgsConstructor;

@Component("learningPersonDirectoryAdapter")
@RequiredArgsConstructor
public class PersonDirectoryAdapter implements PersonDirectory {

    /** Matches identity's label for a departed member, so a person the directory cannot name reads the same here. */
    static final String UNRESOLVED_DISPLAY_NAME = "Deleted member";

    private final MemberProfiles userService;

    @Override
    public Map<String, LearningPerson> resolve(Collection<String> userIds) {

        var found = userService.resolve(userIds).stream().collect(
                Collectors.toMap(u -> u.id(), u -> new LearningPerson(u.id(), u.displayName(), u.avatarUrl())));
        return userIds.stream().distinct().collect(Collectors.toMap(Function.identity(),
                id -> found.getOrDefault(id, new LearningPerson(id, UNRESOLVED_DISPLAY_NAME, null))));
    }
}
