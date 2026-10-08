import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { AccountHero } from "@/features/identity/ui/AccountHero";

const ALICE = {
  id: "alice",
  displayName: "Alice Anderson",
  email: "alice@example.com",
  memberStatus: "PRESENT" as const,
  memberSince: "2026-03-04T10:15:30Z",
  lastActive: "2026-08-14T08:00:00Z",
  signInProvider: "Google",
  avatarIsOwn: false,
  timeZoneIsOwn: false,
};

describe("AccountHero", () => {
  it("shows the person, their provider and their tenure", () => {
    render(
      <AccountHero
        user={ALICE}
        organizationCount={2}
        expertiseCount={5}
        onChangePicture={vi.fn()}
      />,
    );

    expect(screen.getByText("Alice Anderson")).toBeInTheDocument();
    expect(screen.getByText("alice@example.com")).toBeInTheDocument();
    expect(screen.getByText("Google")).toBeInTheDocument();
    expect(screen.getByText("March 2026")).toBeInTheDocument();
    expect(screen.getByText("2")).toBeInTheDocument();
    expect(screen.getByText("5")).toBeInTheDocument();
  });

  it("names a single organization and a single expertise area in the singular", () => {
    render(
      <AccountHero
        user={ALICE}
        organizationCount={1}
        expertiseCount={1}
        onChangePicture={vi.fn()}
      />,
    );

    expect(screen.getByText("ORGANIZATION")).toBeInTheDocument();
    expect(screen.getByText("EXPERTISE AREA")).toBeInTheDocument();
  });

  it("names several organizations and expertise areas in the plural", () => {
    render(
      <AccountHero
        user={ALICE}
        organizationCount={2}
        expertiseCount={5}
        onChangePicture={vi.fn()}
      />,
    );

    expect(screen.getByText("ORGANIZATIONS")).toBeInTheDocument();
    expect(screen.getByText("EXPERTISE AREAS")).toBeInTheDocument();
  });

  it("drops the provider chip when the token named none", () => {
    render(
      <AccountHero
        user={{ ...ALICE, signInProvider: null }}
        organizationCount={2}
        expertiseCount={5}
        onChangePicture={vi.fn()}
      />,
    );

    expect(screen.queryByText("Google")).not.toBeInTheDocument();
  });

  it("holds a count cell empty until its query resolves rather than showing zero", () => {
    render(
      <AccountHero
        user={ALICE}
        organizationCount={undefined}
        expertiseCount={undefined}
        onChangePicture={vi.fn()}
      />,
    );

    expect(screen.queryByText("0")).not.toBeInTheDocument();
  });

  it("drops a stat cell the profile has no value for", () => {
    render(
      <AccountHero
        user={{ ...ALICE, memberSince: null, lastActive: null }}
        organizationCount={2}
        expertiseCount={5}
        onChangePicture={vi.fn()}
      />,
    );

    expect(screen.queryByText("MEMBER SINCE")).not.toBeInTheDocument();
    expect(screen.queryByText("LAST ACTIVE")).not.toBeInTheDocument();
  });
});
