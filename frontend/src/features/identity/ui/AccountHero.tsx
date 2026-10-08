import { Camera } from "lucide-react";
import { Button } from "react-aria-components";

import { MemberAvatar } from "@/components/patterns/MemberAvatar";
import type { User } from "@/features/identity/domain/user";
import { OrgUnitBand } from "@/features/identity/ui/OrgUnitBand";
import { ProviderMark } from "@/features/identity/ui/ProviderMark";
import { pluralWord } from "@/lib/plural";
import { userHue } from "@/lib/user-hue";

interface AccountHeroProps {
  user: User;
  organizationCount: number | undefined;
  expertiseCount: number | undefined;
  onChangePicture: () => void;
}

interface Stat {
  label: string;
  value: string;
}

const DAY_MS = 24 * 60 * 60 * 1000;

/** Rendered with an explicit UTC zone: the suite pins a timezone west of UTC so that a bare
 *  local render of a boundary date shows up as a failure rather than a coin flip. */
function monthYear(iso: string): string {
  return new Date(iso).toLocaleDateString("en-GB", {
    month: "long",
    year: "numeric",
    timeZone: "UTC",
  });
}

/** Deliberately coarse, matching how the product defines last-active: a recency signal,
 *  never presence. */
function lastActiveLabel(iso: string): string {
  const elapsed = Date.now() - new Date(iso).getTime();
  if (elapsed < DAY_MS) return "Today";
  if (elapsed < 7 * DAY_MS) return "This week";
  return monthYear(iso);
}

function countStat(count: number | undefined, noun: string): Stat | undefined {
  if (count === undefined) return undefined;
  return { label: pluralWord(count, noun).toUpperCase(), value: String(count) };
}

function statsOf(
  user: User,
  organizationCount: number | undefined,
  expertiseCount: number | undefined,
): ReadonlyArray<Stat> {
  const candidates: ReadonlyArray<Stat | undefined> = [
    user.memberSince
      ? { label: "MEMBER SINCE", value: monthYear(user.memberSince) }
      : undefined,
    countStat(organizationCount, "Organization"),
    countStat(expertiseCount, "Expertise area"),
    user.lastActive
      ? { label: "LAST ACTIVE", value: lastActiveLabel(user.lastActive) }
      : undefined,
  ];
  return candidates.filter((stat) => stat !== undefined);
}

function Banner() {
  return (
    <div className="bg-surface-sunken h-26 bg-[radial-gradient(60%_100%_at_15%_0%,color-mix(in_srgb,var(--primary)_28%,transparent),transparent_70%),radial-gradient(55%_100%_at_100%_100%,color-mix(in_srgb,var(--secondary)_24%,transparent),transparent_70%)]" />
  );
}

/** Bottom-left, because bottom-right is left to an edition's avatar decoration, which should not
 *  be hidden on the one page that is about its holder. */
function ChangePictureControl({
  onChangePicture,
}: Readonly<{ onChangePicture: () => void }>) {
  return (
    <Button
      aria-label="Change picture"
      onPress={onChangePicture}
      className="bg-surface text-muted-foreground border-border hover:text-foreground hover:border-border-strong absolute bottom-0 left-0 flex size-8 cursor-pointer items-center justify-center rounded-full border shadow-sm transition-colors duration-150"
    >
      <Camera size={15} />
    </Button>
  );
}

function ProviderChip({ label }: Readonly<{ label: string }>) {
  return (
    <span className="border-border text-muted-foreground mt-1 inline-flex items-center gap-1.5 self-start rounded-full border px-2.5 py-1 text-xs font-medium">
      <ProviderMark label={label} className="size-3.5" />
      {label}
    </span>
  );
}

/** Two to a row below `sm`, where a long label would run into its neighbour; the 1px gap over the
 *  border colour draws the dividers in both layouts, and an odd last cell takes the whole row. */
function StatCell({ stat }: Readonly<{ stat: Stat }>) {
  return (
    <div className="bg-surface flex min-w-0 flex-1 flex-col gap-0.5 px-5 py-3 last:odd:col-span-2">
      <span className="text-foreground text-sm font-semibold">
        {stat.value}
      </span>
      <span className="text-muted-foreground text-xs font-medium">
        {stat.label}
      </span>
    </div>
  );
}

function HeroBody({
  user,
  hue,
  onChangePicture,
}: Readonly<{
  user: User;
  hue: string;
  onChangePicture: () => void;
}>) {
  return (
    <div className="-mt-10 flex flex-col gap-4 px-5 pb-4.5">
      <div className="relative w-fit">
        <MemberAvatar
          name={user.displayName}
          hue={hue}
          subject={user.id}
          shape="circle"
          src={user.avatarUrl ?? null}
          className="border-surface size-22 border-4 text-2xl"
        />
        <ChangePictureControl onChangePicture={onChangePicture} />
      </div>
      <div className="flex min-w-0 flex-col gap-1">
        <h1 className="font-display text-foreground truncate text-xl font-bold">
          {user.displayName}
        </h1>
        {user.email && (
          <p className="text-muted-foreground truncate text-sm">{user.email}</p>
        )}
        {user.signInProvider && <ProviderChip label={user.signInProvider} />}
      </div>
      <OrgUnitBand user={user} />
    </div>
  );
}

export function AccountHero({
  user,
  organizationCount,
  expertiseCount,
  onChangePicture,
}: Readonly<AccountHeroProps>) {
  const hue = userHue(user.id);
  const stats = statsOf(user, organizationCount, expertiseCount);

  return (
    <div className="border-border bg-surface overflow-hidden rounded-lg border shadow-sm">
      <Banner />
      <HeroBody user={user} hue={hue} onChangePicture={onChangePicture} />
      {stats.length > 0 && (
        <div className="bg-border border-border grid grid-cols-2 gap-px border-t sm:flex">
          {stats.map((stat) => (
            <StatCell key={stat.label} stat={stat} />
          ))}
        </div>
      )}
    </div>
  );
}
