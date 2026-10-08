import { useState } from "react";
import { useNavigate } from "react-router-dom";

import { Avatar } from "@/components/ui/Avatar";
import { MenuButton, MenuItem } from "@/components/ui/Menu";
import { useCommunities } from "@/features/community/api/use-communities";
import type { Community } from "@/features/community/domain/community";
import { communityHue } from "@/features/community/ui/community-accent";
import { cn } from "@/lib/cn";
import { kindHue } from "@/lib/kind-hue";
import { plural } from "@/lib/plural";

const BROWSE_ALL = "__all__";

/**
 * The switcher trigger's resting face, shared by every community-scoped
 * switcher (within-org `CommunitySwitcher` and the hub-flat
 * `CommunityIdentityChip`) so a near-copy trigger never trips jscpd. `collapsed`
 * (rail collapsed) renders the lone avatar tile; expanded adds the name and
 * member count.
 */
export function SwitcherTile({
  name,
  hue,
  memberCount,
  src = null,
  collapsed = false,
}: Readonly<{
  name: string;
  hue: string;
  memberCount: number;
  src?: string | null;
  collapsed?: boolean;
}>) {
  // The pop marks a switch, not the tile appearing: played on every mount, it left an empty slot
  // beside the avatar on each load and each entry into the community shell. Each change of name
  // plays it once, back to the first community too, and it ends with its animation so a rail
  // expanded later does not replay it.
  const [shown, setShown] = useState(name);
  const [switched, setSwitched] = useState(false);
  if (name !== shown) {
    setShown(name);
    setSwitched(true);
  }
  if (collapsed) {
    // Flat-fill ghost, twin of the global rail's org switcher: the filled
    // avatar tile sits flush on the sunken rail, lifting on hover.
    return (
      <span className="hover:bg-surface flex items-center justify-center rounded-md border border-transparent p-1 transition-colors">
        <Avatar name={name} hue={hue} src={src} />
      </span>
    );
  }
  return (
    <span className="bg-surface flex w-full items-center gap-3 rounded-md border border-transparent px-2.5 py-2 text-left transition-colors hover:bg-[color-mix(in_srgb,var(--primary)_8%,var(--surface))]">
      <Avatar name={name} hue={hue} src={src} />
      {/* Keyed on the name so each later switch replays the pop. */}
      <span
        key={name}
        onAnimationEnd={() => setSwitched(false)}
        className={cn(
          "flex min-w-0 flex-col text-left",
          switched && "motion-safe:animate-[count-pop_0.3s_ease]",
        )}
      >
        <span className="font-display text-foreground truncate text-sm font-bold">
          {name}
        </span>
        <span className="text-muted-foreground text-xs">
          {plural(memberCount, "member")}
        </span>
      </span>
    </span>
  );
}

export function CommunitySwitcher({
  current,
  collapsed = false,
}: Readonly<{ current: Community; collapsed?: boolean }>) {
  const navigate = useNavigate();
  const { data } = useCommunities({ page: 0, size: 20 });
  const communities = data?.content ?? [current];

  return (
    <MenuButton
      label="Switch community"
      className={collapsed ? "" : "w-full"}
      onAction={(key) => {
        void navigate(
          key === BROWSE_ALL ? "/communities" : `/communities/${key}`,
        );
      }}
      trigger={
        <SwitcherTile
          name={current.name}
          hue={communityHue(current)}
          src={current.badgeUrl ?? null}
          memberCount={current.memberCount}
          collapsed={collapsed}
        />
      }
    >
      {communities.map((community) => (
        <MenuItem key={community.slug} id={community.slug}>
          <Avatar
            name={community.name}
            hue={community.accentColor ?? kindHue(String(community.kindId))}
            src={community.badgeUrl ?? null}
            className="size-6 text-xs"
          />
          <span className="flex-1 truncate">{community.name}</span>
          {community.slug === current.slug && (
            <span aria-hidden className="text-primary">
              ✓
            </span>
          )}
        </MenuItem>
      ))}
      <MenuItem id={BROWSE_ALL}>Browse all communities</MenuItem>
    </MenuButton>
  );
}
