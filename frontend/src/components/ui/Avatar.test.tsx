import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { Avatar } from "@/components/ui/Avatar";

describe("Avatar", () => {
  it("renders up to two initials from the name", () => {
    // when
    render(<Avatar name="Backend Guild" hue="#de3b78" />);

    // then
    expect(screen.getByText("BG")).toBeInTheDocument();
  });

  it("applies the hue as a CSS custom property", () => {
    // when
    render(<Avatar name="Solo" hue="#1aa6a0" />);

    // then
    expect(screen.getByText("S").style.getPropertyValue("--k")).toBe("#1aa6a0");
  });

  it("leans the monogram toward the foreground by the theme's share", () => {
    // when
    render(<Avatar name="Solo" hue="#1aa6a0" />);

    // then
    expect(screen.getByText("S")).toHaveClass(
      "text-[color-mix(in_srgb,var(--k),var(--foreground)_var(--monogram-ink))]",
    );
  });

  it("defaults to a squircle (org) and renders a circle for a person", () => {
    const { rerender } = render(<Avatar name="Org" hue="#de3b78" />);
    expect(screen.getByText("O")).toHaveClass("rounded-md");

    rerender(<Avatar name="Person" hue="#de3b78" shape="circle" />);
    expect(screen.getByText("P")).toHaveClass("rounded-full");
  });

  it("renders the badge image instead of initials when src is set", () => {
    // when
    render(
      <Avatar
        name="Platform Guild"
        hue="#6e3ceb"
        src="https://cdn/badge.png"
      />,
    );

    // then
    const img = screen.getByRole("img", { name: "Platform Guild" });
    expect(img).toHaveAttribute("src", "https://cdn/badge.png");
    expect(screen.queryByText("PG")).not.toBeInTheDocument();
  });

  it("falls back to the monogram when the badge image fails to load", () => {
    render(
      <Avatar
        name="Java Backend Guild"
        hue="#6e3ceb"
        src="https://cdn/broken.png"
      />,
    );

    fireEvent.error(screen.getByRole("img", { name: "Java Backend Guild" }));

    expect(screen.queryByRole("img")).not.toBeInTheDocument();
    expect(screen.getByText("JB")).toBeInTheDocument();
  });

  it("retries a later, different src after a prior failure", () => {
    // given
    const { rerender } = render(
      <Avatar
        name="Java Backend Guild"
        hue="#6e3ceb"
        src="https://cdn/a.png"
      />,
    );
    fireEvent.error(screen.getByRole("img", { name: "Java Backend Guild" }));
    expect(screen.getByText("JB")).toBeInTheDocument();

    // when
    rerender(
      <Avatar
        name="Java Backend Guild"
        hue="#6e3ceb"
        src="https://cdn/b.png"
      />,
    );

    // then
    expect(
      screen.getByRole("img", { name: "Java Backend Guild" }),
    ).toHaveAttribute("src", "https://cdn/b.png");
  });

  it("keeps the monogram when rerendered with the same failed src", () => {
    // given
    const { rerender } = render(
      <Avatar
        name="Java Backend Guild"
        hue="#6e3ceb"
        src="https://cdn/a.png"
      />,
    );
    fireEvent.error(screen.getByRole("img", { name: "Java Backend Guild" }));

    // when
    rerender(
      <Avatar
        name="Java Backend Guild"
        hue="#6e3ceb"
        src="https://cdn/a.png"
      />,
    );

    // then
    expect(screen.queryByRole("img")).not.toBeInTheDocument();
    expect(screen.getByText("JB")).toBeInTheDocument();
  });

  it("does not blame a new src for the previous src's failed load", () => {
    // given a first (soon-to-fail) src — e.g. the optimistic token picture
    const { rerender } = render(
      <Avatar
        name="Java Backend Guild"
        hue="#6e3ceb"
        src="https://cdn/stale.png"
      />,
    );
    const stale = screen.getByRole("img", { name: "Java Backend Guild" });

    // when /me resolves and the src flips to the good, loadable URL
    rerender(
      <Avatar
        name="Java Backend Guild"
        hue="#6e3ceb"
        src="https://cdn/good.png"
      />,
    );

    // and the previous load errors late (aborted when the src changed)
    fireEvent.error(stale);

    // then the good image survives — it must not fall back to initials
    expect(
      screen.getByRole("img", { name: "Java Backend Guild" }),
    ).toHaveAttribute("src", "https://cdn/good.png");
    expect(screen.queryByText("JB")).not.toBeInTheDocument();
  });

  it("falls back to initials when src is null", () => {
    // when
    render(<Avatar name="Platform Guild" hue="#6e3ceb" src={null} />);

    // then
    expect(screen.getByText("PG")).toBeInTheDocument();
  });
});
