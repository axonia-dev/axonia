import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { I18nProvider } from "react-aria-components";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { DateTimePicker } from "@/components/ui/DateTimePicker";
import { instantToZoned } from "@/features/events/domain/zoned-instant";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

function renderUS(ui: ReactNode) {
  return render(<I18nProvider locale="en-US">{ui}</I18nProvider>);
}

// The shape a real caller seeds from, rather than one hand-written to match whatever the time
// field happens to emit: `HH:MM`, no seconds.
const seeded = instantToZoned("2026-06-15T16:00:00Z", "Europe/Zurich");

describe("DateTimePicker", () => {
  it("states the zone the typed time is read in, named for a reader", () => {
    renderUS(
      <DateTimePicker
        label="Starts at"
        value={seeded}
        onChange={vi.fn()}
        timeZone="America/Los_Angeles"
      />,
    );

    expect(screen.getByText("Los Angeles")).toBeInTheDocument();
    expect(screen.queryByText(/_/)).not.toBeInTheDocument();
  });

  it("keeps the hidden field labels reachable by name", () => {
    renderUS(
      <DateTimePicker
        label="Starts at"
        value={seeded}
        onChange={vi.fn()}
        timeZone="Europe/Zurich"
      />,
    );

    expect(
      screen.getByRole("group", { name: "Starts at date" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("group", { name: "Starts at time" }),
    ).toBeInTheDocument();
  });

  it("leaves the time untouched when the date changes", async () => {
    const onChange = vi.fn();
    renderUS(
      <DateTimePicker
        label="Starts at"
        value={seeded}
        onChange={onChange}
        timeZone="Europe/Zurich"
      />,
    );

    await user.click(screen.getByRole("spinbutton", { name: /day/i }));
    await user.keyboard("{ArrowUp}");

    expect(onChange).toHaveBeenLastCalledWith({
      date: "2026-06-16",
      time: "18:00",
    });
  });

  it("reports a typed time as HH:MM, the shape it was seeded in", async () => {
    const onChange = vi.fn();
    renderUS(
      <DateTimePicker
        label="Starts at"
        value={seeded}
        onChange={onChange}
        timeZone="Europe/Zurich"
      />,
    );

    await user.click(screen.getByRole("spinbutton", { name: /minute/i }));
    await user.keyboard("{ArrowUp}");

    // The field itself emits "18:01:00"; a value that changes shape the moment it is touched is
    // how an untouched-value comparison starts reporting a change nobody made.
    expect(onChange).toHaveBeenLastCalledWith({
      date: "2026-06-15",
      time: "18:01",
    });
  });

  it("reports a cleared time as an empty string rather than coercing it", async () => {
    const onChange = vi.fn();
    renderUS(
      <DateTimePicker
        label="Starts at"
        value={seeded}
        onChange={onChange}
        timeZone="Europe/Zurich"
        isTimeRequired={false}
      />,
    );

    await user.click(screen.getByRole("spinbutton", { name: /hour/i }));
    await user.keyboard(
      "{Backspace}{ArrowRight}{Backspace}{ArrowRight}{Backspace}",
    );

    expect(onChange).toHaveBeenLastCalledWith({
      date: "2026-06-15",
      time: "",
    });
  });

  it("shows a description only when one states something the fields cannot", () => {
    const { rerender } = renderUS(
      <DateTimePicker
        label="Starts at"
        value={seeded}
        onChange={vi.fn()}
        timeZone="Europe/Zurich"
      />,
    );

    expect(screen.queryByText(/17:00 in London/)).not.toBeInTheDocument();

    rerender(
      <I18nProvider locale="en-US">
        <DateTimePicker
          label="Starts at"
          value={seeded}
          onChange={vi.fn()}
          timeZone="Europe/Zurich"
          description="17:00 in London"
        />
      </I18nProvider>,
    );

    expect(screen.getByText("17:00 in London")).toBeInTheDocument();
  });

  /**
   * A bound on the pair - "before the session starts" reads the date and the time together - so a
   * refusal has to reach both fields. Marked on one only, half the control looks accepted.
   */
  it("marks both fields when the caller refuses the value", () => {
    renderUS(
      <DateTimePicker
        label="Starts at"
        value={seeded}
        onChange={vi.fn()}
        timeZone="Europe/Zurich"
        isInvalid
      />,
    );

    expect(
      screen.getByRole("group", { name: "Starts at date" }),
    ).toHaveAttribute("data-invalid");
    expect(
      screen.getByRole("group", { name: "Starts at time" }),
    ).toHaveAttribute("data-invalid");
    expect(screen.getByRole("spinbutton", { name: /month/i })).toHaveAttribute(
      "aria-invalid",
      "true",
    );
    expect(screen.getByRole("spinbutton", { name: /hour/i })).toHaveAttribute(
      "aria-invalid",
      "true",
    );
  });

  /**
   * A caller with no opinion says nothing rather than "valid": forwarding a default `false` would
   * pin react-aria's own validation state and suppress the invalidity the two fields work out for
   * themselves, which is a refusal no caller ever asked to override.
   */
  it("leaves the fields' own validity alone when no caller states one", () => {
    renderUS(
      <DateTimePicker
        label="Starts at"
        value={seeded}
        onChange={vi.fn()}
        timeZone="Europe/Zurich"
      />,
    );

    expect(
      screen.getByRole("group", { name: "Starts at date" }),
    ).not.toHaveAttribute("data-invalid");
    expect(
      screen.getByRole("group", { name: "Starts at time" }),
    ).not.toHaveAttribute("data-invalid");
  });

  /**
   * The description states a rule about the pair, so it is announced with the pair. Rendered as a
   * bare paragraph it was drawn on screen and reached nobody using a screen reader - and the one
   * rule this control exists to state, that an empty hour is an answer, is exactly the kind that
   * cannot be inferred from the fields.
   */
  it("announces the description as the group's own, not merely draws it", () => {
    renderUS(
      <DateTimePicker
        label="Starts at"
        value={seeded}
        onChange={vi.fn()}
        timeZone="Europe/Zurich"
        isTimeRequired={false}
        description="Leave empty to keep the hour open."
      />,
    );

    expect(
      screen.getByRole("group", { name: "Starts at" }),
    ).toHaveAccessibleDescription("Leave empty to keep the hour open.");
  });
});
