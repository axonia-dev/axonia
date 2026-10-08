import { MemberAvatar } from "@/components/patterns/MemberAvatar";
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

/** `items` is undefined when the contributions could not load: the header then names the member
 *  and claims nothing about what they contributed. */
export function ImpactHeader({
  items,
  scoped = false,
}: Readonly<{
  items: RecognizedContribution[] | undefined;
  scoped?: boolean;
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
        </div>
      </div>
      {summary !== undefined && <MixBar counts={summary.counts} />}
    </header>
  );
}
