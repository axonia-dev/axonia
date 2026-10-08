package dev.axonia.privacy.internal;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;

import dev.axonia.PolicyOpinions;
import dev.axonia.privacy.ExportLabelPolicy;
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
