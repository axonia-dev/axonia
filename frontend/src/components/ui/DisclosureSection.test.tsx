import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it } from "vitest";

import { DisclosureSection } from "@/components/ui/DisclosureSection";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

function renderSection(defaultExpanded = false) {
  render(
    <DisclosureSection
      title="Where it meets"
      summary="Not set."
      defaultExpanded={defaultExpanded}
    >
      <input aria-label="Location" />
    </DisclosureSection>,
  );
  return screen.getByRole("button", { name: /Where it meets/ });
}

describe("DisclosureSection", () => {
  /** A summary that only appears once the section is open would be no help at all: the point of
   *  shutting a section is deciding whether to open it without doing so. */
  it("states what it holds while it is shut", () => {
    renderSection();

    expect(screen.getByText("Not set.")).toBeInTheDocument();
    // Queried by role, which is the accessibility tree: the panel stays in the DOM and is hidden,
    // so what matters is that nothing inside it is reachable.
    expect(screen.queryByRole("textbox", { name: "Location" })).toBeNull();
  });

  /** A shut panel is `hidden="until-found"`, which the browser draws as `content-visibility:
   *  hidden` rather than `display: none` (Tailwind's preflight exempts it too), so its own box,
   *  padding included, stays on screen under the trigger. */
  it("draws no padding while it is shut", () => {
    // when
    const trigger = renderSection();

    // then
    const panel = document.getElementById(
      trigger.getAttribute("aria-controls") ?? "",
    );
    expect(panel).toHaveAttribute("hidden", "until-found");
    expect(panel?.className).not.toMatch(/(?:^|\s)p[trblxy]?-/);
  });

  it("carries its state on the trigger rather than only in the drawing", () => {
    const trigger = renderSection();

    expect(trigger).toHaveAttribute("aria-expanded", "false");
  });

  it("opens on the press", async () => {
    const trigger = renderSection();

    await user.click(trigger);

    expect(trigger).toHaveAttribute("aria-expanded", "true");
    expect(
      screen.getByRole("textbox", { name: "Location" }),
    ).toBeInTheDocument();
  }, 15_000);

  /** Open, the summary is a caption on fields that are directly below it and in plain sight. */
  it("drops the summary once the fields it describes are showing", async () => {
    const trigger = renderSection();

    await user.click(trigger);

    expect(screen.queryByText("Not set.")).toBeNull();
  }, 15_000);

  /** A section that already holds an answer opens showing it: shut, it would hide something the
   *  reader put there and make an edit look like a blank form. */
  it("can open on mount", () => {
    renderSection(true);

    expect(
      screen.getByRole("textbox", { name: "Location" }),
    ).toBeInTheDocument();
  });

  it("shuts again", async () => {
    const trigger = renderSection(true);

    await user.click(trigger);

    expect(screen.queryByRole("textbox", { name: "Location" })).toBeNull();
  }, 15_000);
});
