import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  useJoin,
  useMyMembership,
} from "@/features/community/api/use-membership";
import { useDepartForCeremony } from "@/features/community/ui/ceremony/use-depart-for-ceremony";
import { useRegisterVisitingJoin } from "@/features/community/ui/visiting-join-context";
import { VisitingFrame } from "@/features/community/ui/VisitingBar";

vi.mock("@/features/community/api/use-membership", () => ({
  useMyMembership: vi.fn(),
  useJoin: vi.fn(),
}));
vi.mock("@/features/community/ui/ceremony/use-depart-for-ceremony", () => ({
  useDepartForCeremony: vi.fn(),
}));
vi.mock("@/features/community/ui/ceremony/lazy-ceremonies", () => ({
  preloadHeroCeremony: () => undefined,
}));

const depart = vi.fn(() => Promise.resolve());

function membership(answer: Record<string, unknown>) {
  vi.mocked(useMyMembership).mockReturnValue(answer as never);
}

function HeroStandIn({ onJoined }: Readonly<{ onJoined: () => void }>) {
  useRegisterVisitingJoin(onJoined);
  return <p>hero</p>;
}

function WelcomingHero() {
  const [playing, setPlaying] = useState(false);
  useRegisterVisitingJoin(() => setPlaying(true), playing);
  return (
    <button type="button" onClick={() => setPlaying(false)}>
      end welcome
    </button>
  );
}

function barState(): string | null {
  return (
    screen
      .queryByRole("region", { name: "Membership", hidden: true })
      ?.closest("[data-visiting]")
      ?.getAttribute("data-visiting") ?? null
  );
}

function renderFrame(page = <p>page</p>) {
  return render(
    <MemoryRouter>
      <VisitingFrame name="Backend Guild" slug="backend-guild">
        {page}
      </VisitingFrame>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  membership({ data: null, isError: false });
  vi.mocked(useDepartForCeremony).mockReturnValue(depart);
  vi.mocked(useJoin).mockImplementation(
    (_slug, onJoined) =>
      ({
        mutate: () => void onJoined?.(),
        isPending: false,
        isError: false,
      }) as never,
  );
});

describe("VisitingBar", () => {
  it("tells a visitor they are visiting, with Join at hand", () => {
    // when
    renderFrame();

    // then
    expect(
      screen.getByRole("region", { name: "Membership" }),
    ).toHaveTextContent("Visiting Backend Guild");
    expect(screen.getByRole("button", { name: "Join" })).toBeInTheDocument();
    expect(screen.getByText("page")).toBeInTheDocument();
  });

  it("is absent for a member", () => {
    // given
    membership({ data: { role: "MEMBER" }, isError: false });

    // when
    renderFrame();

    // then
    expect(screen.queryByText(/Visiting/)).toBeNull();
    expect(screen.getByText("page")).toBeInTheDocument();
  });

  it("is absent while membership is still loading", () => {
    // given
    membership({ data: undefined, isError: false });

    // when
    renderFrame();

    // then
    expect(screen.queryByRole("button", { name: "Join" })).toBeNull();
  });

  it("still offers Join when the membership lookup failed", () => {
    // given
    membership({ data: undefined, isError: true });

    // when
    renderFrame();

    // then
    expect(screen.getByRole("button", { name: "Join" })).toBeInTheDocument();
  });

  it("departs for the full welcome when no page welcomes in place", async () => {
    // given
    renderFrame();

    // when
    await userEvent.click(screen.getByRole("button", { name: "Join" }));

    // then
    expect(depart).toHaveBeenCalledWith("welcome", "backend-guild");
  });

  it("hands the join to the page that registered an in-place welcome", async () => {
    // given
    const welcome = vi.fn();
    renderFrame(<HeroStandIn onJoined={welcome} />);

    // when
    await userEvent.click(screen.getByRole("button", { name: "Join" }));

    // then
    expect(welcome).toHaveBeenCalledOnce();
    expect(depart).not.toHaveBeenCalled();
  });

  it("falls back to departing once the registering page is gone", async () => {
    // given
    const welcome = vi.fn();
    const { rerender } = renderFrame(<HeroStandIn onJoined={welcome} />);
    rerender(
      <MemoryRouter>
        <VisitingFrame name="Backend Guild" slug="backend-guild">
          <p>page</p>
        </VisitingFrame>
      </MemoryRouter>,
    );

    // when
    await userEvent.click(screen.getByRole("button", { name: "Join" }));

    // then
    expect(welcome).not.toHaveBeenCalled();
    expect(depart).toHaveBeenCalledWith("welcome", "backend-guild");
  });

  it("runs the latest callback the page registered", async () => {
    // given
    const first = vi.fn();
    const latest = vi.fn();
    const { rerender } = renderFrame(<HeroStandIn onJoined={first} />);
    rerender(
      <MemoryRouter>
        <VisitingFrame name="Backend Guild" slug="backend-guild">
          <HeroStandIn onJoined={latest} />
        </VisitingFrame>
      </MemoryRouter>,
    );

    // when
    await userEvent.click(screen.getByRole("button", { name: "Join" }));

    // then
    expect(first).not.toHaveBeenCalled();
    expect(latest).toHaveBeenCalledOnce();
  });

  it("stays through the in-place welcome, then folds away", async () => {
    // given
    const { rerender } = renderFrame(<WelcomingHero />);
    await userEvent.click(screen.getByRole("button", { name: "Join" }));
    membership({ data: { role: "MEMBER" }, isError: false });
    rerender(
      <MemoryRouter>
        <VisitingFrame name="Backend Guild" slug="backend-guild">
          <WelcomingHero />
        </VisitingFrame>
      </MemoryRouter>,
    );
    const duringWelcome = barState();

    // when
    await userEvent.click(screen.getByRole("button", { name: "end welcome" }));

    // then
    expect(duringWelcome).toBe("open");
    expect(barState()).toBe("closed");
  });

  it("folds away rather than vanishing when the membership arrives", () => {
    // given
    const { rerender } = renderFrame();
    membership({ data: { role: "MEMBER" }, isError: false });

    // when
    rerender(
      <MemoryRouter>
        <VisitingFrame name="Backend Guild" slug="backend-guild">
          <p>page</p>
        </VisitingFrame>
      </MemoryRouter>,
    );

    // then
    expect(barState()).toBe("closed");
    expect(
      screen.getByRole("button", { name: "Join" }).closest("[inert]"),
    ).not.toBeNull();
  });
});
