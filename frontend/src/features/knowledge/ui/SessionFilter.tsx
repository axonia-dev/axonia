import { Calendar } from "lucide-react";

import { Select } from "@/components/ui/Select";
import { useArtifactSessions } from "@/features/knowledge/api/use-library-insights";
import type { ArtifactSession } from "@/features/knowledge/domain/artifact-session";
import { formatHeldOn } from "@/features/knowledge/ui/held-on";

/** Sentinel for "Any session": no session filtering. */
export const ANY_SESSION = -1;
/** Sentinel for standalone artifacts (matches the backend's `session=0`). */
export const NO_SESSION = 0;

interface Props {
  slug: string;
  value: number;
  onChange: (value: number) => void;
}

function labelFor({ sessionId, name, heldOn }: ArtifactSession): string {
  if (name === null || heldOn === null) {
    return `Session #${sessionId}`;
  }
  return `${name} · ${formatHeldOn(heldOn)}`;
}

/**
 * A select over the sessions that actually have artifacts, labelled and ordered
 * (newest held first) by the server, so it never needs the community's whole
 * session history.
 */
export function SessionFilter({ slug, value, onChange }: Readonly<Props>) {
  const artifactSessions = useArtifactSessions(slug);

  const options = [
    { id: ANY_SESSION, label: "Any session" },
    { id: NO_SESSION, label: "No session" },
    ...(artifactSessions.data ?? []).map((session) => ({
      id: session.sessionId,
      label: labelFor(session),
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
