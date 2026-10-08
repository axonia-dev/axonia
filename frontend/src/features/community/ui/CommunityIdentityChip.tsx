import type { Community } from "@/features/community/domain/community";
import { communityHue } from "@/features/community/ui/community-accent";
import { SwitcherTile } from "@/features/community/ui/CommunitySwitcher";

/**
 * The active community's identity, pinned at the rail top of a hub-kind organization's
 * `CommunityShell` and `SettingsShell` (a hub organization holds exactly one community, so this
 * chip and the organization are the same thing). Identity only: it switches nothing.
 */
export function CommunityIdentityChip({
  current,
  collapsed = false,
}: Readonly<{ current: Community; collapsed?: boolean }>) {
  return (
    <div aria-label={`Community: ${current.name}`}>
      <SwitcherTile
        name={current.name}
        hue={communityHue(current)}
        src={current.badgeUrl ?? null}
        memberCount={current.memberCount}
        collapsed={collapsed}
      />
    </div>
  );
}
