import { Navigate } from "react-router-dom";

import type { SettingsGroup } from "@/components/patterns/settings-nav";
import { SettingsOverview } from "@/components/patterns/SettingsOverview";
import { useMediaQuery } from "@/lib/use-media-query";

interface SettingsLandingProps {
  title: string;
  /** Absolute prefix the sections' relative paths hang off. */
  base: string;
  groups: ReadonlyArray<SettingsGroup>;
}

/**
 * The landing route of a settings surface. Desktop already has the rail, so restating
 * its links as a list is a duplicate: it skips straight to the first real section.
 * Below `md` there is no rail at all, so the overview list stays exactly as it is - the
 * only settings navigation a phone gets - reading the same `groups` the rail does.
 *
 * `useMediaQuery` reads the live viewport on the very first render (this app renders
 * with `createRoot`, not `hydrateRoot`, so there is no server snapshot to mismatch),
 * which is what keeps this branch from ever flashing one layout before the other.
 */
export function SettingsLanding({
  title,
  base,
  groups,
}: Readonly<SettingsLandingProps>) {
  const isDesktop = useMediaQuery("(min-width: 48rem)");
  const first = groups[0]?.items[0];
  if (isDesktop && first !== undefined) {
    return <Navigate to={`${base}/${first.path}`} replace />;
  }
  return <SettingsOverview title={title} base={base} groups={groups} />;
}
