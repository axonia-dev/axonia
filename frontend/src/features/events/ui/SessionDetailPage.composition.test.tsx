import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  useMessagePreview,
  useSession,
} from "@/features/events/api/use-session";
import type { Session } from "@/features/events/domain/session";
import { SessionDetailPage } from "@/features/events/ui/SessionDetailPage";
import { useEventActions } from "@/features/events/ui/useEventActions";
import { aScheduledSession, aSection, aSlot } from "@/test/session-fixture";

vi.mock("@/features/events/api/use-member-zones", () => ({
  useMemberZones: vi.fn(() => ({ data: undefined })),
}));

vi.mock("@/features/events/api/use-session", () => ({
  useSession: vi.fn(),
  useSetUpcomingRelease: vi.fn(),
  useMessagePreview: vi.fn(),
}));
vi.mock("@/features/events/ui/useEventActions", () => ({
  useEventActions: vi.fn(),
}));
vi.mock("@/features/community/ui/community-chrome", () => ({
  useCommunityCrumbs: vi.fn(),
}));
vi.mock("@/features/events/ui/SessionActions", () => ({
  SessionActions: () => null,
}));
vi.mock("@/features/bookmarks/api/use-save", () => ({
  useSaveState: () => ({ data: { viewerHasSaved: false, viewerHelped: null } }),
  useSave: () => ({ mutate: vi.fn(), isPending: false }),
  useUnsave: () => ({ mutate: vi.fn(), isPending: false }),
}));
vi.mock("@/features/events/ui/SessionClosePanel", () => ({
  SessionClosePanel: ({ canManage }: { canManage: boolean }) =>
    canManage ? <p>Did this session happen?</p> : null,
}));
vi.mock("@/features/knowledge/ui/SessionOrphanResources", () => ({
  SessionOrphanResources: () => null,
}));
vi.mock("@/features/events/ui/SessionToVerify", () => ({
  SessionToVerify: () => null,
}));
vi.mock("@/features/events/ui/SessionActionPoints", () => ({
  SessionActionPoints: () => <button type="button">+ Action point</button>,
}));
vi.mock("@/features/events/ui/SectionBlock", () => ({
  SectionBlock: ({ section }: { section: { id: number } }) => (
    <div>section {section.id}</div>
  ),
}));

function mockPage(session: Session, options: { canManage?: boolean } = {}) {
  vi.mocked(useSession).mockReturnValue({
    data: session,
    isLoading: false,
    isError: false,
  } as never);
  vi.mocked(useEventActions).mockReturnValue({
    canManage: options.canManage ?? false,
    canManageMessages: false,
    canStartSeries: false,
    canEditStructure: false,
    canQueueTopics: false,
    canManageSchedule: false,
  });
  vi.mocked(useMessagePreview).mockReturnValue({
    data: undefined,
    isLoading: false,
    isError: false,
    error: null,
  } as never);
}

function renderPage(session: Session, options: { canManage?: boolean } = {}) {
  mockPage(session, options);
  return render(
    <MemoryRouter initialEntries={["/communities/backend-guild/sessions/7"]}>
      <Routes>
        <Route
          path="/communities/:slug/sessions/:sessionId"
          element={<SessionDetailPage slug="backend-guild" />}
        />
      </Routes>
    </MemoryRouter>,
  );
}

function heldSession({ sectionCount = 2 }: { sectionCount?: number } = {}) {
  return aScheduledSession({
    status: "HELD",
    scheduledAt: "2020-01-01T10:00:00Z",
    durationMinutes: 60,
    sections: Array.from({ length: sectionCount }, (_, i) =>
      aSection({
        id: i + 1,
        slots: [aSlot({ id: i + 1, durationMinutes: 10 })],
      }),
    ),
  });
}
function awaitingSession() {
  return aScheduledSession({
    scheduledAt: "2020-01-01T10:00:00Z",
    sections: [aSection({ slots: [aSlot()] })],
  });
}
function cancelledSession() {
  return aScheduledSession({
    status: "CANCELLED",
    sections: [aSection({ slots: [aSlot()] })],
  });
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe("composition by phase", () => {
  it("puts a held session's record above its agenda", async () => {
    renderPage(heldSession());
    const outcomes = await screen.findByRole("region", {
      name: "Action points",
    });
    const sheet = screen.getByRole("list", { name: "Run sheet" });
    expect(outcomes.compareDocumentPosition(sheet)).toBe(
      Node.DOCUMENT_POSITION_FOLLOWING,
    );
  });

  it("asks whether a past session happened before anything else", async () => {
    renderPage(awaitingSession(), { canManage: true });
    const ask = await screen.findByText("Did this session happen?");
    const sheet = screen.getByRole("list", { name: "Run sheet" });
    expect(ask.compareDocumentPosition(sheet)).toBe(
      Node.DOCUMENT_POSITION_FOLLOWING,
    );
  });

  it("offers no record on a session still waiting on an answer", async () => {
    renderPage(awaitingSession(), { canManage: true });
    await screen.findByText("Did this session happen?");
    expect(
      screen.queryByRole("region", { name: "Action points" }),
    ).not.toBeInTheDocument();
  });

  it("states a cancelled session's status in the header, not at its foot", async () => {
    renderPage(cancelledSession());
    const chip = await screen.findByText("Cancelled");
    const heading = screen.getByRole("heading", { level: 2 });
    expect(heading.compareDocumentPosition(chip)).toBe(
      Node.DOCUMENT_POSITION_FOLLOWING,
    );
  });

  it("draws no rail for a session of three sections or fewer", async () => {
    renderPage(heldSession());
    await screen.findByRole("list", { name: "Run sheet" });
    expect(
      screen.queryByRole("navigation", { name: "This session" }),
    ).not.toBeInTheDocument();
  });

  it("draws the rail once a session has four", async () => {
    renderPage(heldSession({ sectionCount: 4 }));
    expect(
      await screen.findByRole("navigation", { name: "This session" }),
    ).toBeInTheDocument();
  });
});
