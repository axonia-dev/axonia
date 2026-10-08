import type { ReactNode } from "react";

import { MemberAvatar } from "@/components/patterns/MemberAvatar";
import { declaredOnly } from "@/features/expertise/domain/declared-areas";
import type { SelfView, Tier } from "@/features/expertise/domain/expertise";
import { summarize } from "@/features/expertise/ui/expertise-summary";
import { TIER_META, TIER_ORDER } from "@/features/expertise/ui/tier-meta";
import { useMe } from "@/features/identity/api/use-me";
import { primaryOrgUnit, type User } from "@/features/identity/domain/user";
import { plural } from "@/lib/plural";
import { userHue } from "@/lib/user-hue";

interface Identity {
  name: string;
  role: string | null;
  hue: string;
  avatarUrl: string | null;
}

function identityOf(user: User | undefined): Identity {
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

interface DistributionProps {
  counts: Record<Tier, number>;
  totalSignals: number;
}

function DistributionBar({
  counts,
  totalSignals,
}: Readonly<DistributionProps>) {
  const total = TIER_ORDER.reduce((sum, tier) => sum + counts[tier], 0);
  if (total === 0) return null;

  const present = TIER_ORDER.filter((tier) => counts[tier] > 0);

  return (
    <div className="pl-[4.5rem]">
      <div className="bg-surface-sunken flex h-2.5 max-w-md overflow-hidden rounded-full motion-safe:animate-[hovercard-in_320ms_ease-out]">
        {present.map((tier) => (
          <span
            key={tier}
            className={TIER_META[tier].dotClassName}
            style={{ width: `${(counts[tier] / total) * 100}%` }}
          />
        ))}
      </div>
      {/* Each item leads with a 1rem slot pulled into the clipped left edge, so a wrapped line
          never starts on the separator. */}
      <div className="mt-2 overflow-hidden">
        <div className="text-muted-foreground -ml-4 flex flex-wrap items-center gap-y-1 text-xs">
          {present.map((tier) => (
            <span key={tier} className="flex items-center gap-1.5 pl-4">
              <span
                className={`size-2 rounded-full ${TIER_META[tier].dotClassName}`}
              />
              {counts[tier]} {TIER_META[tier].label}
            </span>
          ))}
          {totalSignals > 0 && (
            <span className="flex items-center">
              <span aria-hidden className="w-4 text-center">
                ·
              </span>
              {plural(totalSignals, "signal")}
            </span>
          )}
        </div>
      </div>
    </div>
  );
}

interface Props {
  /** Undefined when the view could not load: the header then names the member and claims
   *  nothing about their areas. */
  view: SelfView | undefined;
  /** Rendered in the top-right: the visibility control. */
  action?: ReactNode;
}

/** Every tier alike, from the view it is handed: the toolbar never narrows it; a community's page hands it that community's view. */
export function ExpertiseHeader({ view, action }: Readonly<Props>) {
  const me = useMe();
  const summary =
    view === undefined
      ? undefined
      : summarize(view.areas, declaredOnly(view).length);
  const { name, role, hue, avatarUrl } = identityOf(me.data);

  return (
    <header className="border-border flex flex-col gap-4 border-b pb-5">
      <div className="flex items-start gap-4">
        <MemberAvatar
          name={name}
          hue={hue}
          subject={me.data?.id ?? null}
          src={avatarUrl}
          shape="circle"
          className="size-14 text-lg"
        />
        <div className="min-w-0 flex-1">
          <div className="flex flex-col items-start gap-3 sm:flex-row sm:justify-between">
            <div className="min-w-0 self-stretch sm:self-auto">
              <p className="text-muted-foreground text-[11px] font-semibold tracking-[0.14em] uppercase">
                My expertise
              </p>
              <h1 className="font-display text-foreground mt-1 truncate text-lg font-semibold">
                {name}
              </h1>
              {role !== null && (
                <p className="text-muted-foreground text-sm">{role}</p>
              )}
            </div>
            {action}
          </div>
          {summary !== undefined && (
            <p className="text-foreground mt-3 text-sm">{summary.standing}</p>
          )}
        </div>
      </div>

      {summary !== undefined && (
        <DistributionBar
          counts={summary.counts}
          totalSignals={summary.totalSignals}
        />
      )}
    </header>
  );
}
