import { Calendar } from "lucide-react";

import { Select } from "@/components/ui/Select";
import { useSessions } from "@/features/events/api/use-sessions";
import type { Session } from "@/features/events/domain/session";
import { useArtifactSessions } from "@/features/knowledge/api/use-library-insights";

/** Sentinel for "Any session" — no session filtering. */
export const ANY_SESSION = -1;
/** Sentinel for standalone artifacts (matches the backend's `session=0`). */
export const NO_SESSION = 0;

interface Props {
  slug: string;
  value: number;
  onChange: (value: number) => void;
}

function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString(undefined, {
    month: "short",
    day: "numeric",
    year: "numeric",
  });
}

function labelFor(
  sessionId: number,
  sessionById: Map<number, Session>,
): string {
  const session = sessionById.get(sessionId);
  if (!session) {
    return `Session #${sessionId}`;
  }
  return `${session.displayTitle} · ${formatDate(session.occurrenceDate ?? session.scheduledAt)}`;
}

/**
 * A select over the sessions that actually have artifacts, labelled by
 * intersecting with the community's events sessions ("name · date"). Pure surface
 * composition — no knowledge↔events backend coupling.
 */
export function SessionFilter({ slug, value, onChange }: Readonly<Props>) {
  const artifactSessions = useArtifactSessions(slug);
  const sessions = useSessions(slug);

  const sessionById = new Map(
    (sessions.data ?? []).map((session) => [session.id, session]),
  );

  const options = [
    { id: ANY_SESSION, label: "Any session" },
    { id: NO_SESSION, label: "No session" },
    ...(artifactSessions.data ?? []).map((entry) => ({
      id: entry.sessionId,
      label: labelFor(entry.sessionId, sessionById),
    })),
  ];

  return (
    <Select
      label="Session"
      hideLabel
      value={value}
      options={options}
      onChange={onChange}
      leadingIcon={<Calendar size={15} />}
      className="sm:w-56"
    />
  );
}
