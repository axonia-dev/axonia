import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { CommunityKind } from "@/features/community/domain/community-kind";
import { KindEditor } from "@/features/community/ui/KindEditor";
import { ApiError } from "@/lib/api-client";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

const others: CommunityKind[] = [
  { id: 2, label: "Chapter", communityCount: 0 },
];

function renderEditor(
  overrides: Partial<Parameters<typeof KindEditor>[0]> = {},
) {
  const props = {
    kindId: 1 as number | null,
    initialLabel: "Guild",
    communityCount: 0,
    others,
    onSave: vi.fn(),
    isSaving: false,
    saveError: null as unknown,
    deleteKind: vi.fn().mockResolvedValue(undefined),
    isDeleting: false,
    onDeleted: vi.fn(),
    ...overrides,
  };
  render(<KindEditor {...props} />);
  return props;
}

describe("KindEditor", () => {
  it("shows the usage count", () => {
    // given / when
    renderEditor({ communityCount: 7 });

    // then — the heading is owned by the surrounding manager, not the editor
    expect(screen.getByText(/Used by/)).toHaveTextContent(
      "Used by 7 communities.",
    );
  });

  it("saves the trimmed label", async () => {
    // given
    const { onSave } = renderEditor();

    // when
    const input = screen.getByLabelText("Label");
    await user.clear(input);
    await user.type(input, "  Tribe  ");
    await user.click(screen.getByRole("button", { name: "Save" }));

    // then
    expect(onSave).toHaveBeenCalledWith("Tribe");
  });

  it("deletes without reassignment when the kind is unused", async () => {
    // given
    const { deleteKind } = renderEditor({ communityCount: 0 });

    // when
    await user.click(screen.getByRole("button", { name: "Delete" }));
    const dialog = screen.getByRole("alertdialog");
    await user.click(within(dialog).getByRole("button", { name: "Delete" }));

    // then
    expect(deleteKind).toHaveBeenCalledWith({ id: 1 });
  });

  it("requires a reassignment target when the kind is in use", async () => {
    // given
    const { deleteKind } = renderEditor({ communityCount: 3 });

    // when — open the confirm dialog; the reassign picker is shown immediately
    await user.click(screen.getByRole("button", { name: "Delete" }));
    const dialog = screen.getByRole("alertdialog");
    await user.click(
      within(dialog).getByRole("button", { name: /move communities to/i }),
    );
    await user.click(screen.getByRole("option", { name: "Chapter" }));
    await user.click(within(dialog).getByRole("button", { name: "Delete" }));

    // then
    expect(deleteKind).toHaveBeenCalledWith({ id: 1, reassignToId: 2 });
  });

  it("reverts the label to its initial value", async () => {
    // given
    renderEditor({ initialLabel: "Guild" });
    const input = screen.getByLabelText("Label");
    await user.clear(input);
    await user.type(input, "Changed");

    // when
    await user.click(screen.getByRole("button", { name: "Revert" }));

    // then
    expect(input).toHaveValue("Guild");
  });

  it("cancels the delete dialog without deleting", async () => {
    // given
    const { deleteKind } = renderEditor({ communityCount: 0 });

    // when
    await user.click(screen.getByRole("button", { name: "Delete" }));
    const dialog = screen.getByRole("alertdialog");
    await user.click(within(dialog).getByRole("button", { name: "Cancel" }));

    // then
    expect(deleteKind).not.toHaveBeenCalled();
    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
  });

  it("reveals reassignment when the delete reports the kind is in use", async () => {
    // given the cached count was stale (0) but the server rejects as in-use
    const deleteKind = vi
      .fn()
      .mockRejectedValue(new ApiError(409, "in use", "KIND_IN_USE"));
    renderEditor({ communityCount: 0, deleteKind });

    // when
    await user.click(screen.getByRole("button", { name: "Delete" }));
    const dialog = screen.getByRole("alertdialog");
    await user.click(within(dialog).getByRole("button", { name: "Delete" }));

    // then — the reassignment picker now appears as a fallback
    expect(
      await within(dialog).findByRole("button", {
        name: /move communities to/i,
      }),
    ).toBeInTheDocument();
    expect(within(dialog).getByRole("alert")).toHaveTextContent(
      "Communities still use this kind",
    );
  });

  it("renders a draft with no delete affordance or usage count", () => {
    // given / when
    renderEditor({ kindId: null, initialLabel: "", communityCount: 0 });

    // then — a draft has nothing to delete and isn't used yet
    expect(
      screen.queryByRole("button", { name: "Delete" }),
    ).not.toBeInTheDocument();
    expect(screen.queryByText(/used by/i)).not.toBeInTheDocument();
  });

  it("surfaces a save error via the shared message map", () => {
    // given a KIND_EXISTS conflict — ApiError(status, message, code?)
    const saveError = new ApiError(409, "conflict", "KIND_EXISTS");

    // when
    renderEditor({ saveError });

    // then
    expect(screen.getByRole("alert")).toHaveTextContent(
      "That kind already exists in the vocabulary.",
    );
  });
});
