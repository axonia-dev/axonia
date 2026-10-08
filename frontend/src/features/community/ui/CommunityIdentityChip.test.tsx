import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import type { Community } from "@/features/community/domain/community";

import { CommunityIdentityChip } from "./CommunityIdentityChip";

const current = {
  id: 1,
  slug: "rust-practitioners",
  name: "Rust Practitioners",
  kindId: 1,
  memberCount: 42,
  badgeUrl: "https://cdn/badge.png",
  accentColor: "#1aa6a0",
} as Community;

// Switching, discovering and creating communities live on the tenant settings page; this chip is identity only.
describe("CommunityIdentityChip", () => {
  it("renders the current community name", () => {
    render(<CommunityIdentityChip current={current} />);
    expect(screen.getByText("Rust Practitioners")).toBeInTheDocument();
  });

  it("shows the current community's badge on the trigger tile", () => {
    render(<CommunityIdentityChip current={current} />);
    expect(
      screen.getByRole("img", { name: "Rust Practitioners" }),
    ).toHaveAttribute("src", "https://cdn/badge.png");
  });

  it("shows the current community's member count", () => {
    render(<CommunityIdentityChip current={current} />);
    expect(screen.getByText("42 members")).toBeInTheDocument();
  });

  it("offers no switch control", () => {
    render(<CommunityIdentityChip current={current} />);
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
    expect(screen.queryByRole("menu")).not.toBeInTheDocument();
  });

  it("renders a bare avatar with no label in the collapsed rail", () => {
    render(<CommunityIdentityChip current={current} collapsed />);
    expect(screen.queryByText("Rust Practitioners")).not.toBeInTheDocument();
    expect(
      screen.getByRole("img", { name: "Rust Practitioners" }),
    ).toBeInTheDocument();
  });
});
