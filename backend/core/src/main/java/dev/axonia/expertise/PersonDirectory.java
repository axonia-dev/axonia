package dev.axonia.expertise;

import java.util.Collection;
import java.util.Map;

public interface PersonDirectory {

    /** Total: every requested id maps to a person, one the directory cannot name to {@link Person#unresolved}. */
    Map<String, Person> resolve(Collection<String> userIds);
}
