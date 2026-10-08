import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { Membership } from "@/features/community/domain/membership";
import { MemberRow } from "@/features/community/ui/MemberRow";
import type { User } from "@/features/identity/domain/user";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

const member: Membership = {
  id: 2,
  communityId: 1,
  userId: "bob",
  role: "MEMBER",
  joinedAt: "2026-04-02T00:00:00Z",
  updatedAt: "2026-04-02T00:00:00Z",
};

const bob: User = {
  id: "bob",
  displayName: "Bob Lee",
  email: null,
  avatarUrl: null,
  department: "Design Systems",
  businessUnit: null,
  team: "Tokens",
  memberStatus: "PRESENT",
  avatarIsOwn: false,
  timeZoneIsOwn: false,
};

function renderRow(over: Partial<Parameters<typeof MemberRow>[0]> = {}) {
  render(
    <ul>
      <MemberRow
        member={member}
        user={bob}
        isYou={false}
        isDisabled={false}
        onChangeRole={vi.fn()}
        {...over}
      />
    </ul>,
  );
}

describe("MemberRow", () => {
  it("shows the resolved display name and team", () => {
    renderRow();
    expect(screen.getByText("Bob Lee")).toBeInTheDocument();
    expect(
      screen.getByText(/Design Systems · Tokens · joined Apr 2026/),
    ).toBeInTheDocument();
  });

  it("falls back to the userId when unresolved", () => {
    renderRow({ user: undefined });
    expect(screen.getByText("bob")).toBeInTheDocument();
  });

  it("marks the current user", () => {
    renderRow({ isYou: true });
    expect(screen.getByText("Me")).toBeInTheDocument();
  });

  it("promotes through the switch", async () => {
    const onChangeRole = vi.fn();
    renderRow({ onChangeRole });
    await user.click(screen.getByRole("switch"));
    expect(onChangeRole).toHaveBeenCalledWith("ORGANIZER");
  });

  it("renders an inline error when provided", () => {
    renderRow({ errorMessage: "A community always needs one." });
    expect(screen.getByRole("alert")).toHaveTextContent(/always needs one/i);
  });

  it("shows the profile picture when available", () => {
    renderRow({
      user: { ...bob, avatarUrl: "https://cdn.example.com/bob.png" },
    });
    expect(screen.getByRole("img", { name: "Bob Lee" })).toHaveAttribute(
      "src",
      "https://cdn.example.com/bob.png",
    );
  });

  it("marks a deactivated member beside their name", () => {
    renderRow({ user: { ...bob, memberStatus: "DEACTIVATED" } });
    expect(screen.getByText("Bob Lee")).toBeInTheDocument();
    expect(screen.getByText("(Deactivated)")).toBeInTheDocument();
  });

  it("shows an erased member under the neutral label, muted and italic", () => {
    renderRow({
      user: {
        ...bob,
        displayName: "Deleted member",
        memberStatus: "ERASED",
      },
    });
    expect(screen.getByText("Deleted member")).toHaveClass(
      "text-muted-foreground",
      "italic",
    );
  });
});
