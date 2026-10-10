package dev.axonia.privacy.adapter.in.web;

import java.io.IOException;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import dev.axonia.organization.api.OrganizationRegistry;
import dev.axonia.privacy.api.spi.MemberDataRightsPolicy;
import dev.axonia.privacy.application.AccountErasure;
import dev.axonia.privacy.application.ErasurePreflight;
import dev.axonia.privacy.application.MemberDataRights;
import dev.axonia.shared.api.error.ForbiddenException;
import dev.axonia.shared.api.personaldata.ExportScope;
import dev.axonia.shared.api.security.CurrentToken;
import lombok.RequiredArgsConstructor;

/**
 * Self-serve account erasure and data export for the caller's own identity. Every operation reads its subject from
 * {@link CurrentToken#subject()} - there is deliberately no path variable, so this endpoint can only ever act on its
 * own caller and there is no id an attacker could substitute.
 *
 * <p>
 * That guarantee is not enough on its own: a delegated session also authenticates as the person's subject.
 * {@link PrivacyOwnSignInGuard} refuses every handler this controller declares - current and future - to such a session
 * before its body runs, so nothing here needs its own check, and nothing added later can forget one. The preflight is
 * refused too, not only the erasure: answering it would still hand whoever acts for the person their blocker list -
 * which hub they solely own, which enterprise they belong to - for an action this controller will never actually let
 * that session take, and a member's standing is not support material.
 */
@RestController
@RequestMapping("/${spring.application.name}")
@RequiredArgsConstructor
public class PrivacyController {

    private final AccountErasure accountErasure;
    private final OrganizationRegistry organizations;
    private final MemberDataRights memberDataRights;
    private final ExportDownload download;

    /** Every reason erasure would currently be refused, without erasing anything. */
    @GetMapping("/me/account/erasure-preflight")
    ErasurePreflight preflight() {

        return accountErasure.preflight(CurrentToken.subject());
    }

    @DeleteMapping("/me/account")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void eraseMyAccount() {

        accountErasure.erase(CurrentToken.subject());
    }

    /**
     * The caller's own data, as a ZIP. Inside an Organization it is available only where members exercise their own
     * data rights (see {@link MemberDataRightsPolicy}), matching the erasure surface: an enterprise membership is
     * governed by the tenant's administrator, not the member themselves, so it is refused here the same way
     * {@link AccountErasure} refuses to erase one.
     *
     * <p>
     * A token that names no Organization is not refused. It belongs to somebody who signed in and joined nothing, or
     * who has not chosen an Organization yet, and axonia still holds their account and whatever standing or onboarding
     * they have; {@link ExportScope.Person} is what hands that over. The token's own claim decides, never the dev
     * profile's default-organization fallback: that fallback serves reads scoped to an Organization, and would
     * otherwise hand such a person a member export of an Organization they never joined.
     */
    @GetMapping("/me/data-export")
    void exportMyData(HttpServletResponse response) throws IOException {

        var subject = CurrentToken.subject();
        download.writeTo(scopeFor(subject), subject, response);
    }

    private ExportScope scopeFor(String subject) {

        if (!CurrentToken.hasOrganization()) {
            return new ExportScope.Person(subject);
        }
        var organization = CurrentToken.organization();
        if (exercisedByTheOrganization(organization)) {
            throw new ForbiddenException(
                    "Personal data export is only available where members export their own data themselves.");
        }
        return new ExportScope.OneMember(organization, subject);
    }

    /** An Organization the registry does not hold exercises nothing, so it refuses nothing here. */
    private boolean exercisedByTheOrganization(String organizationId) {

        return organizations.organization(organizationId).isPresent()
                && !memberDataRights.exercisedByMembers(organizationId);
    }
}
