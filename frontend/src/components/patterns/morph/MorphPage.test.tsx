import { render, screen } from "@testing-library/react";
import type { ReactNode } from "react";
import { describe, expect, it, vi } from "vitest";

import { MorphPage } from "@/components/patterns/morph/MorphPage";

vi.mock("react", async (original) => ({
  ...(await original<typeof import("react")>()),
  ViewTransition: ({
    name,
    default: fallback,
    update,
    share,
    children,
  }: Readonly<{
    name: string;
    default: string;
    update: Record<string, string>;
    share: Record<string, string>;
    children: ReactNode;
  }>) => (
    <div
      data-testid="view-transition"
      data-name={name}
      data-default={fallback}
      data-update={JSON.stringify(update)}
      data-share={JSON.stringify(share)}
    >
      {children}
    </div>
  ),
}));

describe("MorphPage", () => {
  it("moves the page on morphs only, so plain navigations stay instant", () => {
    // when
    render(
      <MorphPage>
        <main>page</main>
      </MorphPage>,
    );

    // then
    const boundary = screen.getByTestId("view-transition");
    const onMorph = JSON.stringify({ morph: "morph-page", default: "none" });
    expect(boundary).toContainElement(screen.getByText("page"));
    expect(boundary).toHaveAttribute("data-name", "morph-page");
    expect(boundary).toHaveAttribute("data-default", "none");
    expect(boundary).toHaveAttribute("data-update", onMorph);
    expect(boundary).toHaveAttribute("data-share", onMorph);
  });
});
