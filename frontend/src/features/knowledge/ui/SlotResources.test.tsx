import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { SlotResources } from "@/features/knowledge/ui/SlotResources";

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

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

const mockResources = vi.mocked(useSessionResources);
const mockDetach = vi.mocked(useDetachResource);
const mockUser = vi.mocked(currentUserId);

function resource(over: Partial<Record<string, unknown>> = {}) {
  return {
    attachmentId: 5,
    segmentId: 7,
    attachedByUserId: "alice",
    artifact: {
      id: 9,
      communityId: 1,
      typeId: 4,
      type: "Slides",
      title: "React 19 deck",
      description: "",
      url: "https://x.dev/deck",
      tags: [],
      contributorUserId: "alice",
      loveCount: 0,
      provenance: null,
      status: "ACTIVE",
      createdAt: "x",
      updatedAt: "x",
    },
    ...over,
  };
}

describe("SlotResources", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockDetach.mockReturnValue({ mutate: vi.fn() } as never);
    mockUser.mockReturnValue("alice");
  });

  it("renders the resources attached to its segment", () => {
    mockResources.mockReturnValue({
      data: [resource(), resource({ segmentId: 8 })],
      isLoading: false,
    } as never);

    render(
      <MemoryRouter>
        <SlotResources slug="eng" sessionId={100} segmentId={7} />
      </MemoryRouter>,
    );

    expect(
      screen.getByRole("link", { name: "React 19 deck" }),
    ).toBeInTheDocument();
    // 1 artifact link + 1 "View in library" link per resource row
    expect(screen.getAllByRole("link")).toHaveLength(2);
  });

  it("renders nothing when the segment has no resources", () => {
    mockResources.mockReturnValue({ data: [], isLoading: false } as never);

    const { container } = render(
      <MemoryRouter>
        <SlotResources slug="eng" sessionId={100} segmentId={7} />
      </MemoryRouter>,
    );

    expect(container).toBeEmptyDOMElement();
  });

  it("detaches the attacher's own resource on press", async () => {
    const mutate = vi.fn();
    mockDetach.mockReturnValue({ mutate } as never);
    mockResources.mockReturnValue({
      data: [resource()],
      isLoading: false,
    } as never);

    render(
      <MemoryRouter>
        <SlotResources slug="eng" sessionId={100} segmentId={7} />
      </MemoryRouter>,
    );
    await user.click(screen.getByRole("button", { name: /detach/i }));

    expect(mutate).toHaveBeenCalledWith({ artifactId: 9, segmentId: 7 });
  });

  it("detaches a row shown only because the artifact came from this session", async () => {
    const mutate = vi.fn();
    mockDetach.mockReturnValue({ mutate } as never);
    mockResources.mockReturnValue({
      data: [resource({ attachmentId: null, attachedByUserId: null })],
      isLoading: false,
    } as never);

    render(
      <MemoryRouter>
        <SlotResources slug="eng" sessionId={100} segmentId={7} />
      </MemoryRouter>,
    );
    await user.click(screen.getByRole("button", { name: /detach/i }));

    expect(mutate).toHaveBeenCalledWith({ artifactId: 9, segmentId: 7 });
  });

  it("hides detach for a resource the user did not attach and cannot manage", () => {
    mockResources.mockReturnValue({
      data: [resource({ attachedByUserId: "bob" })],
      isLoading: false,
    } as never);

    render(
      <MemoryRouter>
        <SlotResources slug="eng" sessionId={100} segmentId={7} />
      </MemoryRouter>,
    );

    expect(
      screen.queryByRole("button", { name: /detach/i }),
    ).not.toBeInTheDocument();
  });
});
