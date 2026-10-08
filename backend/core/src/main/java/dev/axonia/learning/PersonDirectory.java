package dev.axonia.learning;

import java.util.Collection;
import java.util.Map;

public interface PersonDirectory {

    /**
     * Total: every requested id maps to a person, one the directory cannot name to a "Deleted member" with no avatar,
     * so a lookup of a requested id never returns null.
     */
    Map<String, LearningPerson> resolve(Collection<String> userIds);
}
