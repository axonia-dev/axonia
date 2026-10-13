import {
  QueryClient,
  QueryClientProvider,
  useQuery,
} from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";

import { communityKeys } from "@/features/community/api/query-keys";
import { useHeroCeremony } from "@/features/community/ui/ceremony/use-hero-ceremony";
import { JoinAction } from "@/features/community/ui/JoinAction";
import { jsonResponse } from "@/test/fetch";

const membership = {
  id: 9,
  communityId: 1,
  userId: "alice",
  role: "MEMBER",
  joinedAt: "2026-05-31T10:00:00Z",
  updatedAt: "2026-05-31T10:00:00Z",
};

function stubNetwork() {
  let answerJoin: () => void = () => {};
  vi.spyOn(globalThis, "fetch").mockImplementation((_input, init) => {
    if (init?.method === "POST") {
      return new Promise((resolve) => {
        answerJoin = () => resolve(jsonResponse(membership));
      });
    }
    return new Promise(() => undefined);
  });
  return { answerJoin: () => answerJoin() };
}

function WatchedCommunity() {
  useQuery({
    queryKey: communityKeys.detail("backend-guild"),
    queryFn: () => fetch("/axonia/communities/backend-guild").then(() => null),
  });
  return null;
}

function Hero({ memberCount }: Readonly<{ memberCount: number }>) {
  const ceremony = useHeroCeremony({
    slug: "backend-guild",
    name: "Backend Guild",
    memberCount,
  });
  return (
    <>
      <WatchedCommunity />
      <JoinAction slug="backend-guild" onJoined={ceremony.welcome} />
      <output>
        {ceremony.active === null
          ? "idle"
          : `${ceremony.active.countFrom} to ${ceremony.active.countTo}`}
      </output>
    </>
  );
}

function renderHero(memberCount: number) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  const tree = (count: number) => (
    <QueryClientProvider client={client}>
      <Hero memberCount={count} />
    </QueryClientProvider>
  );
  const view = render(tree(memberCount));
  return { ...view, showCount: (count: number) => view.rerender(tree(count)) };
}

afterEach(() => {
  vi.restoreAllMocks();
});

describe("JoinAction ordering against the real join", () => {
  it("starts the welcome when the join succeeds, while the refetches are still pending", async () => {
    // given
    const network = stubNetwork();
    renderHero(42);
    await userEvent.click(screen.getByRole("button", { name: "Join" }));

    // when
    network.answerJoin();

    // then
    expect(await screen.findByText("42 to 43")).toBeInTheDocument();
  });

  it("counts from the number the member saw when pressing, not from a refetched number", async () => {
    // given
    const network = stubNetwork();
    const hero = renderHero(42);
    await userEvent.click(screen.getByRole("button", { name: "Join" }));
    hero.showCount(43);

    // when
    network.answerJoin();

    // then
    expect(await screen.findByText("42 to 43")).toBeInTheDocument();
  });

  it("still starts the welcome when the button is gone by the time the join succeeds", async () => {
    // given
    const network = stubNetwork();
    const onJoined = vi.fn();
    const client = new QueryClient();
    const view = render(
      <QueryClientProvider client={client}>
        <JoinAction slug="backend-guild" onJoined={onJoined} />
      </QueryClientProvider>,
    );
    await userEvent.click(screen.getByRole("button", { name: "Join" }));
    view.unmount();

    // when
    network.answerJoin();

    // then
    await waitFor(() => expect(onJoined).toHaveBeenCalledOnce());
  });
});
