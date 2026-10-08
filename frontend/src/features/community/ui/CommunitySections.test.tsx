import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  BacklogSection,
  LibrarySection,
} from "@/features/community/ui/CommunitySections";

// ---------------------------------------------------------------------------
// Mocks
// ---------------------------------------------------------------------------

vi.mock("@/auth/current-user", () => ({ currentUserId: vi.fn() }));
vi.mock("@/features/community/api/use-organizer", () => ({
  useIsOrganizer: vi.fn(),
}));
vi.mock("@/features/community/api/use-management-access", () => ({
  useManagementAccess: vi.fn(),
}));
vi.mock("@/features/knowledge/ui/ArtifactActions", () => ({
  ArtifactActions: ({
    canEdit,
    canCredit,
  }: {
    canEdit: boolean;
    canCredit: boolean;
  }) => (
    <div data-testid="artifact-actions">
      {canEdit && <span data-testid="can-edit" />}
      {canCredit && <span data-testid="can-credit" />}
    </div>
  ),
}));
vi.mock("@/features/knowledge/ui/LibraryView", () => ({
  LibraryView: (props: {
    renderArtifactManageActions?: (artifact: {
      contributorUserId: string;
    }) => React.ReactNode;
  }) => (
    <div data-testid="library-view">
      {props.renderArtifactManageActions?.({ contributorUserId: "olive" })}
    </div>
  ),
}));
vi.mock("@/features/events/ui/BacklogView", () => ({
  BacklogView: (props: {
    renderScheduledActions?: (t: { id: number }) => React.ReactNode;
  }) => (
    <div data-testid="scheduled-actions">
      {props.renderScheduledActions?.({ id: 7 })}
    </div>
  ),
}));
vi.mock("@/features/events/ui/ReturnToBacklogButton", () => ({
  ReturnToBacklogButton: (props: { topicId: number }) => (
    <div data-testid="return-btn">{props.topicId}</div>
  ),
}));

import { currentUserId } from "@/auth/current-user";
import { useManagementAccess } from "@/features/community/api/use-management-access";
import { useIsOrganizer } from "@/features/community/api/use-organizer";

const mockCurrentUserId = vi.mocked(currentUserId);
const mockIsOrganizer = vi.mocked(useIsOrganizer);
const mockManagementAccess = vi.mocked(useManagementAccess);

function renderLibrary() {
  render(
    <MemoryRouter initialEntries={["/communities/backend-guild/library"]}>
      <LibrarySection />
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  mockCurrentUserId.mockReturnValue("someone-else");
  mockIsOrganizer.mockReturnValue(true);
  mockManagementAccess.mockReturnValue({ credit: false } as never);
});

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe("BacklogSection", () => {
  it("offers a return-to-backlog action on scheduled topics for an organizer", () => {
    // when
    render(
      <MemoryRouter initialEntries={["/communities/backend-guild/backlog"]}>
        <BacklogSection />
      </MemoryRouter>,
    );

    // then the organizer-gated scheduled action renders the return button
    expect(screen.getByTestId("return-btn")).toHaveTextContent("7");
  });
});

describe("LibrarySection", () => {
  it("renders the library view for the routed community", () => {
    // when
    renderLibrary();

    // then
    expect(screen.getByTestId("library-view")).toBeInTheDocument();
  });

  it("offers only Change contributor to somebody who may credit but is not the contributor or an organizer", () => {
    // given
    mockIsOrganizer.mockReturnValue(false);
    mockManagementAccess.mockReturnValue({ credit: true } as never);
    mockCurrentUserId.mockReturnValue("someone-else");

    // when
    renderLibrary();

    // then
    expect(screen.getByTestId("can-credit")).toBeInTheDocument();
    expect(screen.queryByTestId("can-edit")).toBeNull();
  });

  it("offers Edit and Delete to an organizer who is not the contributor", () => {
    // given
    mockIsOrganizer.mockReturnValue(true);
    mockManagementAccess.mockReturnValue({ credit: true } as never);
    mockCurrentUserId.mockReturnValue("someone-else");

    // when
    renderLibrary();

    // then
    expect(screen.getByTestId("can-edit")).toBeInTheDocument();
    expect(screen.getByTestId("can-credit")).toBeInTheDocument();
  });

  it("offers only Edit and Delete to the artifact's own contributor when the axis is closed", () => {
    // given
    mockIsOrganizer.mockReturnValue(false);
    mockManagementAccess.mockReturnValue({ credit: false } as never);
    mockCurrentUserId.mockReturnValue("olive");

    // when
    renderLibrary();

    // then
    expect(screen.getByTestId("can-edit")).toBeInTheDocument();
    expect(screen.queryByTestId("can-credit")).toBeNull();
  });

  it("offers nothing to a caller who is neither the contributor, an organizer, nor allowed to credit", () => {
    // given
    mockIsOrganizer.mockReturnValue(false);
    mockManagementAccess.mockReturnValue({ credit: false } as never);
    mockCurrentUserId.mockReturnValue("someone-else");

    // when
    renderLibrary();

    // then
    expect(screen.queryByTestId("artifact-actions")).toBeNull();
  });
});
