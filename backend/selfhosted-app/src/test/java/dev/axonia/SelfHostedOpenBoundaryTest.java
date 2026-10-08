package dev.axonia;

import java.util.HashSet;
import java.util.Set;

/**
 * {@link OpenCoreBoundaryTest} over selfhosted-app, which ships in the public repository too and is built against core
 * alone: what it may name is its own packages and core's, read from core's source tree because that is what the public
 * repository carries.
 */
class SelfHostedOpenBoundaryTest extends OpenCoreBoundaryTest {

    @Override
    protected Set<String> allowedSegments() {

        var allowed = new HashSet<>(super.allowedSegments());
        allowed.addAll(segmentsUnder(RepositoryRoot.file("backend/core/src/main/java/dev/axonia")));
        return allowed;
    }
}
