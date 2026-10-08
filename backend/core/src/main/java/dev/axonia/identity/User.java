package dev.axonia.identity;

import java.time.Instant;

import lombok.Builder;

/**
 * A user profile as the IdP asserts it; {@code id} is the JWT {@code sub}. Nullable fields may be absent.
 *
 * <p>
 * {@code memberStatus} is always {@link UserDirectory.MemberStatus#PRESENT} for a claims-only or freshly provisioned
 * profile: a valid session belongs to somebody who is, by definition, here right now. Only a profile read back from
 * storage can be {@link UserDirectory.MemberStatus#DEACTIVATED}.
 *
 * <p>
 * {@code memberSince} and {@code lastActive} are null for a claims-only profile: there is no row to read either from.
 * {@code avatarIsOwn} is a primitive: "has an avatar" ({@code avatarUrl != null}) and "has an avatar worth offering to
 * remove" are different questions, and the frontend cannot tell them apart from {@code avatarUrl} alone.
 *
 * <p>
 * {@code signInProvider} is a fact about this sign-in, not about the person: it is derived from the current token and
 * is null whenever no identity provider claim is present, e.g. a delegated session authenticating with an axonia
 * bearer.
 *
 * <p>
 * {@code timeZone} is the effective clock, declared over observed, and is null when the member has neither: unknown,
 * never defaulted. {@code observedTimeZone} is carried beside it so the frontend can tell whether this browser's
 * current reading has drifted from the last one recorded and report only then, rather than on every load.
 * {@code timeZoneIsOwn} is a primitive for the reason {@code avatarIsOwn} is: "has a clock" and "has a clock worth
 * offering to clear" are different questions, and {@code timeZone} alone cannot answer the second.
 */
@Builder(toBuilder = true)
public record User(String id, String displayName, String email, String avatarUrl, String department,
        String businessUnit, String team, UserDirectory.MemberStatus memberStatus, Instant memberSince,
        Instant lastActive, boolean avatarIsOwn, String signInProvider, String timeZone, String observedTimeZone,
        boolean timeZoneIsOwn) {
}
