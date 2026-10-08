package dev.axonia.roundup;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.roundup.internal.RoundupSettingsEntity;
import dev.axonia.roundup.internal.RoundupSettingsRepository;
import lombok.RequiredArgsConstructor;

/**
 * The organization's single roundup control. An absent row reads as off, which is the default a new enterprise
 * organization gets: the roundup reaches every present member of a provisioned directory, including people who joined
 * no community and asked axonia for nothing, so it never starts on its own.
 */
@Service
@RequiredArgsConstructor
public class RoundupSettingsService {

    private final RoundupSettingsRepository roundupSettingsRepository;
    private final OrganizationContext organizationContext;

    /** The current caller's organization setting, for the administrator reading or changing it over HTTP. */
    @Transactional(readOnly = true)
    public RoundupSettings forCurrentOrganization() {

        return settingsOf(organizationContext.organizationId());
    }

    /**
     * Whether the named organization's roundup is on right now.
     *
     * <p>
     * Takes the organization explicitly, unlike {@link #forCurrentOrganization()}, because the delivery gate calls it
     * from inside the communication drain's cross-tenant sweep, where there is no JWT: each organization's unit runs
     * inside {@code OrganizationScope.runAs}, which installs a JWT-less authentication, and {@link OrganizationContext}
     * would throw rather than answer there. It is the same reason {@code events.internal.MessageSubjectPolicyAdapter}
     * calls the org-explicit {@code CommunityAccess} overloads.
     */
    @Transactional(readOnly = true)
    public boolean enabledFor(String organizationId) {

        return settingsOf(organizationId).enabled();
    }

    /**
     * Switches the current caller's organization roundup on or off, creating the row the first time.
     *
     * <p>
     * <b>Authorised by its caller, not here.</b> {@link RoundupSettingsController} runs
     * {@code OrganizationWriteGate.requireCommunicationWrite()} and refuses a hub before reaching this method, the same
     * way {@code OrganizationExportController} gates the export. The check does not live in this class because
     * {@link #enabledFor} is deliberately reached from the communication drain's cross-tenant sweep, where there is no
     * JWT and no caller to authorise, so a class-wide gate would be wrong for one of these three methods. That makes
     * this permission the one place two controls gate at different layers - the organization-wide kill switch checks
     * {@code communication:write} inside {@code OrganizationService}, this one checks it in the controller - so a
     * second caller of this method must carry the gate itself.
     */
    @Transactional
    public RoundupSettings setEnabled(boolean enabled) {

        var organizationId = organizationContext.organizationId();
        var entity = roundupSettingsRepository.findById(organizationId)
                .map(existing -> existing.toBuilder().enabled(enabled).build()).orElseGet(() -> RoundupSettingsEntity
                        .builder().organizationId(organizationId).enabled(enabled).isNew(true).build());
        var saved = roundupSettingsRepository.save(entity);
        return new RoundupSettings(saved.getOrganizationId(), saved.isEnabled());
    }

    private RoundupSettings settingsOf(String organizationId) {

        return roundupSettingsRepository.findById(organizationId)
                .map(entity -> new RoundupSettings(organizationId, entity.isEnabled()))
                .orElseGet(() -> new RoundupSettings(organizationId, false));
    }
}
