import { useState } from "react";

import type { PermissionGroup } from "@/features/admin/domain/permission-catalog";

/**
 * Which permission groups the pane draws open, and the one control that shuts them all.
 *
 * What it holds is the shut set, not the open one: the default - everything open - then needs
 * no seeding from the catalog, and a change of organization kind cannot leave a key behind for
 * a group that no longer exists. The set survives opening another role and pinning a
 * comparison, since collapsed each group is one line stating its tally, which is what lets a
 * reader read domain coverage down the rail.
 *
 * A group the current search matched is drawn open whatever the reader shut: a match nobody
 * can see reads as broken search. It closes again when the search does.
 */
export function useGroupCollapse(
  groups: ReadonlyArray<PermissionGroup>,
  matchingCodes: Set<string>,
) {
  const [collapsed, setCollapsed] = useState<ReadonlySet<string>>(
    new Set<string>(),
  );
  const allCollapsed = groups.every((g) => collapsed.has(g.key));
  return {
    allCollapsed,
    isExpanded: (group: PermissionGroup) =>
      !collapsed.has(group.key) ||
      group.permissions.some((p) => matchingCodes.has(p.code)),
    setExpanded: (groupKey: string, expanded: boolean) => {
      const next = new Set(collapsed);
      if (expanded) next.delete(groupKey);
      else next.add(groupKey);
      setCollapsed(next);
    },
    toggleAll: () => {
      setCollapsed(
        allCollapsed ? new Set<string>() : new Set(groups.map((g) => g.key)),
      );
    },
  };
}
