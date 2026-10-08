package dev.axonia;

import java.nio.file.Path;

/** {@link DedicatedPostgresContainerGuardTest} over selfhosted-app's tests. */
class SelfHostedDedicatedPostgresContainerGuardTest extends DedicatedPostgresContainerGuardTest {

    @Override
    protected Path calibrationTarget() {

        return Path.of("dev", "axonia", "selfhosted", "SelfHostedDatabase.java");
    }
}
