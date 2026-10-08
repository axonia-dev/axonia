import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useSession } from "@/features/events/api/use-session";
import { EMPTY_PLACE } from "@/features/events/domain/meeting-place";
import type { Session } from "@/features/events/domain/session";
import { SessionDetailPage } from "@/features/events/ui/SessionDetailPage";
import { useEventActions } from "@/features/events/ui/useEventActions";

vi.mock("@/features/events/api/use-member-zones", () => ({
  useMemberZones: vi.fn(() => ({ data: undefined })),
}));

vi.mock("@/features/events/api/use-session", () => ({
  useSession: vi.fn(),
  // The reminder card is a sibling on this page; every session here is one it renders
  // nothing for. Its own slot is covered by SessionDetailPage.reminder.test.tsx.
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
  SessionActions: ({ canEditStructure }: { canEditStructure: boolean }) =>
    canEditStructure ? <div data-testid="session-actions" /> : null,
}));
vi.mock("@/features/bookmarks/api/use-save", () => ({
  useSaveState: () => ({ data: { viewerHasSaved: false, viewerHelped: null } }),
  useSave: () => ({ mutate: vi.fn(), isPending: false }),
  useUnsave: () => ({ mutate: vi.fn(), isPending: false }),
}));
vi.mock("@/features/events/ui/SkippedBanner", () => ({
  SkippedBanner: () => <div>This occurrence has been skipped.</div>,
}));
vi.mock("@/features/events/ui/SessionClosePanel", () => ({
  SessionClosePanel: () => null,
}));
vi.mock("@/features/events/ui/SessionCard", () => ({
  SessionCard: ({
    mode,
    session,
    headerActions,
  }: {
    mode: string;
    session: { id: number };
    headerActions?: ReactNode;
  }) => (
    <div data-testid="session-card">
      card {session.id} mode:{mode}
      {headerActions}
    </div>
  ),
}));

const mockUseSession = vi.mocked(useSession);
const mockUseEventActions = vi.mocked(useEventActions);

function mockSession(overrides: Partial<Session> = {}) {
  mockUseSession.mockReturnValue({
    data: { ...baseSession, ...overrides },
    isLoading: false,
    isError: false,
  } as never);
}

function mockEventActions(overrides: { canEditStructure: boolean }) {
  mockUseEventActions.mockReturnValue({
    canManage: false,
    canManageMessages: false,
    canStartSeries: false,
    canEditStructure: overrides.canEditStructure,
    canQueueTopics: false,
    canManageSchedule: false,
  });
}

const baseSession: Session = {
  id: 7,
  seriesId: 1,
  communityId: 1,
  status: "SCHEDULED",
  title: null,
  occurrenceDate: null,
  scheduledAt: "2099-01-01T18:00:00Z",
  hasTime: true,
  durationMinutes: 60,
  facilitatorUserId: null,
  attendeeCount: 0,
  notes: null,
  recordingUrl: null,
  place: EMPTY_PLACE,
  placeOverride: EMPTY_PLACE,
  seriesPlace: EMPTY_PLACE,
  seriesTimeZone: "Europe/Paris",
  sections: [],
  createdAt: "x",
  updatedAt: "x",
  closedAt: null,
  wrapUpReleaseAt: null,
  wrapUpStatus: null,
  upcomingReleaseAt: null,
  upcomingStatus: null,
};

function renderPage() {
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

beforeEach(() => {
  vi.clearAllMocks();
  mockEventActions({ canEditStructure: true });
});

describe("SessionDetailPage", () => {
  it("renders an upcoming session in contribute mode", () => {
    mockUseSession.mockReturnValue({
      data: baseSession,
      isLoading: false,
      isError: false,
    } as never);
    renderPage();
    expect(screen.getByTestId("session-card")).toHaveTextContent(
      "card 7 mode:contribute",
    );
  });

  it("renders a held session in capture mode", () => {
    mockUseSession.mockReturnValue({
      data: { ...baseSession, status: "HELD" },
      isLoading: false,
      isError: false,
    } as never);
    renderPage();
    expect(screen.getByTestId("session-card")).toHaveTextContent(
      "card 7 mode:capture",
    );
  });

  it("shows a loading state", () => {
    mockUseSession.mockReturnValue({
      data: undefined,
      isLoading: true,
      isError: false,
    } as never);
    renderPage();
    expect(screen.getByText(/loading/i)).toBeInTheDocument();
  });

  it("shows a not-found state on error", () => {
    mockUseSession.mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
    } as never);
    renderPage();
    expect(screen.getByText(/session not found/i)).toBeInTheDocument();
  });

  it("passes SessionActions as headerActions to the session card", () => {
    mockUseSession.mockReturnValue({
      data: baseSession,
      isLoading: false,
      isError: false,
    } as never);
    renderPage();
    // The mock SessionActions renders when canEditStructure is true (which the
    // useEventActions mock returns). It is rendered inside the SessionCard mock.
    expect(screen.getByTestId("session-actions")).toBeInTheDocument();
  });

  it("renders a SaveAction bookmark for the session in headerActions", () => {
    mockUseSession.mockReturnValue({
      data: baseSession,
      isLoading: false,
      isError: false,
    } as never);
    renderPage();
    expect(
      screen.getByRole("button", { name: /save for later/i }),
    ).toBeInTheDocument();
  });

  it("renders the skipped banner and readonly mode for a skipped session", () => {
    mockUseSession.mockReturnValue({
      data: { ...baseSession, status: "SKIPPED", occurrenceDate: "2099-01-01" },
      isLoading: false,
      isError: false,
    } as never);
    renderPage();
    expect(screen.getByText(/has been skipped/i)).toBeInTheDocument();
    expect(screen.getByTestId("session-card")).toHaveTextContent(
      "card 7 mode:readonly",
    );
    expect(
      screen.queryByRole("button", { name: /add a slot/i }),
    ).not.toBeInTheDocument();
  });

  it("keeps a held session read-only by default, so nobody rewrites history by reflex", () => {
    mockSession({ status: "HELD" });
    mockEventActions({ canEditStructure: true });

    renderPage();

    expect(screen.getByText("card 7 mode:capture")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Edit" })).toBeInTheDocument();
  });

  it("switches a held session into edit mode when the organizer asks for it", async () => {
    mockSession({ status: "HELD" });
    mockEventActions({ canEditStructure: true });
    const user = userEvent.setup({ delay: null });

    renderPage();
    await user.click(screen.getByRole("button", { name: "Edit" }));

    expect(screen.getByText("card 7 mode:edit")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Done" })).toBeInTheDocument();
  });

  it("does not offer the Edit toggle to a member without structure-edit rights", () => {
    mockSession({ status: "HELD" });
    mockEventActions({ canEditStructure: false });

    renderPage();

    expect(
      screen.queryByRole("button", { name: "Edit" }),
    ).not.toBeInTheDocument();
  });

  it("does not offer the Edit toggle on a skipped session", () => {
    mockSession({ status: "SKIPPED", occurrenceDate: "2099-01-01" });
    mockEventActions({ canEditStructure: true });

    renderPage();

    expect(screen.getByText("card 7 mode:readonly")).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Edit" }),
    ).not.toBeInTheDocument();
  });

  it("resolves a cancelled session to readonly mode, exactly like a skipped one", () => {
    mockSession({ status: "CANCELLED" });

    renderPage();

    expect(screen.getByTestId("session-card")).toHaveTextContent(
      "card 7 mode:readonly",
    );
  });

  it("keeps the actions menu reachable on a cancelled session, so it is never stranded", () => {
    mockSession({ status: "CANCELLED" });
    mockEventActions({ canEditStructure: true });

    renderPage();

    expect(screen.getByTestId("session-actions")).toBeInTheDocument();
  });
});
