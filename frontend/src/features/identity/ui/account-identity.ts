import type { AuthContextProps } from "@/auth/session";
import type { User } from "@/features/identity/domain/user";

export type OidcProfile = NonNullable<AuthContextProps["user"]>["profile"];

/** The person's real address, unlike `resolveEmail` this never hides it behind
 *  a duplicate display name - the delete-account confirmation needs the actual
 *  value to type, not the display-only fallback chain. Undefined (rather than a
 *  placeholder string) when neither source has it yet, so the dialog can render
 *  that as its own explicit state instead of a confirmation phrase of `""`. */
export function resolveAccountEmail(
  me: User | undefined,
  p: OidcProfile | undefined,
): string | undefined {
  return me?.email ?? p?.email;
}

/** Before `me` resolves, the token's own username reads better than a blank flash -
 *  it is already on hand from the sign-in that just completed, well before the
 *  richer `/me` profile round-trips. The ellipsis only shows if neither source has
 *  a name, which a signed-in member should never hit. */
export function resolveDisplayName(
  me: User | undefined,
  p: OidcProfile | undefined,
): string {
  return me?.displayName ?? p?.preferred_username ?? "…";
}
