package dev.axonia.roundup;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import dev.axonia.CurrentToken;
import dev.axonia.ForbiddenException;
import dev.axonia.communication.RenderedMessage;
import dev.axonia.organization.Organization;
import dev.axonia.organization.OrganizationWriteGate;
import dev.axonia.organization.Organizations;
import dev.axonia.roundup.internal.RoundupAvailability;
import dev.axonia.roundup.internal.RoundupPreviewer;
import lombok.RequiredArgsConstructor;

/**
 * The administrator's two controls over the quarterly roundup: whether it goes out at all, and what it will look like
 * when it does.
 *
 * <p>
 * All three handlers are gated on {@code communication:write} rather than on the generic right to write to the
 * organization - see {@link OrganizationWriteGate#requireCommunicationWrite()} - and all three refuse an Organization
 * the roundup is not available to (see {@link RoundupAvailabilityPolicy}). Such an Organization has no provisioned
 * directory a roundup could be addressed to, which is why {@code RoundupProducer}'s very first guard drops one;
 * refusing here with the same not-applicable {@code 403} {@code OrganizationExportController} uses keeps it from
 * switching on a message that would never be produced, and keeps its preview from being a rendering of nothing.
 *
 * <p>
 * The permission is checked before the organization is read on purpose: a caller who may not manage communication has
 * no business learning whether the roundup is available where their token points from the shape of the refusal.
 */
@RestController
@RequestMapping("/${spring.application.name}/roundup")
@RequiredArgsConstructor
public class RoundupSettingsController {

    private final OrganizationWriteGate gate;
    private final Organizations organizations;
    private final RoundupSettingsService service;
    private final RoundupPreviewer previewer;
    private final RoundupAvailability availability;

    @GetMapping("/settings")
    RoundupSettings settings() {

        currentForCommunicationWrite();
        return service.forCurrentOrganization();
    }

    @PutMapping("/settings")
    RoundupSettings setEnabled(@Valid @RequestBody UpdateRoundupEnabledRequest request) {

        currentForCommunicationWrite();
        return service.setEnabled(request.enabled());
    }

    @GetMapping("/preview")
    RenderedMessage preview() {

        var organization = currentForCommunicationWrite();
        return previewer.preview(organization.id(), organization.name(), CurrentToken.subject());
    }

    private Organization currentForCommunicationWrite() {

        gate.requireCommunicationWrite();
        var organization = organizations.current();
        if (!availability.in(organization.id())) {
            throw new ForbiddenException("The quarterly roundup is not available to this organization.");
        }
        return organization;
    }
}
