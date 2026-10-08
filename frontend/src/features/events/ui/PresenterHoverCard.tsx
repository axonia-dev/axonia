import { MemberAvatar } from "@/components/patterns/MemberAvatar";
import type { User } from "@/features/identity/domain/user";
import { MemberLabel } from "@/features/identity/ui/MemberLabel";
import { cn } from "@/lib/cn";
import { userHue } from "@/lib/user-hue";

interface Props {
  name: string;
  user?: User | undefined;
  /** When provided, the avatar becomes a button that opens a management popover
   * (PresenterStack owns the popover itself); omitted, the avatar stays inert
   * and only ever reveals the hover card. */
  onPress?: () => void;
  /** Withholds the identity card entirely: while this avatar's management popover
   * is open, the still-hovered/focused avatar must not paint the card through it. */
  suppressCard?: boolean;
  /** Which avatar edge the card hangs from; "end" keeps the card on-page when the
   * avatar sits at a container's right edge. */
  align?: "start" | "end";
}

/** The border is what separates one face from the next in an overlapping presenter stack; every
 *  other trait of the avatar - monogram, hue, photo fallback, ring - belongs to `MemberAvatar`. */
const avatarClass = "border-surface-sunken size-7 border-2 text-xs";

/** Keyed on the profile so it matches every other surface drawing the same person, and on the
 *  name only while the profile is still resolving - the same placeholder stance the displayed
 *  name already takes. */
function hueOf(name: string, user: User | undefined): string {
  return userHue(user?.id ?? name);
}

function Row({
  label,
  value,
}: Readonly<{ label: string; value?: string | null | undefined }>) {
  if (!value) {
    return null;
  }
  return (
    <span className="flex items-center justify-between gap-3">
      <span className="text-muted-foreground text-[10px] font-bold tracking-wide uppercase">
        {label}
      </span>
      <span className="text-foreground text-xs">{value}</span>
    </span>
  );
}

function OrgRows({ user }: Readonly<{ user: User }>) {
  const hasOrg = Boolean(user.department ?? user.team ?? user.businessUnit);
  if (!hasOrg) {
    return null;
  }
  return (
    <span className="flex flex-col gap-2 p-3.5">
      <Row label="Department" value={user.department} />
      <Row label="Team" value={user.team} />
      <Row label="Business unit" value={user.businessUnit} />
    </span>
  );
}

function HoverCard({ name, user, align }: Readonly<Props>) {
  return (
    <span
      role="tooltip"
      className={cn(
        "border-border bg-surface pointer-events-none absolute top-9 z-20 hidden w-60 overflow-hidden rounded-xl border shadow-xl",
        align === "end" ? "right-0" : "left-0",
        "group-focus-within/hc:block group-hover/hc:block",
        "motion-safe:group-focus-within/hc:animate-[hovercard-in_260ms_var(--ease-standard,ease-out)] motion-safe:group-hover/hc:animate-[hovercard-in_260ms_var(--ease-standard,ease-out)]",
      )}
    >
      <span className="from-primary/15 to-secondary/15 flex flex-col items-center gap-1.5 bg-gradient-to-br p-4 text-center">
        <MemberAvatar
          name={name}
          hue={hueOf(name, user)}
          subject={user?.id ?? null}
          src={user?.avatarUrl ?? null}
          className={cn(avatarClass, "size-12 text-base")}
        />
        <MemberLabel
          name={name}
          status={user?.memberStatus ?? "PRESENT"}
          className="text-foreground text-sm font-bold"
        />
      </span>
      {user && <OrgRows user={user} />}
    </span>
  );
}

/** Avatar that reveals an identity + org card on hover/focus, and optionally
 * opens a management popover on click when `onPress` is given. */
export function PresenterHoverCard({
  name,
  user,
  onPress,
  suppressCard = false,
  align = "start",
}: Readonly<Props>) {
  return (
    <span className="group/hc relative inline-flex">
      <button
        type="button"
        aria-label={name}
        onClick={onPress}
        className="inline-flex cursor-pointer rounded-full motion-safe:animate-[avatar-pop_280ms_var(--ease-standard,ease-out)]"
      >
        <MemberAvatar
          name={name}
          hue={hueOf(name, user)}
          subject={user?.id ?? null}
          src={user?.avatarUrl ?? null}
          className={avatarClass}
        />
      </button>
      {!suppressCard && <HoverCard name={name} user={user} align={align} />}
    </span>
  );
}
