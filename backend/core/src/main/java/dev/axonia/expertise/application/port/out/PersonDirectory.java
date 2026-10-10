package dev.axonia.expertise.application.port.out;

import java.util.Collection;
import java.util.Map;

import dev.axonia.expertise.domain.Person;

public interface PersonDirectory {

    /** Total: every requested id maps to a person, one the directory cannot name to {@link Person#unresolved}. */
    Map<String, Person> resolve(Collection<String> userIds);
}
