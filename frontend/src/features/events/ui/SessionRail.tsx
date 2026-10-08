import { runSheet, sectionAnchorId } from "@/features/events/domain/run-sheet";
import {
  sectionAccentColor,
  sectionLabel,
} from "@/features/events/domain/section";
import type { Session } from "@/features/events/domain/session";
import type { SessionPhase } from "@/features/events/domain/session-phase";
import { cn } from "@/lib/cn";

/** Below this, the run sheet's own density is enough and a map is furniture. */
export const RAIL_MIN_SECTIONS = 4;

interface RowProps {
  href: string;
  label: string;
  meta?: string;
  accent: string;
  active: boolean;
}

function RailRow({ href, label, meta, accent, active }: Readonly<RowProps>) {
  return (
    <a
      href={href}
      {...(active ? { "aria-current": "location" as const } : {})}
      className={cn(
        "flex items-center gap-2.5 rounded px-2.5 py-1.5 text-xs",
        active ? "bg-surface text-foreground" : "text-muted-foreground",
      )}
    >
      <span
        aria-hidden
        className="h-3.5 w-[3px] shrink-0 rounded-sm"
        style={{ backgroundColor: accent }}
      />
      <span className="min-w-0 flex-1 truncate font-semibold">{label}</span>
      {meta && <span className="tabular-nums">{meta}</span>}
    </a>
  );
}

interface Props {
  session: Session;
  phase: SessionPhase;
  /** The anchor the reader is currently inside, from the page's scroll spy. */
  activeId: string | null;
}

/**
 * The whole session named before a reader scrolls into it.
 *
 * Plain anchors rather than `NavLink`: a router link decides its own active state from the path
 * and drops an `aria-current` passed in from outside, which is exactly wrong for a map of one
 * page's own contents.
 */
export function SessionRail({ session, phase, activeId }: Readonly<Props>) {
  const sheet = runSheet(session);
  return (
    <nav
      aria-label="This session"
      className="border-border sticky top-20 border-r pr-5"
    >
      {phase === "held" && (
        <>
          <h4 className="text-muted-foreground mb-3 text-[10px] font-bold tracking-widest uppercase">
            This session
          </h4>
          <RailRow
            href="#session-outcomes"
            label="Action points"
            accent="var(--primary)"
            active={activeId === "session-outcomes"}
          />
          <RailRow
            href="#session-resources"
            label="Resources"
            accent="var(--secondary)"
            active={activeId === "session-resources"}
          />
        </>
      )}
      <h4 className="text-muted-foreground mt-4 mb-3 text-[10px] font-bold tracking-widest uppercase">
        Run sheet
      </h4>
      {sheet.rows.map((row) => {
        const anchorId = sectionAnchorId(row.section.id);
        return (
          <RailRow
            key={row.section.id}
            href={`#${anchorId}`}
            label={sectionLabel(row.section)}
            meta={String(row.runMinutes)}
            accent={sectionAccentColor(row.section)}
            active={activeId === anchorId}
          />
        );
      })}
      <p className="border-border text-muted-foreground mt-3 border-t pt-3 text-xs tabular-nums">
        {sheet.totalMinutes} min
        {sheet.overrunMinutes > 0 && (
          <span className="text-warning"> · {sheet.overrunMinutes} over</span>
        )}
      </p>
    </nav>
  );
}
