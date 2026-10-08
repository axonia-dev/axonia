import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { Section } from "@/features/events/domain/section";
import { SlotFormTail } from "@/features/events/ui/SlotFormTail";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

const section: Section = {
  id: 3,
  sessionId: 9,
  position: 0,
  sectionTypeId: 2,
  sectionType: "Talk",
  sectionTypeIcon: null,
  sectionTypeAccentColor: null,
  title: "Main talk",
  durationMinutes: 30,
  slots: [],
};

function renderTail(
  overrides: Partial<React.ComponentProps<typeof SlotFormTail>> = {},
) {
  return render(
    <SlotFormTail
      minutes={15}
      onMinutesChange={vi.fn()}
      section={section}
      onCancel={vi.fn()}
      onSubmit={vi.fn()}
      submitLabel="Add"
      submitDisabled={false}
      error={null}
      {...overrides}
    />,
  );
}

describe("SlotFormTail", () => {
  it("renders the minutes slider and a labelled submit button", () => {
    renderTail({ submitLabel: "Save" });
    expect(screen.getByRole("slider")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Save" })).toBeInTheDocument();
  });

  it("calls onCancel and onSubmit from the footer's buttons", async () => {
    const onCancel = vi.fn();
    const onSubmit = vi.fn();
    renderTail({ onCancel, onSubmit });
    await user.click(screen.getByRole("button", { name: "Cancel" }));
    await user.click(screen.getByRole("button", { name: "Add" }));
    expect(onCancel).toHaveBeenCalledOnce();
    expect(onSubmit).toHaveBeenCalledOnce();
  });

  it("disables the submit button when submitDisabled is true", () => {
    renderTail({ submitDisabled: true });
    expect(screen.getByRole("button", { name: "Add" })).toBeDisabled();
  });

  it("shows the mutation's error message", () => {
    renderTail({ error: new Error("boom") });
    expect(screen.getByRole("alert")).toBeInTheDocument();
  });

  it("shows nothing for a null error", () => {
    renderTail({ error: null });
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });
});
