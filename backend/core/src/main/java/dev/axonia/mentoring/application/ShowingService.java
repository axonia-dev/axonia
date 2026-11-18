package dev.axonia.mentoring.application;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.axonia.mentoring.application.port.out.PairingRepository;
import dev.axonia.mentoring.domain.Role;
import dev.axonia.mentoring.domain.ShowAnswer;
import dev.axonia.shared.api.error.ConflictException;
import lombok.RequiredArgsConstructor;

/** Each of a pairing's two people answers whether their ongoing pairing shows on the community's Mentoring page. */
@Service
@RequiredArgsConstructor
public class ShowingService {

    private final PairingParties parties;
    private final PairingRepository pairings;

    @Transactional
    public void answer(long pairingId, ShowAnswer answer) {

        var party = parties.write(pairingId);
        var pairing = party.pairing();
        if (pairing.mentorUserId() == null || pairing.menteeUserId() == null) {
            throw new ConflictException("MENTORING_SHOWING_SETTLED", "A pairing with a side gone is never shown");
        }
        var side = party.me().equals(pairing.mentorUserId()) ? Role.MENTOR : Role.MENTEE;
        pairings.saveShowing(party.org(), pairingId, pairing.showing().answer(side, answer));
    }
}
