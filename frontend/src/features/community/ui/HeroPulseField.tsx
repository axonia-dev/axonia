import { type ComponentType, lazy, Suspense, useMemo } from "react";

import type { AmbientFieldProps } from "@/components/patterns/ambient-field/AmbientField";
import {
  type Light,
  seedOf,
} from "@/components/patterns/ambient-field/mote-field";
import {
  type Community,
  isArchived,
} from "@/features/community/domain/community";
import { communityHue } from "@/features/community/ui/community-accent";
import { useActivityTrace } from "@/features/health/api/use-activity-trace";
import {
  type Act,
  toActs,
  visibleActs,
} from "@/features/health/domain/activity-trace";
import { useIsDarkTheme } from "@/theme/use-is-dark-theme";

const NO_FIELD: ComponentType<AmbientFieldProps> = () => null;

const AmbientField = lazy(() =>
  import("@/components/patterns/ambient-field/AmbientField").then(
    (module) => ({ default: module.AmbientField }),
    () => ({ default: NO_FIELD }),
  ),
);

const SIZE = { session: "large", addition: "medium", join: "small" } as const;

export function toLight(act: Act, days: number): Light {
  return {
    position: 1 - (act.daysAgo + 0.5) / days,
    freshness: 1 - act.daysAgo / days,
    size: SIZE[act.kind],
    blooms: act.kind === "addition",
  };
}

/** The banner's bottom edge: where the ink may change. */
function bannerBottom(host: HTMLElement): number {
  const banner = host.querySelector<HTMLElement>(
    '[data-ceremony-part="banner"]',
  );
  return banner?.offsetHeight ?? 0;
}

export function HeroPulseField({
  community,
}: Readonly<{ community: Community }>) {
  const { data } = useActivityTrace(community.slug);
  const dark = useIsDarkTheme();
  const photo = community.heroUrl != null;
  const inks = useMemo<AmbientFieldProps["inks"]>(
    () => ({
      upper: photo || dark ? "light" : "accent",
      lower: dark ? "light" : "accent",
    }),
    [photo, dark],
  );
  const lights = useMemo(
    () =>
      data
        ? visibleActs(toActs(data)).map((act) => toLight(act, data.days))
        : [],
    [data],
  );
  if (!data || isArchived(community)) {
    return null;
  }
  return (
    <Suspense fallback={null}>
      <AmbientField
        lights={lights}
        seed={seedOf(community.slug)}
        hue={communityHue(community)}
        inks={inks}
        seam={bannerBottom}
      />
    </Suspense>
  );
}
