import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useExpertiseMap } from "@/features/expertise/api/use-expertise-map";
import {
  useNamedAreaActions,
  useNamedAreas,
} from "@/features/expertise/api/use-named-areas";
import { groupsFor } from "@/features/expertise/ui/named-area-grouping";
import {
  actions,
  mapResult,
  namedAreasResult,
  renderPanel,
  STREAMING,
} from "@/features/expertise/ui/named-area-panel-fixture";

vi.mock("@/features/expertise/api/use-named-areas", () => ({
  useNamedAreas: vi.fn(),
  useNamedAreaActions: vi.fn(),
}));
vi.mock("@/features/expertise/api/use-expertise-map", () => ({
  useExpertiseMap: vi.fn(),
}));
vi.mock("@/features/expertise/ui/named-area-grouping", () => ({
  groupsFor: vi.fn(),
}));

let user: ReturnType<typeof userEvent.setup>;

function card() {
  return screen.getByRole("region", { name: "Streaming" });
}

beforeEach(() => {
  vi.clearAllMocks();
  user = userEvent.setup({ delay: null });
  vi.mocked(useNamedAreaActions).mockReturnValue(actions);
  vi.mocked(useNamedAreas).mockReturnValue(namedAreasResult([STREAMING]));
  vi.mocked(useExpertiseMap).mockReturnValue(mapResult(true));
  vi.mocked(groupsFor).mockReturnValue([]);
});

describe("NamedAreaPanel actions", () => {
  it("places a tag in a named area", async () => {
    // given
    renderPanel();

    // when
    await user.type(within(card()).getByRole("combobox"), "redis");
    await user.click(await screen.findByRole("option", { name: "redis" }));

    // then
    expect(actions.placeInNamedArea).toHaveBeenCalledWith(1, "redis");
  });

  it("takes a tag out of a named area", async () => {
    // given
    renderPanel();

    // when
    await user.click(
      within(card()).getByRole("button", {
        name: "Take kafka out of Streaming",
      }),
    );

    // then
    expect(actions.removeFromNamedArea).toHaveBeenCalledWith("kafka");
  });

  it("renames a named area, starting from its current name", async () => {
    // given
    renderPanel();

    // when
    await user.click(
      within(card()).getByRole("button", { name: "Rename Streaming" }),
    );
    const dialog = screen.getByRole("dialog", { name: "Rename Streaming" });
    const field = within(dialog).getByRole("textbox");
    expect(field).toHaveFocus();
    expect(field).toHaveValue("Streaming");
    expect(
      within(dialog).getByRole("button", { name: "Rename" }),
    ).toBeDisabled();
    await user.clear(field);
    await user.type(field, "Streams");
    await user.click(within(dialog).getByRole("button", { name: "Rename" }));

    // then
    expect(actions.renameNamedArea).toHaveBeenCalledWith(1, "Streams");
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("asks before deleting, and says the tags go back to automatic grouping", async () => {
    // given
    renderPanel();

    // when
    await user.click(
      within(card()).getByRole("button", { name: "Delete Streaming" }),
    );

    // then
    const dialog = screen.getByRole("alertdialog", {
      name: "Delete Streaming?",
    });
    expect(
      within(dialog).getByText(
        "Its tags go back to being grouped automatically.",
      ),
    ).toBeInTheDocument();
    expect(actions.deleteNamedArea).not.toHaveBeenCalled();
  });

  it("deletes once confirmed", async () => {
    // given
    renderPanel();
    await user.click(
      within(card()).getByRole("button", { name: "Delete Streaming" }),
    );

    // when
    await user.click(screen.getByRole("button", { name: "Delete" }));

    // then
    expect(actions.deleteNamedArea).toHaveBeenCalledWith(STREAMING);
    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
  });

  it("keeps the named area when the delete is cancelled", async () => {
    // given
    renderPanel();
    await user.click(
      within(card()).getByRole("button", { name: "Delete Streaming" }),
    );

    // when
    await user.click(screen.getByRole("button", { name: "Cancel" }));

    // then
    expect(actions.deleteNamedArea).not.toHaveBeenCalled();
    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
  });

  it("says so when a named area holds no tags", () => {
    // given
    vi.mocked(useNamedAreas).mockReturnValue(
      namedAreasResult([{ ...STREAMING, tags: [] }]),
    );

    // when
    renderPanel();

    // then
    expect(within(card()).getByText("Holds no tags.")).toBeInTheDocument();
  });
});
