import { useEffect, useState } from "react";

import { MemberAvatar } from "@/components/patterns/MemberAvatar";
import { useExpertiseSearch } from "@/features/expertise/api/use-expertise-search";
import type { Practitioner } from "@/features/expertise/domain/expertise";
import { ECHO } from "@/features/expertise/ui/echo-timeline";
import {
  countSummary,
  declarationLine,
} from "@/features/expertise/ui/practitioner-lines";
import { TierBadge } from "@/features/expertise/ui/TierBadge";
import { useNow } from "@/lib/use-wake-at";
import { userHue } from "@/lib/user-hue";

const MAX_ROWS = 4;

function msUntilCue(mountedAt: number, cueMs: number): number {
  return Math.max(0, cueMs - (performance.now() - mountedAt));
}

function PersonRow({
  practitioner,
  delayMs,
}: Readonly<{ practitioner: Practitioner; delayMs: number }>) {
  const now = useNow();
  const name = practitioner.displayName ?? practitioner.userId;
  const line =
    countSummary(practitioner.evidence) ||
    (declarationLine(practitioner, now) ?? "");
  return (
    <li
      className="flex items-start gap-2.5 motion-safe:animate-[who-knows-in_350ms_ease-out_both]"
      style={{ animationDelay: `${delayMs}ms` }}
    >
      <MemberAvatar
        name={name}
        hue={userHue(practitioner.userId)}
        subject={practitioner.userId}
        src={practitioner.avatarUrl}
        shape="circle"
        className="size-7 text-xs"
        open={practitioner.openToQuestions}
        openDot={false}
        openLabelled
      />
      <div className="min-w-0 flex-1">
        <div className="flex flex-wrap items-center gap-x-2 gap-y-0.5">
          <span className="text-foreground text-sm font-medium">{name}</span>
          <TierBadge tier={practitioner.tier} />
        </div>
        {line !== "" && <p className="text-muted-foreground text-xs">{line}</p>}
      </div>
    </li>
  );
}

/**
 * A short preview of the people Search would list for one area; it stays out of sight until it has an answer.
 * `cueMs` is how long after mounting the rows may appear: the echo's crossing time when a term is echoing on the map,
 * and nothing anywhere else.
 */
export function WhoKnowsSection({
  slug,
  tag,
  onOpenSearch,
  cueMs = 0,
}: Readonly<{
  slug: string;
  tag: string;
  onOpenSearch?: (tag: string) => void;
  cueMs?: number;
}>) {
  const [mountedAt] = useState(() => performance.now());
  const [revealed, setRevealed] = useState(false);
  const { data } = useExpertiseSearch(slug, {
    term: tag,
    demonstratedOnly: false,
  });
  const answered = data !== undefined && data.length > 0;
  // The reveal is a timer armed when the first answer lands, so a refetch never rewrites a wait already running.
  useEffect(() => {
    if (!answered) return;
    const timer = setTimeout(
      () => setRevealed(true),
      msUntilCue(mountedAt, cueMs),
    );
    return () => clearTimeout(timer);
  }, [answered, mountedAt, cueMs]);

  if (!answered || !revealed) return null;

  return (
    <section className="flex flex-col gap-2.5">
      <h3 className="text-sm font-semibold motion-safe:animate-[who-knows-in_350ms_ease-out_both]">
        Who knows {tag}
      </h3>
      <ul className="flex flex-col gap-2.5">
        {data.slice(0, MAX_ROWS).map((practitioner, index) => (
          <PersonRow
            key={practitioner.userId}
            practitioner={practitioner}
            delayMs={(index + 1) * ECHO.panelStepMs}
          />
        ))}
      </ul>
      {onOpenSearch !== undefined && (
        <button
          type="button"
          onClick={() => onOpenSearch(tag)}
          className="text-primary-strong focus-visible:ring-focus-ring cursor-pointer self-start rounded-lg text-sm font-medium outline-none hover:underline focus-visible:ring-2"
        >
          Open in Search
        </button>
      )}
    </section>
  );
}
