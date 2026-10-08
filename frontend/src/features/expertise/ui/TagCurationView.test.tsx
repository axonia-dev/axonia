import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  useCurationSuggestions,
  useCurationVocabulary,
} from "@/features/expertise/api/use-curation";
import type { TagUsage } from "@/features/expertise/domain/expertise";
import type { MergeableTagCard } from "@/features/expertise/ui/MergeableTagCard";
import { TagCurationView } from "@/features/expertise/ui/TagCurationView";

vi.mock("@/features/expertise/api/use-curation", () => ({
  useCurationSuggestions: vi.fn(),
  useCurationVocabulary: vi.fn(),
}));

vi.mock("@/features/expertise/ui/NamedAreaPanel", () => ({
  NamedAreaPanel: () => null,
}));
vi.mock("@/features/expertise/ui/MergeSuggestionCard", () => ({
  MergeSuggestionCard: ({
    onMerge,
    onDismiss,
  }: {
    onMerge: (t: { variant: string; canonical: string }) => void;
    onDismiss: (p: { tagA: string; tagB: string }) => void;
  }) => (
    <div>
      <button
        type="button"
        data-testid="merge-card"
        onClick={() => onMerge({ variant: "hooks", canonical: "react-hooks" })}
      >
        merge
      </button>
      <button
        type="button"
        data-testid="dismiss-card"
        onClick={() => onDismiss({ tagA: "react-hooks", tagB: "hooks" })}
      >
        not a match
      </button>
    </div>
  ),
}));

// The real TagCard minus dnd-kit: a stub would prove nothing about the rename and delete flows.
vi.mock("@/features/expertise/ui/MergeableTagCard", async () => {
  const { TagCard } = await import("@/features/expertise/ui/TagCard");
  return {
    MergeableTagCard: ({
      area,
      selected,
      onSplit,
      onToggleSelect,
      onRename,
      onDelete,
    }: Parameters<typeof MergeableTagCard>[0]) => (
      <TagCard
        canonicalTag={area.canonicalTag}
        variants={area.variants}
        onSplit={onSplit}
        selected={selected}
        onToggleSelect={onToggleSelect}
        onRename={onRename}
        onDelete={onDelete}
      />
    ),
  };
});

const mergeIntoSpy = vi.fn();
const splitOffSpy = vi.fn();

vi.mock("@/features/expertise/api/use-tag-merge", () => ({
  useTagMerge: () => ({ mergeInto: mergeIntoSpy, splitOff: splitOffSpy }),
}));

const dismissPairSpy = vi.fn();

vi.mock("@/features/expertise/api/use-tag-dismissal", () => ({
  useTagDismissal: () => ({ dismissPair: dismissPairSpy }),
}));

const renameTagSpy = vi.fn();
const deleteTagSpy = vi.fn();
const USAGE: TagUsage = {
  artifacts: 3,
  topics: 2,
  talks: 0,
  declarations: 1,
  learningIntents: 0,
};

vi.mock("@/features/expertise/api/use-tag-lifecycle", () => ({
  useTagLifecycle: () => ({
    renameTag: renameTagSpy,
    deleteTag: deleteTagSpy,
    isPending: false,
  }),
  useTagUsage: () => ({
    data: USAGE,
    isSuccess: true,
    isError: false,
  }),
}));

const mockSuggestions = vi.mocked(useCurationSuggestions);
const mockVocabulary = vi.mocked(useCurationVocabulary);

function renderView() {
  render(<TagCurationView slug="eng" />);
}

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.clearAllMocks();
  mockSuggestions.mockReturnValue({
    data: [{ tagA: "react-hooks", tagB: "hooks", score: 0.92 }],
    isLoading: false,
  } as never);
  mockVocabulary.mockReturnValue({
    data: [
      { canonicalTag: "ok", variants: [], peopleCount: 1 },
      { canonicalTag: "yes", variants: [], peopleCount: 3 },
      {
        canonicalTag: "domain-driven-design",
        variants: ["ddd"],
        peopleCount: 5,
      },
      { canonicalTag: "kafka", variants: [], peopleCount: 4 },
    ],
    isLoading: false,
  } as never);
});

describe("TagCurationView", () => {
  it("opens on its page title, like the other community pages", () => {
    renderView();
    expect(
      screen.getByRole("heading", { level: 1, name: "Curation" }),
    ).toBeInTheDocument();
  });

  it("shows a single loading state while either query loads", () => {
    mockVocabulary.mockReturnValue({
      data: undefined,
      isLoading: true,
    } as never);
    renderView();
    expect(screen.getByText(/loading…/i)).toBeInTheDocument();
  });

  it("celebrates a tidy state when there are no merge suggestions", () => {
    mockSuggestions.mockReturnValue({ data: [], isLoading: false } as never);
    renderView();
    expect(
      screen.getByText(/no duplicate tags to review/i),
    ).toBeInTheDocument();
  });

  it("lists all tags with a count of canonical tags", () => {
    renderView();
    expect(screen.getByText("All tags")).toBeInTheDocument();
    expect(screen.getByText("· 4")).toBeInTheDocument();
    expect(
      screen.getAllByRole("button", { name: /more actions for/i }),
    ).toHaveLength(4);
  });

  it("lists a name no search would return, so a curator can still act on it", () => {
    mockVocabulary.mockReturnValue({
      data: [{ canonicalTag: "cobol", variants: [], peopleCount: 0 }],
      isLoading: false,
    } as never);
    renderView();
    expect(
      screen.getByRole("button", { name: "More actions for cobol" }),
    ).toBeInTheDocument();
  });

  it("shows an empty state when there are no tags", () => {
    mockVocabulary.mockReturnValue({ data: [], isLoading: false } as never);
    renderView();
    expect(screen.getByText(/no tags yet/i)).toBeInTheDocument();
  });

  it("merges from the attention queue immediately, no confirm modal", async () => {
    renderView();
    await user.click(screen.getByTestId("merge-card"));
    expect(mergeIntoSpy).toHaveBeenCalledWith(["hooks"], "react-hooks");
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("dismisses a suggestion immediately when 'Not a match' is pressed", async () => {
    renderView();
    await user.click(screen.getByTestId("dismiss-card"));
    expect(dismissPairSpy).toHaveBeenCalledWith("react-hooks", "hooks");
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("splits a tag immediately with its canonical, no confirm modal", async () => {
    renderView();
    await user.click(
      screen.getByRole("button", {
        name: /split ddd out of domain-driven-design/i,
      }),
    );
    expect(splitOffSpy).toHaveBeenCalledWith("ddd", "domain-driven-design");
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("merges the selected tags into the survivor via the batch bar", async () => {
    renderView();
    await user.click(screen.getByRole("checkbox", { name: /select ok/i }));
    await user.click(screen.getByRole("checkbox", { name: /select yes/i }));
    await user.click(screen.getByRole("button", { name: /^Merge$/ }));
    expect(mergeIntoSpy).toHaveBeenCalledWith(["ok"], "yes");
  });
});

describe("TagCurationView tag lifecycle", () => {
  async function openMenuFor(tag: string) {
    await user.click(
      screen.getByRole("button", { name: `More actions for ${tag}` }),
    );
  }

  it("offers rename and delete on a standalone tag", async () => {
    renderView();

    await openMenuFor("kafka");

    expect(screen.getByRole("menuitem", { name: "Rename" })).toBeEnabled();
    expect(screen.getByRole("menuitem", { name: "Delete" })).toBeEnabled();
  });

  it("will not rename or delete a tag that has variants merged into it", async () => {
    renderView();

    await openMenuFor("domain-driven-design");

    for (const name of ["Rename", "Delete"]) {
      expect(screen.getByRole("menuitem", { name })).toHaveAttribute(
        "aria-disabled",
        "true",
      );
    }
    expect(screen.getByText(/split its variants first/i)).toBeInTheDocument();
  });

  it("renames a tag from the card's menu", async () => {
    renderView();

    await openMenuFor("kafka");
    await user.click(screen.getByRole("menuitem", { name: "Rename" }));
    const field = screen.getByRole("textbox", { name: /new name/i });
    await user.clear(field);
    await user.type(field, "Apache Kafka");
    await user.click(screen.getByRole("button", { name: "Rename" }));

    expect(renameTagSpy).toHaveBeenCalledWith("kafka", "apache-kafka");
    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
  });

  it("deletes a tag from the card's menu, once its usage is known", async () => {
    renderView();

    await openMenuFor("kafka");
    await user.click(screen.getByRole("menuitem", { name: "Delete" }));
    expect(
      screen.getByText(
        /strips kafka from 3 artifacts, 2 topics and 1 member’s declarations\./i,
      ),
    ).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Delete" }));

    expect(deleteTagSpy).toHaveBeenCalledWith("kafka");
    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
  });

  it.each([
    ["Rename", renameTagSpy],
    ["Delete", deleteTagSpy],
  ])("closes the %s dialog untouched when cancelled", async (verb, spy) => {
    renderView();

    await openMenuFor("kafka");
    await user.click(screen.getByRole("menuitem", { name: verb }));
    await user.click(screen.getByRole("button", { name: "Cancel" }));

    expect(spy).not.toHaveBeenCalled();
    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
  });
});
