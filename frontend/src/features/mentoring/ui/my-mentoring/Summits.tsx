import { useId } from "react";
import { Link, useNavigate } from "react-router-dom";

import { MenuButton, MenuItem } from "@/components/ui/Menu";
import type {
  MentoringRole,
  PairingSummary,
} from "@/features/mentoring/domain/mentoring";
import { partnerRole, plural } from "@/features/mentoring/ui/journal-people";
import { pairingDates } from "@/features/mentoring/ui/mentoring-dates";
import { useJournalPath } from "@/features/mentoring/ui/mentoring-paths";
import type { Summit } from "@/features/mentoring/ui/my-mentoring/mentoring-groups";
import { personName } from "@/features/mentoring/ui/PartnerFace";

const TILE =
  "border-border bg-surface hover:border-border-strong focus-visible:ring-focus-ring relative flex w-full items-center gap-3 rounded-[14px] border p-3 text-left outline-none focus-visible:ring-2 motion-safe:transition-colors";

const PARTNER_INK: Record<MentoringRole, string> = {
  MENTEE: "var(--mentor)",
  MENTOR: "var(--mentee)",
};

function Mountain({ myRole }: Readonly<{ myRole: MentoringRole }>) {
  const ink = PARTNER_INK[myRole];
  const gold = `summit-gold${useId().replace(/\W/g, "")}`;
  return (
    <svg aria-hidden viewBox="0 0 46 36" className="h-9 w-[46px] flex-none">
      <defs>
        <linearGradient id={gold} x1="0" x2="1" y1="0" y2="1">
          <stop offset="0" stopColor="var(--ring-gold-b)" />
          <stop offset="1" stopColor="var(--ring-gold-a)" />
        </linearGradient>
      </defs>
      <path
        d="M 2 34 L 18 10 L 26 20 L 32 12 L 44 34 Z"
        fill={`color-mix(in srgb, ${ink} 18%, var(--surface))`}
        stroke={ink}
        strokeWidth={1.5}
        strokeLinejoin="round"
      />
      <path
        d="M 18 10 L 14 16 L 18 14.5 L 21 17 Z"
        fill={`color-mix(in srgb, ${ink} 45%, var(--surface))`}
      />
      <path d="M 18 10 V 1.5" stroke="var(--ring-gold-a)" strokeWidth={1.5} />
      <path d="M 18 1.5 L 26 4.5 L 18 7.5 Z" fill={`url(#${gold})`} />
    </svg>
  );
}

function years(pairings: PairingSummary[]): string {
  const all = pairings.flatMap((pairing) =>
    [pairing.startedAt, pairing.endedAt ?? pairing.startedAt].map((iso) =>
      new Date(iso).getFullYear(),
    ),
  );
  const distinct = [...new Set(all)];
  distinct.sort((a, b) => a - b);
  return distinct.join(", ");
}

function SummitLine({ pairings }: Readonly<Summit>) {
  const [newest] = pairings;
  if (newest === undefined) return null;
  const when =
    pairings.length === 1
      ? pairingDates(newest.startedAt, newest.endedAt)
      : years(pairings);
  const topics = pairings.reduce(
    (sum, pairing) =>
      sum + pairing.stops.filter((state) => state === "DONE").length,
    0,
  );
  return (
    <>
      {partnerRole(newest)} · <span className="whitespace-nowrap">{when}</span>{" "}
      ·{" "}
      <span className="whitespace-nowrap">
        {plural(topics, "topic", "topics")}
      </span>
    </>
  );
}

function Face({ summit }: Readonly<{ summit: Summit }>) {
  return (
    <>
      <Mountain myRole={summit.myRole} />
      <span className="min-w-0">
        <span className="text-foreground block text-sm font-semibold wrap-anywhere">
          {personName(summit.partnerName, summit.partnerId)}
        </span>
        <span className="text-muted-foreground block text-xs wrap-anywhere">
          <SummitLine {...summit} />
        </span>
      </span>
    </>
  );
}

function Stacked({ summit }: Readonly<{ summit: Summit }>) {
  const pathOf = useJournalPath();
  const navigate = useNavigate();
  const name = personName(summit.partnerName, summit.partnerId);
  return (
    <MenuButton
      label={`${name}, ${summit.pairings.length} pairings`}
      className={TILE}
      onAction={(key) => {
        void navigate(pathOf(Number(key)));
      }}
      trigger={
        <>
          <Face summit={summit} />
          <span className="font-display bg-surface-sunken border-border-strong text-foreground absolute -top-2 -right-2 rounded-full border px-1.5 py-px text-[11px] font-bold">
            ×{summit.pairings.length}
          </span>
        </>
      }
    >
      {summit.pairings.map((pairing) => (
        <MenuItem key={pairing.id} id={pairing.id}>
          {pairingDates(pairing.startedAt, pairing.endedAt)}
        </MenuItem>
      ))}
    </MenuButton>
  );
}

function Single({ summit }: Readonly<{ summit: Summit }>) {
  const pathOf = useJournalPath();
  const [pairing] = summit.pairings;
  if (pairing === undefined) return null;
  return (
    <Link to={pathOf(pairing.id)} className={TILE}>
      <Face summit={summit} />
    </Link>
  );
}

/** Ended pairings, one summit per partner and role; a pair that walked more than once stacks. */
export function Summits({ summits }: Readonly<{ summits: Summit[] }>) {
  return (
    <ul className="grid grid-cols-1 gap-3 sm:grid-cols-2">
      {summits.map((summit) => (
        <li key={summit.key} className="flex">
          {summit.pairings.length > 1 ? (
            <Stacked summit={summit} />
          ) : (
            <Single summit={summit} />
          )}
        </li>
      ))}
    </ul>
  );
}
