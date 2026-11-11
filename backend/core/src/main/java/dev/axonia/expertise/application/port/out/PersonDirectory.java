package dev.axonia.expertise.application.port.out;

import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

import dev.axonia.expertise.domain.Person;

public interface PersonDirectory {

    /** The people the directory can name among the ids; an id it cannot name is absent. */
    Map<String, Person> found(Collection<String> userIds);

    /** Total: every requested id maps to a person, one the directory cannot name to {@link Person#unresolved}. */
    default Map<String, Person> resolve(Collection<String> userIds) {

        var found = found(userIds);
        return userIds.stream().distinct()
                .collect(Collectors.toMap(id -> id, id -> found.getOrDefault(id, Person.unresolved(id))));
    }
}
