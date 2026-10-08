import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import type { ActionGroup } from "@/features/events/ui/ScheduleMenuList";
import { ActionList } from "@/features/events/ui/ScheduleMenuList";

const OCCURRENCE: ActionGroup = {
  label: "This session",
  actions: [
    { id: "reschedule", label: "Reschedule" },
    { id: "cancel", label: "Cancel", tone: "danger" },
  ],
};

const SERIES: ActionGroup = {
  label: "Whole series",
  actions: [
    { id: "edit-series", label: "Edit" },
    { id: "end-series", label: "End", tone: "danger" },
  ],
};

function renderList(
  groups: ActionGroup[],
  title?: { primary: string; secondary?: string },
) {
  render(
    <ActionList
      groups={groups}
      onAction={vi.fn()}
      {...(title === undefined ? {} : { title })}
    />,
  );
}

describe("ActionList", () => {
  /**
   * The heading is what lets a series command be called "Edit" rather than "Edit series". That
   * shortening is only honest if the scope reaches a reader who cannot see the rule above it, so
   * the grouping has to be a real group with a real accessible name, not a styled line.
   */
  it("names each group so the scope travels with its items", () => {
    renderList([OCCURRENCE, SERIES]);

    expect(
      screen.getByRole("group", { name: "This session" }),
    ).toBeInTheDocument();
    const series = screen.getByRole("group", { name: "Whole series" });
    expect(series).toContainElement(
      screen.getByRole("menuitem", { name: "Edit" }),
    );
  });

  it("rules one group off from the next", () => {
    renderList([OCCURRENCE, SERIES]);

    expect(screen.getAllByRole("separator")).toHaveLength(1);
  });

  /** A one-off belongs to no series, so its series group arrives empty. An empty group must
   *  leave behind neither a heading nor a rule with nothing under it. */
  it("drops an empty group entirely", () => {
    renderList([OCCURRENCE, { label: "Whole series", actions: [] }]);

    expect(screen.queryByRole("group", { name: "Whole series" })).toBeNull();
    expect(screen.queryAllByRole("separator")).toHaveLength(0);
  });

  it("renders an unlabelled group as the plain list it was", () => {
    renderList([{ actions: [{ id: "new-series", label: "New series" }] }]);

    expect(
      screen.getByRole("menuitem", { name: "New series" }),
    ).toBeInTheDocument();
    expect(screen.queryAllByRole("group")).toHaveLength(0);
  });

  it("states what the menu was opened on, above a rule", () => {
    renderList([OCCURRENCE], {
      primary: "Guild",
      secondary: "Wed 2 Jul · 18:00",
    });

    expect(screen.getByText("Guild")).toBeInTheDocument();
    expect(screen.getByText("Wed 2 Jul · 18:00")).toBeInTheDocument();
    expect(screen.getAllByRole("separator")).toHaveLength(1);
  });

  it("says so when there is nothing to offer", () => {
    renderList([{ label: "This session", actions: [] }]);

    expect(screen.getByText("No actions")).toBeInTheDocument();
  });

  /**
   * The heading naming the target is a non-focusable node, so the focus ring steps over it: a
   * reader who lands on the first command hears the menu's name and nothing else. Left as
   * "Schedule actions", the header closed the ambiguity for sighted readers alone.
   */
  it("answers to what it was opened on, not to what kind of menu it is", () => {
    renderList([OCCURRENCE], {
      primary: "Guild",
      secondary: "Wed, Jul 2 · 18:00",
    });

    expect(screen.getByRole("menu")).toHaveAccessibleName(
      "Guild, Wed, Jul 2 · 18:00",
    );
  });

  it("names itself for what it is when nothing was opened on", () => {
    renderList([OCCURRENCE]);

    expect(screen.getByRole("menu")).toHaveAccessibleName("Schedule actions");
  });
});
