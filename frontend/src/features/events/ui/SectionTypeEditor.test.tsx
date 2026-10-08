import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { SectionTypeEditor } from "@/features/events/ui/SectionTypeEditor";
import { ApiError } from "@/lib/api-client";

const OTHERS = [
  {
    id: 2,
    label: "Workshop",
    icon: null,
    accentColor: null,
    expectsPresenter: false,
  },
];

function defaultProps(overrides: Record<string, unknown> = {}) {
  return {
    typeId: 1,
    initialLabel: "Keynote",
    initialIcon: "presentation",
    initialAccent: "#de3b78",
    initialExpectsPresenter: true,
    others: OTHERS,
    onSave: vi.fn(),
    isSaving: false,
    saveError: null,
    deleteType: vi.fn().mockResolvedValue(undefined),
    isDeleting: false,
    onDeleted: vi.fn(),
    ...overrides,
  };
}

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.clearAllMocks();
});

describe("SectionTypeEditor", () => {
  it("renders the initial label", () => {
    // given / when
    render(<SectionTypeEditor {...defaultProps()} />);

    // then
    expect(screen.getByDisplayValue("Keynote")).toBeInTheDocument();
  });

  it("reflects initialExpectsPresenter in the toggle state", () => {
    // given / when — expectsPresenter starts true
    render(<SectionTypeEditor {...defaultProps()} />);

    // then
    expect(
      screen.getByRole("switch", { name: /expects presenter/i }),
    ).toBeChecked();
  });

  it("calls onSave with the current field values including expectsPresenter", async () => {
    // given
    const onSave = vi.fn();
    render(<SectionTypeEditor {...defaultProps({ onSave })} />);

    // when
    await user.click(screen.getByRole("button", { name: /^save$/i }));

    // then
    expect(onSave).toHaveBeenCalledWith({
      label: "Keynote",
      icon: "presentation",
      accentColor: "#de3b78",
      expectsPresenter: true,
    });
  });

  it("toggles expectsPresenter and includes updated value in onSave", async () => {
    // given
    const onSave = vi.fn();
    render(<SectionTypeEditor {...defaultProps({ onSave })} />);

    // when — click the toggle to flip it off
    const toggle = screen.getByRole("switch", { name: /expects presenter/i });
    await user.click(toggle);
    await user.click(screen.getByRole("button", { name: /^save$/i }));

    // then
    expect(onSave).toHaveBeenCalledWith(
      expect.objectContaining({ expectsPresenter: false }),
    );
  });

  it("revert restores expectsPresenter to its initial value", async () => {
    // given — start with expectsPresenter=true, toggle to false, then revert
    render(<SectionTypeEditor {...defaultProps()} />);
    const toggle = screen.getByRole("switch", { name: /expects presenter/i });
    await user.click(toggle);
    expect(toggle).not.toBeChecked();

    // when
    await user.click(screen.getByRole("button", { name: /revert/i }));

    // then
    expect(toggle).toBeChecked();
  });

  it("shows a duplicate-label error when saveError has SECTION_TYPE_EXISTS code", () => {
    // given
    const saveError = new ApiError(409, "exists", "SECTION_TYPE_EXISTS");
    render(<SectionTypeEditor {...defaultProps({ saveError })} />);

    // then
    expect(screen.getByRole("alert")).toHaveTextContent(
      "A type with that label already exists.",
    );
  });

  it("shows a generic error message for other save errors", () => {
    // given
    const saveError = new Error("network failure");
    render(<SectionTypeEditor {...defaultProps({ saveError })} />);

    // then
    expect(screen.getByRole("alert")).toHaveTextContent("network failure");
  });

  it("disables Save when the label is empty", () => {
    // given
    render(<SectionTypeEditor {...defaultProps({ initialLabel: "" })} />);

    // then
    expect(screen.getByRole("button", { name: /^save$/i })).toBeDisabled();
  });

  it("does not render a Delete button for a draft (typeId null)", () => {
    // given
    render(<SectionTypeEditor {...defaultProps({ typeId: null })} />);

    // then
    expect(
      screen.queryByRole("button", { name: "Delete" }),
    ).not.toBeInTheDocument();
  });

  it("calls onDeleted after a successful delete", async () => {
    // given
    const onDeleted = vi.fn();
    const deleteType = vi.fn().mockResolvedValue(undefined);
    render(<SectionTypeEditor {...defaultProps({ onDeleted, deleteType })} />);

    // when
    await user.click(screen.getByRole("button", { name: "Delete" }));
    await user.click(screen.getByRole("button", { name: /^delete$/i }));

    // then
    await waitFor(() => {
      expect(onDeleted).toHaveBeenCalled();
    });
  });

  it("reveals reassignment chooser when delete fails with SECTION_TYPE_IN_USE", async () => {
    // given
    const deleteType = vi
      .fn()
      .mockRejectedValueOnce(
        new ApiError(409, "in use", "SECTION_TYPE_IN_USE"),
      );
    render(<SectionTypeEditor {...defaultProps({ deleteType })} />);

    // when
    await user.click(screen.getByRole("button", { name: "Delete" }));
    await user.click(screen.getByRole("button", { name: /^delete$/i }));

    // then — a "Move sections to…" chooser appears
    await screen.findByRole("button", { name: /move sections to/i });
    expect(screen.getByRole("alert")).toHaveTextContent(
      "This type still classifies sections.",
    );
  });
});
