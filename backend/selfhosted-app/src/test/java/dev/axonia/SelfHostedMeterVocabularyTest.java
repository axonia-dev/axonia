package dev.axonia;

import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import dev.axonia.selfhosted.SelfHostedSpringBootTest;
import io.micrometer.core.instrument.MeterRegistry;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An installation registers, before anything has happened, every series the open vocabulary names, and none the hosted
 * edition alone records.
 */
@SelfHostedSpringBootTest
class SelfHostedMeterVocabularyTest {

    @Autowired
    private MeterRegistry registry;

    @Test
    void registersEverySweepAnInstallationRuns() {

        assertThat(tags("axonia.sweep.runs", "sweep")).containsExactly("agent-connection-retention",
                "communication-drain", "communication-produce", "communication-retention");
    }

    @Test
    void registersEveryIntegrationAnInstallationCalls() {

        assertThat(tags("axonia.integration.calls", "integration")).containsExactly("avatar", "azure-blob", "chat",
                "client-metadata", "entra", "s3", "smtp");
    }

    @Test
    void registersEveryEventAnInstallationCounts() {

        assertThat(tags("axonia.usage.events", "event")).containsExactly("community-created", "email-sent",
                "organization-provisioned", "session-held", "sign-up");
    }

    private Set<String> tags(String meter, String tag) {

        return registry.find(meter).meters().stream().map(registered -> registered.getId().getTag(tag))
                .collect(Collectors.toCollection(TreeSet::new));
    }
}
