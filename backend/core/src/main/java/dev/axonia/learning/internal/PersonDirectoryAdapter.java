package dev.axonia.learning.internal;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import dev.axonia.identity.UserService;
import dev.axonia.learning.LearningPerson;
import dev.axonia.learning.PersonDirectory;
import lombok.RequiredArgsConstructor;

@Component("learningPersonDirectoryAdapter")
@RequiredArgsConstructor
class PersonDirectoryAdapter implements PersonDirectory {

    /** Matches identity's label for a departed member, so a person the directory cannot name reads the same here. */
    static final String UNRESOLVED_DISPLAY_NAME = "Deleted member";

    private final UserService userService;

    @Override
    public Map<String, LearningPerson> resolve(Collection<String> userIds) {

        var found = userService.resolve(userIds).stream().collect(
                Collectors.toMap(u -> u.id(), u -> new LearningPerson(u.id(), u.displayName(), u.avatarUrl())));
        return userIds.stream().distinct().collect(Collectors.toMap(Function.identity(),
                id -> found.getOrDefault(id, new LearningPerson(id, UNRESOLVED_DISPLAY_NAME, null))));
    }
}
