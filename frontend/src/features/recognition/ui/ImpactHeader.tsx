import { useEffect, useState } from "react";

import { MemberAvatar } from "@/components/patterns/MemberAvatar";
import { HeartIcon } from "@/components/ui/HeartIcon";
import { useMe } from "@/features/identity/api/use-me";
import { primaryOrgUnit, type User } from "@/features/identity/domain/user";
import type {
  ContributionType,
  RecognizedContribution,
} from "@/features/recognition/domain/recognition";
import {
  CONTRIBUTION_META,
  CONTRIBUTION_ORDER,
} from "@/features/recognition/ui/contribution-meta";
import { summarizeImpact } from "@/features/recognition/ui/impact-summary";
import { cn } from "@/lib/cn";
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

function MixBar({
  counts,
}: Readonly<{ counts: Record<ContributionType, number> }>) {
  const present = CONTRIBUTION_ORDER.filter((type) => counts[type] > 0);
  const total = present.reduce((sum, type) => sum + counts[type], 0);
  if (total === 0) return null;

  return (
    <div className="pl-[4.5rem]">
      <div className="bg-surface-sunken flex h-2.5 max-w-md overflow-hidden rounded-full motion-safe:animate-[hovercard-in_320ms_ease-out]">
        {present.map((type) => (
          <span
            key={type}
            className={CONTRIBUTION_META[type].dotClassName}
            style={{ width: `${(counts[type] / total) * 100}%` }}
          />
        ))}
      </div>
      <div className="text-muted-foreground mt-2 flex flex-wrap items-center gap-x-4 gap-y-1 text-xs">
        {present.map((type) => {
          const n = counts[type];
          const meta = CONTRIBUTION_META[type];
          return (
            <span key={type} className="inline-flex items-center gap-1.5">
              <span className={`size-2 rounded-full ${meta.dotClassName}`} />
              {n} {n === 1 ? meta.noun : meta.nounPlural}
            </span>
          );
        })}
      </div>
    </div>
  );
}

const SUMMARY_SLOT =
  "grid motion-safe:transition-[grid-template-rows,opacity] motion-safe:duration-320 motion-safe:ease-out";
const SUMMARY_COLLAPSED = "grid-rows-[minmax(0,0fr)] opacity-0";
const SUMMARY_EXPANDED = "grid-rows-[minmax(0,1fr)] opacity-100";

// Mounts collapsed and opens two frames later, so the collapsed state paints before the glide.
function useOpenAfterPaint(): boolean {
  const [open, setOpen] = useState(false);
  useEffect(() => {
    let second = 0;
    const first = requestAnimationFrame(() => {
      second = requestAnimationFrame(() => setOpen(true));
    });
    return () => {
      cancelAnimationFrame(first);
      cancelAnimationFrame(second);
    };
  }, []);
  return open;
}

function ReplaySummaryLine({ text }: Readonly<{ text: string }>) {
  const open = useOpenAfterPaint();
  return (
    <div
      data-state={open ? "expanded" : "collapsed"}
      className={cn(SUMMARY_SLOT, open ? SUMMARY_EXPANDED : SUMMARY_COLLAPSED)}
    >
      <div className="min-h-0 overflow-hidden">
        <p className="text-primary mt-2 inline-flex items-center gap-1.5 text-sm font-medium">
          <HeartIcon filled />
          {text}
        </p>
      </div>
    </div>
  );
}

function ReplaySummary({ text }: Readonly<{ text: string | null }>) {
  return text === null ? null : <ReplaySummaryLine text={text} />;
}

/** `items` is undefined when the contributions could not load: the header then names the member
 *  and claims nothing about what they contributed. */
export function ImpactHeader({
  items,
  scoped = false,
  avatarRef,
  replaySummary = null,
}: Readonly<{
  items: RecognizedContribution[] | undefined;
  scoped?: boolean;
  avatarRef?: ((element: HTMLElement | null) => void) | undefined;
  replaySummary?: string | null;
}>) {
  const me = useMe();
  const summary =
    items === undefined ? undefined : summarizeImpact(items, { scoped });
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
          {...(avatarRef === undefined ? {} : { ref: avatarRef })}
        />
        <div className="min-w-0 flex-1">
          <div className="min-w-0">
            <p className="text-muted-foreground text-[11px] font-semibold tracking-[0.14em] uppercase">
              My impact
            </p>
            <h1 className="font-display text-foreground mt-1 truncate text-lg font-semibold">
              {name}
            </h1>
            {role !== null && (
              <p className="text-muted-foreground text-sm">{role}</p>
            )}
          </div>
          {summary !== undefined && (
            <p className="text-foreground mt-3 text-sm">{summary.standing}</p>
          )}
          <ReplaySummary text={replaySummary} />
        </div>
      </div>
      {summary !== undefined && <MixBar counts={summary.counts} />}
    </header>
  );
}
