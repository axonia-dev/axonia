import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import {
  MasterDetailManager,
  type MdSelection,
} from "@/components/patterns/MasterDetailManager";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

interface Item {
  id: number;
  label: string;
}

const items: Item[] = [
  { id: 1, label: "Alpha" },
  { id: 2, label: "Beta" },
];

type Props = Parameters<typeof MasterDetailManager<Item>>[0];

function setup(overrides: Partial<Props> = {}) {
  const props: Props = {
    items,
    getId: (i) => i.id,
    noun: "thing",
    canWrite: true,
    selection: null as MdSelection,
    onSelect: vi.fn(),
    renderRowContent: (i) => <span>{i.label}</span>,
    renderDetail: (current, isDraft) => (
      <div>{isDraft ? "draft editor" : `editing ${current?.label ?? "?"}`}</div>
    ),
    detailTitle: (current, isDraft) =>
      isDraft ? "New thing" : (current?.label ?? ""),
    ...overrides,
  };
  render(<MasterDetailManager<Item> {...props} />);
  return props;
}

const originalMatchMedia = window.matchMedia;

function forceMobile() {
  window.matchMedia = vi.fn().mockReturnValue({
    matches: false,
    media: "",
    onchange: null,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    addListener: vi.fn(),
    removeListener: vi.fn(),
    dispatchEvent: vi.fn(),
  });
}

afterEach(() => {
  window.matchMedia = originalMatchMedia;
});

describe("MasterDetailManager", () => {
  it("lists the items", () => {
    // given / when
    setup();

    // then
    expect(screen.getByRole("button", { name: "Alpha" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Beta" })).toBeInTheDocument();
  });

  it("selects an item on click", async () => {
    // given
    const { onSelect } = setup();

    // when
    await user.click(screen.getByRole("button", { name: "Alpha" }));

    // then
    expect(onSelect).toHaveBeenCalledWith({ kind: "item", id: 1 });
  });

  it("marks the selected row as pressed", () => {
    // given / when
    setup({ selection: { kind: "item", id: 2 } });

    // then
    expect(screen.getByRole("button", { name: "Beta" })).toHaveAttribute(
      "aria-pressed",
      "true",
    );
  });

  it("starts a draft from the new button", async () => {
    // given
    const { onSelect } = setup();

    // when
    await user.click(screen.getByRole("button", { name: "+ New thing" }));

    // then
    expect(onSelect).toHaveBeenCalledWith({ kind: "draft" });
  });

  it("renders the selected detail inline on desktop", () => {
    // given / when
    setup({ selection: { kind: "item", id: 1 } });

    // then
    expect(screen.getByText("editing Alpha")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Alpha" })).toBeInTheDocument();
  });

  it("prompts to pick an item when nothing is selected", () => {
    // given / when
    setup();

    // then
    expect(
      screen.getByText("Select a thing to edit it, or create a new one."),
    ).toBeInTheDocument();
  });

  it("shows a read-only prompt and hides the new button without write access", () => {
    // given / when
    setup({ canWrite: false, selection: { kind: "item", id: 1 } });

    // then
    expect(screen.getByText("Select a thing to view it.")).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "+ New thing" }),
    ).not.toBeInTheDocument();
  });

  it("renders the detail in a sheet on mobile", () => {
    // given a viewport below the md breakpoint
    forceMobile();

    // when
    setup({ selection: { kind: "item", id: 1 } });

    // then
    const dialog = screen.getByRole("dialog");
    expect(within(dialog).getByText("editing Alpha")).toBeInTheDocument();
  });

  it("clears the selection when the mobile sheet is dismissed", async () => {
    // given a selected item on mobile
    forceMobile();
    const { onSelect } = setup({ selection: { kind: "item", id: 1 } });

    // when the sheet is dismissed
    await user.keyboard("{Escape}");

    // then
    expect(onSelect).toHaveBeenCalledWith(null);
  });
});
