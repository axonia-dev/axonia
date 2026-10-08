import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { setCurrentUserId } from "@/auth/current-user";
import { useManagementAccess } from "@/features/community/api/use-management-access";
import type { Section } from "@/features/events/domain/section";
import type { Session } from "@/features/events/domain/session";
import type { Slot } from "@/features/events/domain/slot";
import { SlotRow } from "@/features/events/ui/SlotRow";
import { jsonResponse, urlOf } from "@/test/fetch";

vi.mock("@/features/community/api/use-management-access", () => ({
  useManagementAccess: vi.fn(),
}));

/**
 * The human backfill path, end to end, with only `fetch` stubbed: an organizer opens a held session
 * for editing, captures the recording of a talk Alice gave, and the body that leaves the app must
 * credit ALICE.
 *
 * Asserted on the request body, deliberately, and not on component state: crediting the caller is a
 * bug that has already shipped twice on this surface, and both times the component's own state
 * looked right. The only thing that decides who becomes an expert in kafka is what the POST says.
 */

const ORGANIZER = "orga-id";
const ALICE = "alice-id";
const BOB = "bob-id";

const session = { id: 9 } as Session;
const section = { id: 3 } as Section;

function slotPresentedBy(...presenterUserIds: string[]): Slot {
  return {
    id: 7,
    sectionId: 3,
    position: 0,
    presenterUserIds,
    topicId: null,
    title: "Kafka 101",
    notes: null,
    durationMinutes: 20,
    tags: ["kafka"],
  };
}

const artifact = {
  id: 42,
  communityId: 1,
  typeId: 3,
  type: "Recording",
  title: "Kafka 101",
  description: "",
  url: "https://example.com/rec",
  tags: [],
  contributorUserId: ALICE,
  loveCount: 0,
  provenance: null,
  status: "ACTIVE",
  createdAt: "2026-07-12T10:00:00Z",
  updatedAt: "2026-07-12T10:00:00Z",
};

const emptyPage = {
  content: [],
  page: { size: 20, number: 0, totalElements: 0, totalPages: 0 },
};

/** A backend that answers every call this row and its dialog can make. */
function respond(url: string, method: string): Response {
  if (url.includes("/artifact-types")) {
    return jsonResponse([
      {
        id: 3,
        label: "Recording",
        icon: null,
        accentColor: null,
        artifactCount: 0,
      },
    ]);
  }
  if (url.includes("/users")) {
    return jsonResponse([
      {
        id: ALICE,
        displayName: "Alice Dupont",
        email: null,
        memberStatus: "PRESENT",
      },
      {
        id: BOB,
        displayName: "Bob Martin",
        email: null,
        memberStatus: "PRESENT",
      },
      {
        id: ORGANIZER,
        displayName: "Olive Organizer",
        email: null,
        memberStatus: "PRESENT",
      },
    ]);
  }
  if (url.includes("/resources")) {
    return method === "POST"
      ? jsonResponse(
          {
            id: 1,
            artifactId: 42,
            sessionId: 9,
            segmentId: 7,
            attachedByUserId: ORGANIZER,
            createdAt: "2026-07-12T10:00:00Z",
          },
          201,
        )
      : jsonResponse([]);
  }
  if (url.includes("/artifacts")) {
    return method === "POST"
      ? jsonResponse(artifact, 201)
      : jsonResponse(emptyPage);
  }
  return jsonResponse(emptyPage);
}

function Wrapped({ children }: Readonly<{ children: ReactNode }>) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

function renderRow(slot: Slot) {
  render(
    <Wrapped>
      <SlotRow
        slug="backend-guild"
        session={session}
        section={section}
        slot={slot}
        mode="edit"
        canManage
        canManageSchedule
      />
    </Wrapped>,
  );
}

/** The body of the POST that created the artifact: the only thing the expertise graph ever sees. */
async function capturedArtifactBody(): Promise<Record<string, unknown>> {
  const post = () =>
    vi
      .mocked(fetch)
      .mock.calls.find(
        (call) =>
          call[1]?.method === "POST" && urlOf(call[0]).includes("/artifacts"),
      );
  await waitFor(() => expect(post()).toBeDefined());
  const body = post()?.[1]?.body;
  return JSON.parse(typeof body === "string" ? body : "{}") as Record<
    string,
    unknown
  >;
}

/** Walks the organizer through: + Add resource, create instead of attach, fill, submit. */
async function captureAResource() {
  await user.click(screen.getByRole("button", { name: /add resource/i }));
  await user.click(
    screen.getByRole("button", { name: /create a new artifact/i }),
  );
  await user.click(screen.getByRole("button", { name: /choose a type/i }));
  await user.click(await screen.findByRole("option", { name: "Recording" }));
  await user.type(screen.getByLabelText("Link"), "https://example.com/rec");
}

// The only file here that keeps user-event's default delay. It drives the real gateway, so the
// artifact-type vocabulary arrives over the network between two interactions, and react-stately
// refuses to open a Select whose collection is still empty. The per-event delay is what yields the
// event loop for that response to land; nothing in the DOM signals it has.
let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup();
  vi.mocked(useManagementAccess).mockReturnValue({ credit: true } as never);
  setCurrentUserId(ORGANIZER);
  vi.stubGlobal(
    "fetch",
    vi.fn((input: RequestInfo | URL, init?: RequestInit) =>
      Promise.resolve(respond(urlOf(input), init?.method ?? "GET")),
    ),
  );
});

afterEach(() => {
  vi.unstubAllGlobals();
  setCurrentUserId(undefined);
});

describe("SlotRow - who a captured resource is credited to", () => {
  it("credits the slot's sole presenter, not the organizer who files the recording", async () => {
    renderRow(slotPresentedBy(ALICE));

    await captureAResource();
    expect(
      await screen.findByText(/credited to alice dupont/i),
    ).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Attach" }));

    expect(await capturedArtifactBody()).toMatchObject({
      title: "Kafka 101",
      contributor: ALICE,
    });
  });

  it("makes the organizer choose between co-presenters rather than guessing", async () => {
    renderRow(slotPresentedBy(ALICE, BOB));

    await captureAResource();
    // No safe default with two speakers: the form will not submit until one is named.
    expect(screen.getByRole("button", { name: "Attach" })).toBeDisabled();
    await user.click(
      screen.getByRole("button", { name: /choose who this is by/i }),
    );
    await user.click(await screen.findByRole("option", { name: "Bob Martin" }));
    await user.click(screen.getByRole("button", { name: "Attach" }));

    expect(await capturedArtifactBody()).toMatchObject({ contributor: BOB });
  });

  it("sends no contributor when the organizer credits themselves", async () => {
    renderRow(slotPresentedBy(ALICE, BOB));

    await captureAResource();
    await user.click(
      screen.getByRole("button", { name: /choose who this is by/i }),
    );
    await user.click(await screen.findByRole("option", { name: "Me" }));
    await user.click(screen.getByRole("button", { name: "Attach" }));

    // Omitted means "the caller": the one value a non-organizer is also allowed to send.
    expect(await capturedArtifactBody()).not.toHaveProperty("contributor");
  });

  it("sends no contributor for somebody the community does not let credit, even on a presenter's talk", async () => {
    vi.mocked(useManagementAccess).mockReturnValue({ credit: false } as never);
    renderRow(slotPresentedBy(ALICE));

    await captureAResource();
    expect(screen.queryByText(/credited to/i)).toBeNull();
    await user.click(screen.getByRole("button", { name: "Attach" }));

    expect(await capturedArtifactBody()).not.toHaveProperty("contributor");
  });
});
