import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it } from "vitest";

import type { ActionItem } from "@/features/events/domain/action-item";
import { DueChip } from "@/features/events/ui/ActionItemChips";
import { ActionMeta } from "@/features/events/ui/ActionMeta";

function item(overrides: Partial<ActionItem> = {}): ActionItem {
  return {
    id: 1,
    communityId: 1,
    title: "Renew the certificate",
    description: "",
    tags: [],
    raisedByUserId: "alice",
    raisedByDisplayName: "Alice",
    assigneeUserId: null,
    assigneeDisplayName: null,
    dueDate: null,
    originSessionId: null,
    checkpoint: {
      sessionId: null,
      scheduledAt: null,
      pinned: false,
      governingTimeZone: null,
    },
    carryCount: 0,
    outcome: "OPEN",
    canClose: false,
    canReopen: false,
    canEdit: false,
    canDelete: false,
    createdAt: "x",
    updatedAt: "x",
    ...overrides,
  };
}

function renderMeta(props: Omit<Parameters<typeof ActionMeta>[0], "slug">) {
  render(
    <MemoryRouter>
      <ActionMeta slug="backend-guild" {...props} />
    </MemoryRouter>,
  );
}

describe("ActionMeta", () => {
  it("shows a passed due date and a carry count as separate facts", () => {
    renderMeta({
      item: item({ dueDate: "2026-09-11", carryCount: 3 }),
      users: {},
    });

    expect(screen.getByText("Due Sep 11")).toBeInTheDocument();
    expect(screen.getByText("Carried 3 times")).toBeInTheDocument();
  });

  it("says nothing about carrying on a fresh item", () => {
    renderMeta({ item: item({ carryCount: 0 }), users: {} });

    expect(screen.queryByText(/Carried/)).not.toBeInTheDocument();
  });

  it("uses the singular form for a single carry", () => {
    renderMeta({ item: item({ carryCount: 1 }), users: {} });

    expect(screen.getByText("Carried once")).toBeInTheDocument();
  });

  it("never calls a passed due date overdue", () => {
    renderMeta({ item: item({ dueDate: "2020-09-11" }), users: {} });

    expect(screen.queryByText(/Overdue/)).not.toBeInTheDocument();
    expect(screen.getByText(/Due Sep 11/)).toBeInTheDocument();
  });

  /**
   * The Action items tab renders a due date through DueChip and a session's action point row
   * through here. Two formats for one day read as two different dates, so both go through one
   * formatter and this pins them to the same string.
   */
  it("spells a due date exactly as the Action items tab does", () => {
    const dueDate = "2020-03-05";

    render(
      <MemoryRouter>
        <ActionMeta item={item({ dueDate })} slug="backend-guild" users={{}} />
        <DueChip dueDate={dueDate} now={new Date()} />
      </MemoryRouter>,
    );

    expect(screen.getAllByText("Due Mar 5")).toHaveLength(2);
  });

  it("falls back to Unassigned with no assignee", () => {
    renderMeta({ item: item(), users: {} });

    expect(screen.getByLabelText("Unassigned")).toBeInTheDocument();
  });

  /** The hover card is expected to name the assignee on hover; it stays mounted, just
   *  css-hidden. What must not exist is a second, always-visible label beside the avatar. */
  it("names the assignee without printing the name beside the avatar", () => {
    renderMeta({
      item: item({ assigneeUserId: "bob", assigneeDisplayName: "Bob Green" }),
      users: {},
    });

    expect(screen.getByLabelText("Bob Green")).toBeInTheDocument();
    const outside = screen
      .queryAllByText("Bob Green")
      .filter((el) => el.closest('[role="tooltip"]') === null);
    expect(outside).toHaveLength(0);
  });

  it("suppresses the checkpoint chip when the surface is the checkpoint", () => {
    renderMeta({ item: item(), users: {}, hideCheckpoint: true });

    expect(screen.queryByText(/Next session/)).not.toBeInTheDocument();
  });
});
