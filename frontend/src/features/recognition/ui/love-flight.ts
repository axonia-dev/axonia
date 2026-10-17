import type { Point } from "@/features/recognition/domain/comet-path";
import type { ContributionKey } from "@/features/recognition/domain/recognition";
import { centreOf, flyComet } from "@/features/recognition/ui/comet-trail";
import {
  type CreditTarget,
  creditTargetsOf,
} from "@/features/recognition/ui/credit-targets";
import { landPulse } from "@/features/recognition/ui/land-pulse";

const PRESENTER_STAGGER_MS = 110;

function inViewport(element: HTMLElement): boolean {
  const r = element.getBoundingClientRect();
  return (
    r.width > 0 &&
    r.bottom > 0 &&
    r.right > 0 &&
    r.top < window.innerHeight &&
    r.left < window.innerWidth
  );
}

function nearestPerUser(
  targets: CreditTarget[],
  origin: Point,
): CreditTarget[] {
  const best = new Map<string, { target: CreditTarget; distance: number }>();
  for (const target of targets) {
    const c = centreOf(target.element);
    const distance = Math.hypot(c.x - origin.x, c.y - origin.y);
    const current = best.get(target.userId);
    if (!current || distance < current.distance) {
      best.set(target.userId, { target, distance });
    }
  }
  return [...best.values()].map((b) => b.target);
}

/** Flies one comet per visible credited contributor other than the viewer; false when there is none. */
export function launchLoveFlight({
  contribution,
  viewerId,
  origin,
}: Readonly<{
  contribution: ContributionKey;
  viewerId: string | null;
  origin: HTMLElement;
}>): boolean {
  const from = centreOf(origin);
  const targets = nearestPerUser(
    creditTargetsOf(contribution).filter(
      (t) => t.userId !== viewerId && inViewport(t.element),
    ),
    from,
  );
  targets.forEach((target, index) => {
    void flyComet({
      from,
      to: centreOf(target.element),
      delay: index * PRESENTER_STAGGER_MS,
    }).then(() => landPulse(target.element));
  });
  return targets.length > 0;
}
