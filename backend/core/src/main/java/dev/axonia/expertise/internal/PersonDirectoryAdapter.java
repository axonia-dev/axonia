package dev.axonia.expertise.internal;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import dev.axonia.expertise.Person;
import dev.axonia.expertise.PersonDirectory;
import dev.axonia.identity.UserService;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
class PersonDirectoryAdapter implements PersonDirectory {

    private final UserService userService;

    @Override
    public Map<String, Person> resolve(Collection<String> userIds) {

        var found = userService.resolve(userIds).stream().collect(Collectors.toMap(u -> u.id(), u -> new Person(u.id(),
                u.displayName(), u.avatarUrl(), firstNonBlank(u.team(), u.department(), u.businessUnit()))));
        return userIds.stream().distinct()
                .collect(Collectors.toMap(id -> id, id -> found.getOrDefault(id, Person.unresolved(id))));
    }

    private static String firstNonBlank(String... values) {

        for (var v : values) {
            if (v != null && !v.isBlank()) {
                return v;
            }
        }
        return null;
    }
}
