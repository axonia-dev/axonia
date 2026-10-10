package dev.axonia.privacy.application;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import dev.axonia.privacy.api.spi.ExportLabelPolicy;
import dev.axonia.shared.api.policy.PolicyOpinions;
import lombok.RequiredArgsConstructor;

/**
 * Asks the contributed {@link ExportLabelPolicy}s, and falls back to no label, which names the package after the
 * Organization itself.
 */
@Component
@RequiredArgsConstructor
public class ExportLabels {

    private final List<ExportLabelPolicy> policies;

    public Optional<String> of(String organizationId) {

        return PolicyOpinions.sole(policies, policy -> policy.label(organizationId));
    }
}
