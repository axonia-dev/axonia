import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";

import { MarqueeNext } from "@/features/community/ui/MarqueeNext";
import { morphNames } from "@/test/morph-names";

vi.mock("react", async (original) => ({
  ...(await original<typeof import("react")>()),
  ViewTransition: (await import("@/test/morph-names")).ViewTransitionProbe,
}));
vi.mock(
  "@/features/events/api/use-ready-session",
  () => import("@/test/ready-session"),
);

const NEXT = {
  sessionId: 12,
  title: "Kafka office hours",
  scheduledAt: "2099-01-01T18:00:00Z",
  booked: 1,
  openSpots: 2,
  empty: false,
};

describe("MarqueeNext", () => {
  it("names the next session's title as the only morph source", () => {
    // when
    const { container } = render(
      <MemoryRouter>
        <MarqueeNext slug="backend-guild" next={NEXT} />
      </MemoryRouter>,
    );

    // then
    expect(morphNames(container)).toEqual(["morph-title-session-12"]);
    expect(
      screen.getByRole("link", { name: "Kafka office hours" }),
    ).toHaveAttribute("href", "/communities/backend-guild/sessions/12");
    expect(screen.getByRole("link", { name: /take a spot/i })).toHaveAttribute(
      "href",
      "/communities/backend-guild/sessions/12",
    );
  });
});
