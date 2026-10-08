import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { screen, waitFor } from "@testing-library/react";
import type userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { expect, vi } from "vitest";

import type { Section } from "@/features/events/domain/section";
import type { Session } from "@/features/events/domain/session";
import type { Slot } from "@/features/events/domain/slot";
import { jsonResponse, urlOf } from "@/test/fetch";

/**
 * A slot form, wired to its real hooks and the real HTTP gateway, with only `fetch` stubbed: the
 * only harness that can see WHICH endpoint a form calls and WHAT body it sends. Component-state
 * assertions cannot, and that is exactly the bug this surface has already shipped once.
 */

const sessionJson = {
  id: 9,
  seriesId: 1,
  communityId: 1,
  status: "SCHEDULED",
  scheduledAt: "2099-01-01T18:00:00Z",
  hasTime: true,
  durationMinutes: 60,
  facilitatorUserId: "u-alice",
  attendeeCount: 0,
  notes: null,
  recordingUrl: null,
  sections: [],
  createdAt: "x",
  updatedAt: "x",
};

/** The one topic in the backlog, so the add form's "From backlog" branch has something to pick. */
const backlogTopic = {
  id: 42,
  communityId: 1,
  kind: "OFFER",
  title: "Event Sourcing",
  description: "How to use event sourcing",
  tags: ["event-sourcing"],
  proposerUserId: "user-1",
  proposerDisplayName: "Alice",
  voteCount: 3,
  hasVoted: false,
  canEdit: false,
  canDelete: false,
  status: "PROPOSED",
  scheduledFor: null,
  createdAt: "x",
  updatedAt: "x",
};

export const slotFormSession = { id: 9 } as Session;

export const slotFormSection: Section = {
  id: 3,
  sessionId: 9,
  position: 0,
  sectionTypeId: 2,
  sectionType: "Talk",
  sectionTypeIcon: null,
  sectionTypeAccentColor: null,
  title: "Main talk",
  durationMinutes: 60,
  slots: [],
};

export function slotWith(overrides: Partial<Slot>): Slot {
  return {
    id: 7,
    sectionId: 3,
    position: 0,
    presenterUserIds: ["u-alice"],
    topicId: null,
    title: "Hexagons",
    notes: null,
    durationMinutes: 20,
    tags: [],
    ...overrides,
  };
}

function respond(url: string): Response {
  if (url.includes("/slots/recorded")) {
    return jsonResponse(
      {
        session: sessionJson,
        slotId: 5,
        presenters: { assigned: [], unmatched: [], ambiguous: [] },
      },
      201,
    );
  }
  if (url.includes("/topics")) {
    return jsonResponse({
      content: [backlogTopic],
      page: { size: 50, number: 0, totalElements: 1, totalPages: 1 },
    });
  }
  return jsonResponse(sessionJson);
}

/** Stubs fetch with a backend that answers every call a slot form can make. */
export function stubSlotBackend() {
  vi.stubGlobal(
    "fetch",
    vi.fn((input: RequestInfo | URL) => Promise.resolve(respond(urlOf(input)))),
  );
}

interface Write {
  url: string;
  method: string;
  body: Record<string, unknown>;
}

/** The JSON body of a fetch call: every write in this app sends a stringified body. */
function bodyOf(body: BodyInit | null | undefined): Record<string, unknown> {
  return JSON.parse(typeof body === "string" ? body : "{}") as Record<
    string,
    unknown
  >;
}

function writes(): Write[] {
  return vi
    .mocked(fetch)
    .mock.calls.filter((call) => (call[1]?.method ?? "GET") !== "GET")
    .map((call) => ({
      url: urlOf(call[0]),
      method: call[1]?.method ?? "GET",
      body: bodyOf(call[1]?.body),
    }));
}

/** The single write the form made: its endpoint, its verb, and the body that left the app. */
export async function theWrite(): Promise<Write> {
  await waitFor(() => expect(writes()).toHaveLength(1));
  const [write] = writes();
  if (!write) {
    throw new Error("no write reached the gateway");
  }
  return write;
}

export function Wrapped({ children }: Readonly<{ children: ReactNode }>) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

/** Types each topic into the slot forms' "Topics" field, committing it with Enter. */
export async function typeTopics(
  user: ReturnType<typeof userEvent.setup>,
  ...tags: string[]
) {
  const field = screen.getByLabelText("Topics");
  for (const tag of tags) {
    await user.type(field, `${tag}{Enter}`);
  }
}
