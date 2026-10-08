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
  LINKS,
  mapResult,
  namedAreasResult,
  renderPanel,
  STREAMING,
  TOPICS,
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

beforeEach(() => {
  vi.clearAllMocks();
  user = userEvent.setup({ delay: null });
  vi.mocked(useNamedAreaActions).mockReturnValue(actions);
  vi.mocked(useNamedAreas).mockReturnValue(namedAreasResult([]));
  vi.mocked(useExpertiseMap).mockReturnValue(mapResult(true));
  vi.mocked(groupsFor).mockReturnValue([
    {
      tags: ["kafka", "flink", "postgres"],
      anchor: "kafka",
      name: "around kafka",
      kind: "neighbourhood",
      slot: 0,
    },
  ]);
});

describe("NamedAreaPanel, before any named area exists", () => {
  it("offers to start from what the map found", () => {
    // when
    renderPanel();

    // then
    expect(
      screen.getByRole("heading", { name: "Named areas" }),
    ).toBeInTheDocument();
    expect(screen.getByText("No named areas")).toBeInTheDocument();
    expect(
      screen.getByRole("button", {
        name: "Start from the kafka neighbourhood",
      }),
    ).toBeInTheDocument();
    expect(vi.mocked(groupsFor)).toHaveBeenCalledWith(TOPICS, LINKS, []);
  });

  it("starts a named area pre-filled with a neighbourhood's members", async () => {
    // given
    renderPanel();

    // when
    await user.click(
      screen.getByRole("button", {
        name: "Start from the kafka neighbourhood",
      }),
    );
    const dialog = screen.getByRole("dialog", { name: "New named area" });
    expect(within(dialog).getByRole("textbox")).toHaveFocus();
    expect(
      within(dialog).getByText("Holds kafka, flink and postgres."),
    ).toBeInTheDocument();
    await user.type(within(dialog).getByRole("textbox"), "  Streaming ");
    await user.click(within(dialog).getByRole("button", { name: "Create" }));

    // then
    expect(actions.createNamedArea).toHaveBeenCalledWith("Streaming", [
      "kafka",
      "flink",
      "postgres",
    ]);
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("offers no starting point when the map found no neighbourhood", () => {
    // given
    vi.mocked(groupsFor).mockReturnValue([]);

    // when
    renderPanel();

    // then
    expect(
      screen.queryByRole("button", { name: /start from/i }),
    ).not.toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "New named area" }),
    ).toBeInTheDocument();
  });

  it("offers no starting point before the map loads", () => {
    // given
    vi.mocked(useExpertiseMap).mockReturnValue(mapResult(false));

    // when
    renderPanel();

    // then
    expect(vi.mocked(groupsFor)).not.toHaveBeenCalled();
    expect(
      screen.queryByRole("button", { name: /start from/i }),
    ).not.toBeInTheDocument();
  });

  it("creates an empty named area from a typed name", async () => {
    // given
    renderPanel();

    // when
    await user.click(screen.getByRole("button", { name: "New named area" }));
    const dialog = screen.getByRole("dialog", { name: "New named area" });
    const create = within(dialog).getByRole("button", { name: "Create" });
    expect(within(dialog).getByRole("textbox")).toHaveFocus();
    expect(create).toBeDisabled();
    await user.type(within(dialog).getByRole("textbox"), "Data");
    await user.click(create);

    // then
    expect(actions.createNamedArea).toHaveBeenCalledWith("Data", []);
  });

  it("creates on Enter", async () => {
    // given
    renderPanel();
    await user.click(screen.getByRole("button", { name: "New named area" }));
    const dialog = screen.getByRole("dialog", { name: "New named area" });

    // when
    await user.type(within(dialog).getByRole("textbox"), "Data{Enter}");

    // then
    expect(actions.createNamedArea).toHaveBeenCalledWith("Data", []);
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("ignores Enter on a blank name", async () => {
    // given
    renderPanel();
    await user.click(screen.getByRole("button", { name: "New named area" }));
    const dialog = screen.getByRole("dialog", { name: "New named area" });

    // when
    await user.type(within(dialog).getByRole("textbox"), "  {Enter}");

    // then
    expect(actions.createNamedArea).not.toHaveBeenCalled();
    expect(dialog).toBeInTheDocument();
  });

  it("refuses a name longer than 80 characters", async () => {
    // given
    renderPanel();
    await user.click(screen.getByRole("button", { name: "New named area" }));
    const dialog = screen.getByRole("dialog", { name: "New named area" });

    // when
    await user.type(within(dialog).getByRole("textbox"), "x".repeat(81));

    // then
    expect(
      within(dialog).getByText("A name can be at most 80 characters."),
    ).toBeInTheDocument();
    expect(
      within(dialog).getByRole("button", { name: "Create" }),
    ).toBeDisabled();
  });

  it("closes the create dialog without creating when cancelled", async () => {
    // given
    renderPanel();
    await user.click(screen.getByRole("button", { name: "New named area" }));

    // when
    await user.click(screen.getByRole("button", { name: "Cancel" }));

    // then
    expect(actions.createNamedArea).not.toHaveBeenCalled();
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("shows nothing while the named areas load", () => {
    // given
    vi.mocked(useNamedAreas).mockReturnValue(namedAreasResult(undefined));

    // when
    renderPanel();

    // then
    expect(screen.queryByText("No named areas")).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /start from/i }),
    ).not.toBeInTheDocument();
  });
});

describe("NamedAreaPanel, when the named areas fail to load", () => {
  it("says so in place of the list", () => {
    // given
    vi.mocked(useNamedAreas).mockReturnValue({
      data: undefined,
      isError: true,
    } as never);

    // when
    renderPanel();

    // then
    expect(screen.getByRole("alert")).toHaveTextContent(
      "Could not load named areas.",
    );
    expect(screen.queryByText("No named areas")).not.toBeInTheDocument();
  });

  it("keeps a list already shown when only a refetch fails", () => {
    // given
    vi.mocked(useNamedAreas).mockReturnValue({
      data: [STREAMING],
      isSuccess: false,
      isError: true,
    } as never);

    // when
    renderPanel();

    // then
    expect(
      screen.getByRole("region", { name: "Streaming" }),
    ).toBeInTheDocument();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });
});

describe("NamedAreaPanel, with a named area", () => {
  beforeEach(() => {
    vi.mocked(useNamedAreas).mockReturnValue(namedAreasResult([STREAMING]));
  });

  it("lists each stored tag, saying which one the map cannot draw", () => {
    // when
    renderPanel();

    // then
    const card = screen.getByRole("region", { name: "Streaming" });
    expect(within(card).getByText("kafka")).toBeInTheDocument();
    expect(within(card).getByText("cobol")).toBeInTheDocument();
    expect(within(card).getAllByText("not on the map")).toHaveLength(1);
    expect(screen.queryByText("No named areas")).not.toBeInTheDocument();
  });

  it("marks no tag off the map before the map loads", () => {
    // given
    vi.mocked(useExpertiseMap).mockReturnValue(mapResult(false));

    // when
    renderPanel();

    // then
    expect(screen.queryByText("not on the map")).not.toBeInTheDocument();
  });

  it("never counts what is left ungrouped", () => {
    // when
    renderPanel();

    // then
    expect(screen.queryByRole("progressbar")).not.toBeInTheDocument();
    expect(screen.queryByRole("meter")).not.toBeInTheDocument();
    expect(document.body.textContent).not.toMatch(
      /\d (of|\/) \d|%|unplaced|ungrouped|remaining|left to/i,
    );
  });
});
