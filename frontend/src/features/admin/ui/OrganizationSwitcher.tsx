import { useState } from "react";

import { Logo } from "@/components/ui/Logo";
import { useActiveOrganizationId } from "@/features/admin/api/use-active-organization-id";
import { useCurrentOrganization } from "@/features/admin/api/use-current-organization";
import { useMyOrganizations } from "@/features/admin/api/use-my-organizations";
import type { MyOrganization } from "@/features/admin/domain/my-organization";
import { cn } from "@/lib/cn";
import { failedToLoad } from "@/lib/failed-to-load";
import { kindHue } from "@/lib/kind-hue";

type Variant = "full" | "compact";

/** The hue an organization's identity wears: its own configured accent color, or a
 *  deterministic fallback hashed from its name. Exported so every surface that renders
 *  an organization's identity - this chip, the tenant settings page's hero and switch
 *  list - derives the same color the same way rather than re-deriving the fallback. */
export function orgHue(org: MyOrganization): string {
  return org.accentColor ?? kindHue(org.name);
}

/**
 * The control's resting face: the Axonia mark tinted by the active org's hue — the brand
 * "wears" the workspace, so one element answers both "what app is this" and "which org am
 * I in". The mark is the only avatar: a single letter badge can't tell two same-initial
 * orgs apart, so identity rides on the org's configured accent color when it has one,
 * falling back to a hash of the full name, plus the name itself.
 *
 * Flat-filled, not a raised button: a resting `surface` fill (so the collapse toggle has
 * a surface to sit on, never floating alone) with no border or shadow. `full` (rail
 * expanded / mobile bar) shows the name; `compact` (collapsed rail) is the lone mark with
 * the name in its native tooltip.
 */
function SwitcherFace({
  org,
  variant,
}: Readonly<{ org: MyOrganization; variant: Variant }>) {
  // The pop marks a change of organization, not the face appearing: played on every mount, it
  // showed an empty name on each load and each move between the app and settings.
  const [firstId] = useState(org.id);
  const switched = org.id !== firstId;
  if (variant === "compact") {
    return (
      <span
        title={org.name}
        className="flex items-center justify-center rounded-md border border-transparent p-1"
      >
        <Logo tint={orgHue(org)} />
      </span>
    );
  }
  return (
    <span className="bg-surface flex w-full items-center gap-3 rounded-md border border-transparent px-2.5 py-2 text-left">
      <Logo tint={orgHue(org)} />
      {/* Keyed on the org so each later switch replays the pop: the name flips in as the
          mark's tile washes to the new hue. */}
      <span
        key={org.id}
        className={cn(
          "font-display text-foreground min-w-0 flex-1 truncate text-sm font-bold",
          switched && "motion-safe:animate-[count-pop_0.3s_ease]",
        )}
      >
        {org.name}
      </span>
    </span>
  );
}

/**
 * The active-organization identity, pinned at the top of the rail (and mirrored into the
 * mobile top bar). It keeps the operating boundary always in view: which Organization a
 * member is scoped into. Membership comes from `GET /me/organizations`; the active org is
 * the signed `org` claim in the access token.
 *
 * Identity only: switching moved to the tenant settings page (`/settings/organization/
 * general`), reached through the account menu, so a plain member without any organization
 * permission can still switch without this chip granting a menu it has no permission to
 * act on. Renders nothing while membership loads or when the caller belongs to no org, and
 * names the current organization when membership could not load.
 */
export function OrganizationSwitcher({
  variant = "full",
}: Readonly<{ variant?: Variant }>) {
  const memberships = useMyOrganizations();
  const { data: current } = useCurrentOrganization();
  const active = activeOrganization(
    memberships,
    useActiveOrganizationId(),
    current,
  );

  if (active === undefined) return null;

  return (
    <div aria-label={`Organization: ${active.name}`}>
      <SwitcherFace org={active} variant={variant} />
    </div>
  );
}

/** The active organization's membership row; when the memberships could not load, the current
 *  organization itself, so the rail still names where the member stands. */
function activeOrganization(
  memberships: ReturnType<typeof useMyOrganizations>,
  activeId: string | undefined,
  current: MyOrganization | undefined,
): MyOrganization | undefined {
  if (failedToLoad(memberships)) return current;
  const organizations = memberships.data ?? [];
  return organizations.find((o) => o.id === activeId) ?? organizations[0];
}
