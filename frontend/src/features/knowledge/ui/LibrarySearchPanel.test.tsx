import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ComponentProps } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { Dialog } from "@/components/ui/Dialog";
import { LibrarySearchPanel } from "@/features/knowledge/ui/LibrarySearchPanel";

vi.mock("@/features/knowledge/api/use-artifacts", () => ({
  useArtifacts: vi.fn(),
}));

import { useArtifacts } from "@/features/knowledge/api/use-artifacts";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

const mockArtifacts = vi.mocked(useArtifacts);

function setResults(
  content: Array<{ id: number; title: string; type: string; typeId?: number }>,
) {
  mockArtifacts.mockReturnValue({
    data: {
      content: content.map((c) => ({ typeId: 4, ...c })),
      totalElements: content.length,
    },
  } as never);
}

function renderPanel(props: Partial<PanelProps> = {}) {
  return render(
    <LibrarySearchPanel
      slug="eng"
      query="react"
      attachedArtifactIds={[]}
      attachingArtifactId={null}
      onQueryChange={vi.fn()}
      onAttach={vi.fn()}
      onCreate={vi.fn()}
      {...props}
    />,
  );
}

type PanelProps = ComponentProps<typeof LibrarySearchPanel>;

describe("LibrarySearchPanel", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    setResults([{ id: 9, title: "React 19 deck", type: "Slides" }]);
  });

  it("takes focus when its dialog opens, so typing searches straight away", () => {
    render(
      <Dialog isOpen onOpenChange={vi.fn()} title="Add a resource">
        <LibrarySearchPanel
          slug="eng"
          query=""
          attachedArtifactIds={[]}
          attachingArtifactId={null}
          onQueryChange={vi.fn()}
          onAttach={vi.fn()}
          onCreate={vi.fn()}
        />
      </Dialog>,
    );

    expect(
      screen.getByRole("searchbox", { name: "Search the library" }),
    ).toHaveFocus();
  });

  it("attaches the picked artifact", async () => {
    const onAttach = vi.fn();
    renderPanel({ onAttach });

    await user.click(screen.getByRole("button", { name: /React 19 deck/i }));

    expect(onAttach).toHaveBeenCalledWith(9);
  });

  it("marks an artifact already attached to this slot, and stops offering it", async () => {
    const onAttach = vi.fn();
    renderPanel({ attachedArtifactIds: [9], onAttach });

    expect(screen.getByText("Attached")).toBeInTheDocument();
    expect(screen.queryByText("＋ Attach")).toBeNull();

    await user.click(screen.getByText("React 19 deck"));

    expect(onAttach).not.toHaveBeenCalled();
  });

  it("keeps the attached row focusable, so attaching never throws focus out of the dialog", () => {
    const { rerender } = renderPanel();
    const row = screen.getByRole("button", { name: /React 19 deck/i });
    row.focus();
    expect(row).toHaveFocus();

    // the attach lands: the row restates itself, but it must survive as the same focused node,
    // or the browser drops focus on <body> and the dialog stops answering Escape.
    rerender(
      <LibrarySearchPanel
        slug="eng"
        query="react"
        attachedArtifactIds={[9]}
        attachingArtifactId={null}
        onQueryChange={vi.fn()}
        onAttach={vi.fn()}
        onCreate={vi.fn()}
      />,
    );

    const attachedRow = screen.getByRole("button", { name: /React 19 deck/i });
    expect(attachedRow).toHaveFocus();
    expect(attachedRow).toHaveAttribute("aria-disabled", "true");
    expect(attachedRow).not.toBeDisabled();
  });

  it("sinks the attached artifacts below the ones still on offer", () => {
    setResults([
      { id: 9, title: "React 19 deck", type: "Slides" },
      { id: 10, title: "Signals talk", type: "Recording" },
      { id: 11, title: "Retro notes", type: "Notes" },
    ]);
    renderPanel({ attachedArtifactIds: [9, 11] });

    const titles = screen
      .getAllByRole("listitem")
      .map((row) => row.textContent);

    expect(titles[0]).toContain("Signals talk");
    expect(titles[1]).toContain("React 19 deck");
    expect(titles[2]).toContain("Retro notes");
  });

  it("shows the artifact being attached as in flight, and refuses a second click", async () => {
    const onAttach = vi.fn();
    renderPanel({ attachingArtifactId: 9, onAttach });

    expect(screen.getByText("Attaching…")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: /React 19 deck/i }));

    expect(onAttach).not.toHaveBeenCalled();
  });

  it("offers a create row carrying the typed query", async () => {
    const onCreate = vi.fn();
    renderPanel({ query: "retro", onCreate });

    const createRow = screen.getByRole("button", {
      name: /create .*retro.* new artifact/i,
    });
    await user.click(createRow);

    expect(onCreate).toHaveBeenCalledOnce();
  });

  it("shows a no-match message but still offers create", () => {
    setResults([]);
    renderPanel({ query: "nope" });

    expect(screen.getByText(/no matches in the library/i)).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /create .*new artifact/i }),
    ).toBeInTheDocument();
  });

  it("propagates query changes to the parent", async () => {
    const onQueryChange = vi.fn();
    setResults([]);
    renderPanel({ query: "", onQueryChange });

    await user.type(screen.getByRole("searchbox"), "r");

    expect(onQueryChange).toHaveBeenCalled();
  });

  it("prompts to type when the query is empty", () => {
    setResults([]);
    renderPanel({ query: "" });

    expect(screen.getByText(/type to search the library/i)).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /create a new artifact/i }),
    ).toBeInTheDocument();
  });

  it("shows a per-type icon and an accent attach affordance", () => {
    setResults([{ id: 9, title: "React 19 deck", type: "Slides", typeId: 4 }]);
    const { container } = renderPanel();

    expect(
      container.querySelector("span[aria-hidden][style*='--k']"),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: /React 19 deck/i }),
    ).toHaveTextContent(/Attach/);
  });
});
