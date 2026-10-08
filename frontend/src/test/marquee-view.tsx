import { render } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { vi } from "vitest";

import { useIsOrganizer } from "@/features/community/api/use-organizer";
import { SessionMarquee } from "@/features/community/ui/SessionMarquee";
import { useActionItems } from "@/features/events/api/use-action-items";
import {
  useLastHeldSession,
  useUpcomingSessions,
} from "@/features/events/api/use-sessions";
import type { Session } from "@/features/events/domain/session";
import { useMe } from "@/features/identity/api/use-me";
import { useUsers } from "@/features/identity/api/use-users";
import { useSessionResources } from "@/features/knowledge/api/use-session-resources";
import { aScheduledSession, aSection, aSlot } from "@/test/session-fixture";

/** The community's last session: one lightning talk, held, with someone in the room. */
export const HELD = aScheduledSession({
  id: 10,
  status: "HELD",
  title: "Cutting p95",
  scheduledAt: "2026-09-15T18:00:00Z",
  attendeeCount: 9,
  sections: [
    aSection({
      sectionType: "Lightning Talk",
      slots: [
        aSlot({
          id: 1,
          title: "Cutting our p95 in half",
          presenterUserIds: ["carol"],
        }),
      ],
    }),
  ],
});

export interface Mocked {
  lastHeld?: Session | null;
  upcoming?: Session[];
  actionPoints?: unknown[];
  resources?: unknown[];
  organizer?: boolean;
}

/** Each importer declares its own `vi.mock` of these hooks; this only feeds them. */
export function mockQueries({
  lastHeld = HELD,
  upcoming = [],
  actionPoints = [],
  resources = [],
  organizer = false,
}: Mocked = {}) {
  vi.mocked(useLastHeldSession).mockReturnValue({
    data: lastHeld,
    isPending: false,
    isError: false,
  } as never);
  vi.mocked(useUpcomingSessions).mockReturnValue({
    data: { content: upcoming },
    isPending: false,
    isError: false,
  } as never);
  vi.mocked(useActionItems).mockReturnValue({
    data: { content: actionPoints, totalElements: actionPoints.length },
    isPending: false,
    isError: false,
  } as never);
  vi.mocked(useSessionResources).mockReturnValue({
    data: resources,
    isPending: false,
    isError: false,
  } as never);
  vi.mocked(useIsOrganizer).mockReturnValue(organizer);
}

/** The reader is Carol, who presented the talk above, so an action point of her own can be hers. */
export function mockReader() {
  vi.mocked(useMe).mockReturnValue({ data: { id: "carol" } } as never);
  vi.mocked(useUsers).mockReturnValue({
    data: { carol: { id: "carol", displayName: "Carol Chen" } },
  } as never);
}

export function renderMarquee() {
  return render(
    <MemoryRouter>
      <SessionMarquee slug="guild" />
    </MemoryRouter>,
  );
}
