package dev.axonia.mentoring.application;

import org.springframework.stereotype.Component;

import dev.axonia.mentoring.application.port.out.CurrentUser;
import dev.axonia.mentoring.application.port.out.OrganizationContext;
import dev.axonia.mentoring.application.port.out.PairingRepository;
import dev.axonia.mentoring.domain.Pairing;
import dev.axonia.shared.api.error.ConflictException;
import dev.axonia.shared.api.error.NotFoundException;
import dev.axonia.shared.api.error.OwnSignInRequiredException;
import lombok.RequiredArgsConstructor;

/**
 * The caller as one of a pairing's two people. To anybody else the pairing does not exist, and a delegated session
 * reaches none. A write holds the pairing's row lock, so it is decided one at a time and an end either waits for it or
 * is seen by it; an ended pairing takes no write.
 */
@Component("mentoringPairingParties")
@RequiredArgsConstructor
class PairingParties {

    private final OrganizationContext organizationContext;
    private final CurrentUser currentUser;
    private final PairingRepository pairings;

    record Party(String org, String me, Pairing pairing) {

        long pairingId() {

            return pairing.id();
        }
    }

    Party read(long pairingId) {

        return party(pairingId, false);
    }

    Party write(long pairingId) {

        var party = party(pairingId, true);
        if (party.pairing().endedAt() != null) {
            throw new ConflictException("MENTORING_PAIRING_ENDED", "An ended pairing's journal is read only");
        }
        return party;
    }

    private Party party(long pairingId, boolean forWrite) {

        if (currentUser.delegated()) {
            throw new OwnSignInRequiredException();
        }
        var org = organizationContext.organizationId();
        var me = currentUser.userId();
        var found = forWrite ? pairings.findForWrite(org, pairingId) : pairings.find(org, pairingId);
        var pairing = found.filter(p -> me.equals(p.mentorUserId()) || me.equals(p.menteeUserId()))
                .orElseThrow(() -> new NotFoundException("No such pairing"));
        return new Party(org, me, pairing);
    }
}
