import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { PresenterHoverCard } from "@/features/events/ui/PresenterHoverCard";
import { userHue } from "@/lib/user-hue";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

describe("PresenterHoverCard", () => {
  const base = {
    id: "u1",
    displayName: "Alex Bauer",
    email: "a@x.com",
    memberStatus: "PRESENT" as const,
    avatarIsOwn: false,
    timeZoneIsOwn: false,
  };

  it("shows name and org rows when present, but never the email", () => {
    render(
      <PresenterHoverCard
        name="Alex Bauer"
        user={{
          ...base,
          department: "Platform",
          team: "Payments",
          businessUnit: "Acme",
        }}
      />,
    );
    expect(screen.getByText("Alex Bauer")).toBeInTheDocument();
    expect(screen.getByText("Platform")).toBeInTheDocument();
    expect(screen.getByText("Payments")).toBeInTheDocument();
    expect(screen.getByText("Business unit")).toBeInTheDocument();
    expect(screen.getByText("Acme")).toBeInTheDocument();
    expect(screen.queryByText("a@x.com")).not.toBeInTheDocument();
  });

  it("omits org rows when absent and shows initials when no avatarUrl", () => {
    render(<PresenterHoverCard name="Alex Bauer" user={base} />);
    expect(screen.queryByText("Department")).not.toBeInTheDocument();
    // Initials render in both the trigger and the hover-card header.
    expect(screen.getAllByText("AB")).toHaveLength(2);
    expect(
      within(screen.getByRole("tooltip")).getByText("AB"),
    ).toBeInTheDocument();
    expect(screen.queryByRole("img")).not.toBeInTheDocument();
  });

  /**
   * The account menu is the reference rendering of a person, and every other surface follows it.
   * A presenter had its own monogram: two letters for a one-word name where the reference draws
   * one, and a fixed accent where the reference draws the person's own hue - so the same account
   * read as two different people depending on which surface you looked at.
   */
  it("draws a one-word name exactly as the reference avatar does, on one letter", () => {
    render(<PresenterHoverCard name="Gabriel" user={{ ...base }} />);

    expect(screen.getAllByText("G")).toHaveLength(2);
    expect(screen.queryByText("GA")).not.toBeInTheDocument();
  });

  it("colours the monogram with the person's own hue, not a fixed accent", () => {
    render(<PresenterHoverCard name="Gabriel" user={{ ...base }} />);

    for (const monogram of screen.getAllByText("G")) {
      expect(monogram.style.getPropertyValue("--k")).toBe(userHue(base.id));
    }
  });

  it("renders the photo when avatarUrl is set", () => {
    const { container } = render(
      <PresenterHoverCard
        name="Alex Bauer"
        user={{ ...base, avatarUrl: "https://img/a.png" }}
      />,
    );
    // Decorative <img alt=""> has role="presentation", so query the element.
    const img = container.querySelector("img");
    expect(img).toHaveAttribute("src", "https://img/a.png");
  });

  it("hangs the card from the left edge by default and the right edge for align end", () => {
    const { rerender } = render(<PresenterHoverCard name="Alex Bauer" />);
    expect(screen.getByRole("tooltip")).toHaveClass("left-0");

    rerender(<PresenterHoverCard name="Alex Bauer" align="end" />);
    expect(screen.getByRole("tooltip")).toHaveClass("right-0");
  });

  it("takes the card out of layout until the avatar is hovered or focused", () => {
    // when
    render(<PresenterHoverCard name="Alex Bauer" />);

    // then
    expect(screen.getByRole("tooltip")).toHaveClass(
      "hidden",
      "group-hover/hc:block",
      "group-focus-within/hc:block",
    );
  });

  it("fires onPress when the avatar is clicked, for a caller that opts in", async () => {
    const onPress = vi.fn();
    render(
      <PresenterHoverCard name="Alex Bauer" user={base} onPress={onPress} />,
    );
    await user.click(screen.getByRole("button", { name: "Alex Bauer" }));
    expect(onPress).toHaveBeenCalledOnce();
  });

  it("stays inert on click when no onPress is given", async () => {
    render(<PresenterHoverCard name="Alex Bauer" user={base} />);
    await user.click(screen.getByRole("button", { name: "Alex Bauer" }));
    expect(screen.getByText("Alex Bauer")).toBeInTheDocument();
  });

  it("withholds the identity card entirely while suppressed", () => {
    render(<PresenterHoverCard name="Alex Bauer" user={base} suppressCard />);
    expect(screen.queryByRole("tooltip")).not.toBeInTheDocument();
    // The avatar itself stays: only the card yields.
    expect(
      screen.getByRole("button", { name: "Alex Bauer" }),
    ).toBeInTheDocument();
  });

  it("marks a deactivated presenter beside their name, muted", () => {
    render(
      <PresenterHoverCard
        name="Alex Bauer"
        user={{ ...base, memberStatus: "DEACTIVATED" }}
      />,
    );
    expect(screen.getByText("Alex Bauer")).toHaveClass("text-muted-foreground");
    expect(screen.getByText("(Deactivated)")).toBeInTheDocument();
  });

  it("shows an erased presenter under the neutral label, muted and italic", () => {
    render(
      <PresenterHoverCard
        name="Deleted member"
        user={{
          ...base,
          displayName: "Deleted member",
          memberStatus: "ERASED",
        }}
      />,
    );
    expect(screen.getByText("Deleted member")).toHaveClass(
      "text-muted-foreground",
      "italic",
    );
  });
});
