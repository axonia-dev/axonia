package dev.axonia.identity.application;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import dev.axonia.identity.api.OrganizationMembershipView;
import dev.axonia.identity.api.SwitchableOrganizations;
import dev.axonia.identity.api.spi.OrganizationCardLookSource;
import dev.axonia.identity.api.spi.OrganizationCardLookSource.CardLook;
import dev.axonia.identity.api.spi.OrganizationMembershipSource;
import dev.axonia.identity.api.spi.OrganizationMembershipSource.OrganizationMembership;
import dev.axonia.shared.api.security.CurrentToken;
import lombok.RequiredArgsConstructor;

/**
 * Lists the Organizations the caller's identity can switch into.
 *
 * <p>
 * This authorizes on the caller's {@code sub} <strong>only</strong>: it deliberately never reads the active {@code org}
 * claim, so an Org-scoped token (or a token carrying no {@code org} claim at all) can still enumerate every peer
 * Organization it belongs to. Rows are decorated through {@link OrganizationCardLookSource}, whose two reads are gated
 * differently and so are looked up independently: {@code lookFor} (badge, accent colour) is RLS-scoped to whichever
 * organization is ambiently active and never filters the membership list — an invisible row's look fields simply stay
 * {@code null} and the UI falls back to initials — while {@code kindFor} resolves for every membership regardless of
 * which one is active, so kind stays {@code null} only when the registry row genuinely does not exist.
 */
@Service
@RequiredArgsConstructor
public class MyOrganizationsService implements SwitchableOrganizations {

    private static final Comparator<OrganizationMembershipView> BY_NAME_THEN_ID = Comparator
            .comparing(OrganizationMembershipView::name).thenComparing(OrganizationMembershipView::id);

    private final OrganizationMembershipSource source;
    private final OrganizationCardLookSource cardLookSource;

    /** The caller's switchable Organizations, sorted by name then id. */
    public List<OrganizationMembershipView> myOrganizations() {
        return organizationsOf(CurrentToken.subject());
    }

    /**
     * The same read for a named identity rather than the caller's own, for background work that has no token at all: a
     * scheduled tick and a provider webhook both need to know what a person belongs to while nobody is signed in. It
     * discloses no more than {@link #myOrganizations()} already does, because that method's only authorization was the
     * subject it is now handed.
     */
    @Override
    public List<OrganizationMembershipView> organizationsOf(String subject) {
        var memberships = source.membershipsFor(subject);
        var ids = memberships.stream().map(m -> m.id()).toList();
        var looks = cardLookSource.lookFor(ids);
        var kinds = cardLookSource.kindFor(ids);
        return memberships.stream().map(m -> toView(m, looks.get(m.id()), kinds.get(m.id()))).sorted(BY_NAME_THEN_ID)
                .toList();
    }

    private static OrganizationMembershipView toView(OrganizationMembership m, CardLook look, String kind) {
        return new OrganizationMembershipView(m.id(), m.displayName(), look == null ? null : look.badgeUrl(),
                look == null ? null : look.accentColor(), kind);
    }
}
