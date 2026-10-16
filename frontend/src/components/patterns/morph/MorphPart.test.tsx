import { render, screen } from "@testing-library/react";
import type { ReactNode } from "react";
import { describe, expect, it, vi } from "vitest";

import { morphName, MorphPart } from "@/components/patterns/morph/MorphPart";

vi.mock("react", async (original) => ({
  ...(await original<typeof import("react")>()),
  ViewTransition: ({
    name,
    default: fallback,
    share,
    children,
  }: Readonly<{
    name?: string;
    default: string;
    share: string | Record<string, string>;
    children: ReactNode;
  }>) => (
    <div
      data-testid="view-transition"
      data-name={name}
      data-default={fallback}
      data-share={JSON.stringify(share)}
    >
      {children}
    </div>
  ),
}));

describe("MorphPart", () => {
  it("keeps an inert boundary without an id", () => {
    // when
    render(
      <MorphPart part="badge" id={undefined}>
        <span>badge</span>
      </MorphPart>,
    );

    // then
    const boundary = screen.getByTestId("view-transition");
    expect(boundary).toContainElement(screen.getByText("badge"));
    expect(boundary).not.toHaveAttribute("data-name");
    expect(boundary).toHaveAttribute("data-default", "none");
    expect(boundary).toHaveAttribute("data-share", '"none"');
  });

  it("keeps the same child node when the id comes and goes", () => {
    // given
    const { rerender } = render(
      <MorphPart part="banner" id={undefined}>
        <img alt="hero" src="/hero.png" />
      </MorphPart>,
    );
    const before = screen.getByRole("img");

    // when
    rerender(
      <MorphPart part="banner" id="community-x">
        <img alt="hero" src="/hero.png" />
      </MorphPart>,
    );
    const named = screen.getByRole("img");
    rerender(
      <MorphPart part="banner" id={undefined}>
        <img alt="hero" src="/hero.png" />
      </MorphPart>,
    );

    // then
    expect(named).toBe(before);
    expect(screen.getByRole("img")).toBe(before);
  });

  it("shares the child under its name on morphs only", () => {
    // when
    render(
      <MorphPart part="badge" id="community-x">
        <span>badge</span>
      </MorphPart>,
    );

    // then
    const boundary = screen.getByTestId("view-transition");
    expect(boundary).toContainElement(screen.getByText("badge"));
    expect(boundary).toHaveAttribute("data-name", "morph-badge-community-x");
    expect(boundary).toHaveAttribute("data-default", "none");
    expect(boundary).toHaveAttribute(
      "data-share",
      JSON.stringify({ morph: "morph-part", default: "none" }),
    );
  });

  it.each([
    ["banner", "morph-cover"],
    ["surface", "morph-surface"],
    ["title", "morph-text"],
    ["pill", "morph-part"],
  ] as const)("styles a %s with %s", (part, className) => {
    // when
    render(
      <MorphPart part={part} id="session-1">
        <span>part</span>
      </MorphPart>,
    );

    // then
    expect(screen.getByTestId("view-transition")).toHaveAttribute(
      "data-share",
      JSON.stringify({ morph: className, default: "none" }),
    );
  });

  it("names a part by kind and id, as a valid identifier", () => {
    // then
    expect(morphName("title", "session-42")).toBe("morph-title-session-42");
    expect(morphName("name", "community-café club")).toBe(
      "morph-name-community-caf__club",
    );
  });
});
