import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { SessionOrphanResources } from "@/features/knowledge/ui/SessionOrphanResources";

vi.mock("@/features/knowledge/api/use-artifact-types", () => ({
  useArtifactTypes: () => ({ data: [] }),
}));
vi.mock("@/features/knowledge/api/use-session-resources", () => ({
  useSessionResources: vi.fn(),
  useDetachResource: vi.fn(),
}));
vi.mock("@/auth/current-user", () => ({ currentUserId: vi.fn() }));

import { currentUserId } from "@/auth/current-user";
import {
  useDetachResource,
  useSessionResources,
} from "@/features/knowledge/api/use-session-resources";

const mockResources = vi.mocked(useSessionResources);
const mockDetach = vi.mocked(useDetachResource);
const mockUser = vi.mocked(currentUserId);

function resource(segmentId: number | null) {
  return {
    attachmentId: segmentId === null ? null : 5,
    segmentId,
    attachedByUserId: null,
    artifact: {
      id: segmentId === null ? 1 : 2,
      communityId: 1,
      typeId: 4,
      type: "Slides",
      title: segmentId === null ? "Legacy deck" : "Live deck",
      description: "",
      url: "https://x.dev/a",
      tags: [],
      contributorUserId: "alice",
      loveCount: 0,
      provenance: null,
      status: "ACTIVE",
      createdAt: "x",
      updatedAt: "x",
    },
  };
}

describe("SessionOrphanResources", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockDetach.mockReturnValue({ mutate: vi.fn() } as never);
    mockUser.mockReturnValue("alice");
  });

  it("shows only resources whose segment is unknown or null", () => {
    mockResources.mockReturnValue({
      data: [resource(null), resource(7)],
      isLoading: false,
    } as never);

    render(
      <MemoryRouter>
        <SessionOrphanResources
          slug="eng"
          sessionId={100}
          knownSegmentIds={[7]}
        />
      </MemoryRouter>,
    );

    expect(screen.getByText("Legacy deck")).toBeInTheDocument();
    expect(screen.queryByText("Live deck")).not.toBeInTheDocument();
  });

  it("renders nothing when there are no orphans", () => {
    mockResources.mockReturnValue({
      data: [resource(7)],
      isLoading: false,
    } as never);

    const { container } = render(
      <MemoryRouter>
        <SessionOrphanResources
          slug="eng"
          sessionId={100}
          knownSegmentIds={[7]}
        />
      </MemoryRouter>,
    );

    expect(container).toBeEmptyDOMElement();
  });

  it("detaches a session-level row by artifact, with no segment", async () => {
    const user = userEvent.setup({ delay: null });
    const mutate = vi.fn();
    mockDetach.mockReturnValue({ mutate } as never);
    mockResources.mockReturnValue({
      data: [resource(null)],
      isLoading: false,
    } as never);

    render(
      <MemoryRouter>
        <SessionOrphanResources
          slug="eng"
          sessionId={100}
          knownSegmentIds={[7]}
        />
      </MemoryRouter>,
    );
    await user.click(screen.getByRole("button", { name: /detach/i }));

    expect(mutate).toHaveBeenCalledWith({ artifactId: 1, segmentId: null });
  });
});
