package dev.axonia;

import java.util.List;
import java.util.Map;

/**
 * The Self-hosted application's own migration chain: the open chain of {@link MigrationChain#SELF_HOSTED}, then the
 * migrations only this edition runs, for the Self-hosted tests that build a database of their own.
 */
public final class SelfHostedMigrationChain {

    public static final MigrationChain INSTALLATION = new MigrationChain(
            List.of("classpath:db/selfhosted-baseline", "classpath:db/migration", "classpath:db/selfhosted"),
            Map.of("app-password", "axonia_app", "communication-read-password", "communication_read"));

    private SelfHostedMigrationChain() {
    }
}
