import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type {
  OnboardingPath,
  OnboardingStep,
  OnboardingStepKey,
  OnboardingView,
} from "@/features/onboarding/domain/onboarding";

vi.mock("@/features/onboarding/adapters/http-onboarding-gateway", () => ({
  httpOnboardingGateway: {
    view: vi.fn(),
    assign: vi.fn(),
    visit: vi.fn(),
    close: vi.fn(),
  },
}));

import { httpOnboardingGateway } from "@/features/onboarding/adapters/http-onboarding-gateway";
import { onboardingKeys } from "@/features/onboarding/api/query-keys";

import { OnboardingAffordance } from "./OnboardingAffordance";

const gateway = vi.mocked(httpOnboardingGateway);

function step(key: OnboardingStepKey, visited: boolean): OnboardingStep {
  return {
    key,
    route: `/communities/guild/${key.toLowerCase().replaceAll("_", "-")}`,
    detail: null,
    visited,
  };
}

function unvisited(key: OnboardingStepKey): OnboardingStep {
  return step(key, false);
}

function visited(key: OnboardingStepKey): OnboardingStep {
  return step(key, true);
}

function pathWith(steps: OnboardingStep[]): OnboardingPath {
  return { variant: "MEMBER", communitySlug: "guild", steps };
}

function stubOnboarding(options: {
  loading?: boolean;
  error?: boolean;
  state?: OnboardingView["state"];
  path?: OnboardingPath | null;
}) {
  const {
    loading = false,
    error = false,
    state = "OPEN",
    path = null,
  } = options;
  if (loading) {
    gateway.view.mockReturnValue(new Promise<OnboardingView>(() => {}));
    return;
  }
  if (error) {
    gateway.view.mockRejectedValue(new Error("down"));
    return;
  }
  gateway.view.mockResolvedValue({ state, path });
}

function renderAffordance(ui: React.ReactElement) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  const view = render(
    <MemoryRouter>
      <QueryClientProvider client={client}>{ui}</QueryClientProvider>
    </MemoryRouter>,
  );
  return { ...view, client };
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe("OnboardingAffordance", () => {
  it("draws nothing at all while the path is still unknown", () => {
    stubOnboarding({ loading: true });
    renderAffordance(<OnboardingAffordance />);

    expect(
      screen.queryByRole("button", { name: /getting started/i }),
    ).toBeNull();
  });

  it("draws nothing once the path is closed", async () => {
    // A non-null path: this must fail on state alone, not on `path` also being null,
    // or deleting the `view.state !== "OPEN"` check from OnboardingAffordance would
    // still pass.
    stubOnboarding({
      state: "CLOSED",
      path: pathWith([unvisited("HEALTH")]),
    });
    const { client } = renderAffordance(<OnboardingAffordance />);

    // Settle the query before asserting absence: right after render() the query is
    // still pending, indistinguishable from the loading case above.
    await waitFor(() =>
      expect(client.getQueryState(onboardingKeys.view())?.status).toBe(
        "success",
      ),
    );

    expect(
      screen.queryByRole("button", { name: /getting started/i }),
    ).toBeNull();
  });

  it("draws nothing when the read failed, rather than an error", async () => {
    stubOnboarding({ error: true });
    const { client } = renderAffordance(<OnboardingAffordance />);

    await waitFor(() =>
      expect(client.getQueryState(onboardingKeys.view())?.status).toBe("error"),
    );

    expect(screen.queryByRole("alert")).toBeNull();
    expect(
      screen.queryByRole("button", { name: /getting started/i }),
    ).toBeNull();
  });

  it("names the progress in the trigger, not only on its face", async () => {
    stubOnboarding({
      state: "OPEN",
      path: pathWith([visited("LIBRARY"), unvisited("HEALTH")]),
    });
    renderAffordance(<OnboardingAffordance />);

    expect(
      await screen.findByRole("button", {
        name: "Getting started, 1 of 2 seen",
      }),
    ).toBeVisible();
  });

  it("marks the finish when the last step ticks the path shut in front of the person", async () => {
    stubOnboarding({
      state: "OPEN",
      path: pathWith([visited("LIBRARY"), unvisited("HEALTH")]),
    });
    const { client } = renderAffordance(<OnboardingAffordance />);
    await screen.findByRole("button", { name: /getting started/i });

    client.setQueryData(onboardingKeys.view(), {
      state: "COMPLETED",
      path: null,
    } satisfies OnboardingView);

    expect(await screen.findByRole("status")).toHaveTextContent("Good to go!");
    expect(
      screen.queryByRole("button", { name: /getting started/i }),
    ).toBeNull();
  });

  it("takes the finish chip away again rather than leaving it in the top bar", async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true });
    try {
      stubOnboarding({
        state: "OPEN",
        path: pathWith([unvisited("HEALTH")]),
      });
      const { client } = renderAffordance(<OnboardingAffordance />);
      await vi.waitFor(() =>
        expect(
          screen.getByRole("button", { name: /getting started/i }),
        ).toBeVisible(),
      );

      client.setQueryData(onboardingKeys.view(), {
        state: "COMPLETED",
        path: null,
      } satisfies OnboardingView);
      await vi.waitFor(() => expect(screen.getByRole("status")).toBeVisible());

      act(() => {
        vi.advanceTimersByTime(5000);
      });
      await vi.waitFor(() => expect(screen.queryByRole("status")).toBeNull());
    } finally {
      vi.useRealTimers();
    }
  });

  it("marks nothing for a path that was already completed when the page loaded", async () => {
    stubOnboarding({ state: "COMPLETED", path: null });
    const { client } = renderAffordance(<OnboardingAffordance />);

    await waitFor(() =>
      expect(client.getQueryState(onboardingKeys.view())?.status).toBe(
        "success",
      ),
    );

    expect(screen.queryByRole("status")).toBeNull();
  });

  it("congratulates nobody who hid the panel one step from the end", async () => {
    stubOnboarding({
      state: "OPEN",
      path: pathWith([visited("LIBRARY"), unvisited("HEALTH")]),
    });
    const { client } = renderAffordance(<OnboardingAffordance />);
    await screen.findByRole("button", { name: /getting started/i });

    client.setQueryData(onboardingKeys.view(), {
      state: "CLOSED",
      path: null,
    } satisfies OnboardingView);
    await waitFor(() =>
      expect(
        screen.queryByRole("button", { name: /getting started/i }),
      ).toBeNull(),
    );

    expect(screen.queryByRole("status")).toBeNull();
  });
});
