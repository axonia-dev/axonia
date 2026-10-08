import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { AddResourceButton } from "@/features/knowledge/ui/AddResourceButton";

vi.mock("@/features/knowledge/api/use-session-resources", () => ({
  useAttachResource: vi.fn(),
  useSessionResources: vi.fn(),
}));

// Stub the panel: expose buttons that drive the orchestrator's callbacks.
vi.mock("@/features/knowledge/ui/LibrarySearchPanel", () => ({
  LibrarySearchPanel: (props: {
    attachedArtifactIds: number[];
    attachingArtifactId: number | null;
    onAttach: (id: number) => void;
    onCreate: () => void;
  }) => (
    <div data-testid="search-panel">
      <span data-testid="panel-attached">
        {props.attachedArtifactIds.join(",")}
      </span>
      <span data-testid="panel-attaching">
        {props.attachingArtifactId ?? "none"}
      </span>
      <button type="button" onClick={() => props.onAttach(9)}>
        attach-9
      </button>
      <button type="button" onClick={() => props.onCreate()}>
        go-create
      </button>
    </div>
  ),
}));

// Stub the form: expose the props we orchestrate + a trigger to fire onCaptured.
vi.mock("@/features/knowledge/ui/CaptureArtifactForm", () => ({
  CaptureArtifactForm: (props: {
    defaultTitle?: string;
    defaultUrl?: string;
    submitLabel?: string;
    onBack?: () => void;
    onCaptured: (artifact: { id: number }) => void;
  }) => (
    <div data-testid="capture-form">
      <span data-testid="form-default-title">{props.defaultTitle}</span>
      <span data-testid="form-default-url">{props.defaultUrl}</span>
      <span data-testid="form-submit-label">{props.submitLabel}</span>
      <button type="button" onClick={() => props.onBack?.()}>
        form-back
      </button>
      <button type="button" onClick={() => props.onCaptured({ id: 42 })}>
        form-capture
      </button>
    </div>
  ),
}));

import {
  useAttachResource,
  useSessionResources,
} from "@/features/knowledge/api/use-session-resources";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

const mockAttach = vi.mocked(useAttachResource);
const mockResources = vi.mocked(useSessionResources);

function setResources(
  resources: Array<{ artifactId: number; segmentId: number | null }>,
) {
  mockResources.mockReturnValue({
    data: resources.map((r) => ({
      attachmentId: r.artifactId * 10,
      artifact: { id: r.artifactId },
      segmentId: r.segmentId,
      attachedByUserId: "u1",
    })),
  } as never);
}

function renderButton() {
  return render(
    <AddResourceButton
      slug="eng"
      sessionId={100}
      segmentId={7}
      defaultTitle="Ice Breaker"
      provenanceLabel="Ice Breaker"
    />,
  );
}

describe("AddResourceButton", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockAttach.mockReturnValue({ mutate: vi.fn() } as never);
    setResources([]);
  });

  it("tells the panel which artifacts this slot already holds", async () => {
    setResources([
      { artifactId: 3, segmentId: 7 },
      { artifactId: 4, segmentId: 8 },
      { artifactId: 5, segmentId: null },
    ]);
    renderButton();

    await user.click(screen.getByRole("button", { name: /add resource/i }));

    // only this slot's segment counts: not a sibling slot's, not the session's own
    expect(screen.getByTestId("panel-attached")).toHaveTextContent("3");
    expect(screen.getByTestId("panel-attaching")).toHaveTextContent("none");
  });

  it("session level: counts the session's own resources, not any slot's", async () => {
    setResources([
      { artifactId: 3, segmentId: 7 },
      { artifactId: 5, segmentId: null },
    ]);

    render(<AddResourceButton slug="eng" sessionId={100} />);

    await user.click(screen.getByRole("button", { name: /add resource/i }));

    expect(screen.getByTestId("panel-attached")).toHaveTextContent("5");
  });

  it("tells the panel which artifact is in flight while the attach runs", async () => {
    mockAttach.mockReturnValue({
      mutate: vi.fn(),
      isPending: true,
      variables: { artifactId: 9, segmentId: 7 },
    } as never);
    renderButton();

    await user.click(screen.getByRole("button", { name: /add resource/i }));

    expect(screen.getByTestId("panel-attaching")).toHaveTextContent("9");
  });

  it("opens the dialog on the search view", async () => {
    renderButton();
    expect(screen.queryByRole("dialog")).toBeNull();

    await user.click(screen.getByRole("button", { name: /add resource/i }));

    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(screen.getByTestId("search-panel")).toBeInTheDocument();
  });

  it("attaches an existing artifact and keeps the dialog open", async () => {
    const mutate = vi.fn();
    mockAttach.mockReturnValue({ mutate } as never);
    renderButton();

    await user.click(screen.getByRole("button", { name: /add resource/i }));
    await user.click(screen.getByRole("button", { name: "attach-9" }));

    expect(mutate).toHaveBeenCalledWith(
      { segmentId: 7, artifactId: 9 },
      expect.anything(),
    );
    expect(screen.getByRole("dialog")).toBeInTheDocument();
  });

  it("switches to a prefilled create form", async () => {
    renderButton();

    await user.click(screen.getByRole("button", { name: /add resource/i }));
    await user.click(screen.getByRole("button", { name: "go-create" }));

    expect(screen.getByTestId("capture-form")).toBeInTheDocument();
    expect(screen.getByTestId("form-submit-label")).toHaveTextContent("Attach");
    // empty query falls back to the slot default title
    expect(screen.getByTestId("form-default-title")).toHaveTextContent(
      "Ice Breaker",
    );
  });

  it("create then attach: captures, attaches the new artifact, and closes", async () => {
    const mutate = vi.fn((_input, opts?: { onSuccess?: () => void }) =>
      opts?.onSuccess?.(),
    );
    mockAttach.mockReturnValue({ mutate } as never);
    renderButton();

    await user.click(screen.getByRole("button", { name: /add resource/i }));
    await user.click(screen.getByRole("button", { name: "go-create" }));
    await user.click(screen.getByRole("button", { name: "form-capture" }));

    expect(mutate).toHaveBeenCalledWith(
      { segmentId: 7, artifactId: 42 },
      expect.anything(),
    );
    expect(screen.queryByRole("dialog")).toBeNull();
  });

  it("reopens on the search view after a create closes the dialog", async () => {
    const mutate = vi.fn((_input, opts?: { onSuccess?: () => void }) =>
      opts?.onSuccess?.(),
    );
    mockAttach.mockReturnValue({ mutate } as never);
    renderButton();

    await user.click(screen.getByRole("button", { name: /add resource/i }));
    await user.click(screen.getByRole("button", { name: "go-create" }));
    await user.click(screen.getByRole("button", { name: "form-capture" }));
    // dialog closed — reopen
    await user.click(screen.getByRole("button", { name: /add resource/i }));

    expect(screen.getByTestId("search-panel")).toBeInTheDocument();
    expect(screen.queryByTestId("capture-form")).toBeNull();
  });

  it("returns from create to search via back", async () => {
    renderButton();

    await user.click(screen.getByRole("button", { name: /add resource/i }));
    await user.click(screen.getByRole("button", { name: "go-create" }));
    await user.click(screen.getByRole("button", { name: "form-back" }));

    expect(screen.getByTestId("search-panel")).toBeInTheDocument();
    expect(screen.queryByTestId("capture-form")).toBeNull();
  });

  it("renders the launcher as a quiet accent link, not a solid primary button", () => {
    renderButton();
    const trigger = screen.getByRole("button", { name: /add resource/i });
    expect(trigger.className).toContain("text-primary");
    expect(trigger.className).not.toContain("bg-primary");
  });

  it("session-level attach: passes only artifactId when segmentId is absent", async () => {
    const mutate = vi.fn();
    mockAttach.mockReturnValue({ mutate } as never);

    render(
      <AddResourceButton
        slug="eng"
        sessionId={100}
        defaultTitle="June dojo"
        provenanceLabel="June dojo"
      />,
    );

    await user.click(screen.getByRole("button", { name: /add resource/i }));
    await user.click(screen.getByRole("button", { name: "attach-9" }));

    expect(mutate).toHaveBeenCalledWith({ artifactId: 9 }, expect.anything());
  });

  it("threads defaultUrl into the create form", async () => {
    mockAttach.mockReturnValue({ mutate: vi.fn() } as never);

    render(
      <AddResourceButton
        slug="eng"
        sessionId={100}
        defaultTitle="June dojo"
        defaultUrl="https://example.com/rec"
      />,
    );

    await user.click(screen.getByRole("button", { name: /add resource/i }));
    await user.click(screen.getByRole("button", { name: "go-create" }));

    expect(screen.getByTestId("form-default-url")).toHaveTextContent(
      "https://example.com/rec",
    );
  });
});
