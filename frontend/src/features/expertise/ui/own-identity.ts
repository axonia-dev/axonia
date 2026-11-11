import { primaryOrgUnit, type User } from "@/features/identity/domain/user";
import { userHue } from "@/lib/user-hue";

export interface OwnIdentity {
  name: string;
  role: string | null;
  hue: string;
  avatarUrl: string | null;
}

/** How the member's own pages name and draw them, before the profile has loaded too. */
export function ownIdentity(user: User | undefined): OwnIdentity {
  if (!user) {
    return { name: "Me", role: null, hue: userHue("Me"), avatarUrl: null };
  }
  return {
    name: user.displayName,
    role: primaryOrgUnit(user),
    hue: userHue(user.id),
    avatarUrl: user.avatarUrl ?? null,
  };
}
