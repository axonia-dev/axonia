import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, waitFor } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type {
  OnboardingPath,
  OnboardingStep,
  OnboardingStepKey,
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

import { OnboardingAffordance } from "./OnboardingAffordance";
import { useVisitRecorder } from "./use-visit-recorder";

const gateway = vi.mocked(httpOnboardingGateway);

function step(
  key: OnboardingStepKey,
  visited: boolean,
  detail: string | null,
  route: string,
): OnboardingStep {
  return { key, route, detail, visited };
}

function unvisited(
  key: OnboardingStepKey,
  detail: string | null = null,
  route = `/communities/guild/${key.toLowerCase().replaceAll("_", "-")}`,
): OnboardingStep {
  return step(key, false, detail, route);
}

function visited(
  key: OnboardingStepKey,
  route = `/communities/guild/${key.toLowerCase().replaceAll("_", "-")}`,
): OnboardingStep {
  return step(key, true, null, route);
}

function pathWith(steps: OnboardingStep[]): OnboardingPath {
  return { variant: "MEMBER", communitySlug: "guild", steps };
}

function Recorder({ path }: { path: OnboardingPath }) {
  useVisitRecorder(path);
  return null;
}

function renderAt(
  location: string,
  path: OnboardingPath,
  visit: (key: OnboardingStepKey) => void,
) {
  gateway.visit.mockImplementation((key) => {
    visit(key);
    return Promise.resolve();
  });
  const client = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });
  return render(
    <MemoryRouter initialEntries={[location]}>
      <QueryClientProvider client={client}>
        <Recorder path={path} />
      </QueryClientProvider>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe("useVisitRecorder", () => {
  it("records a visit when the location equals an unvisited step's frozen route", async () => {
    const visit = vi.fn();
    renderAt(
      "/communities/guild/health",
      pathWith([unvisited("HEALTH")]),
      visit,
    );

    await waitFor(() => expect(visit).toHaveBeenCalledWith("HEALTH"));
  });

  it("matches the search string too, so a focused artifact is distinguishable", async () => {
    const visit = vi.fn();
    renderAt(
      "/communities/guild/library?focus=7",
      pathWith([
        // The prefix candidate is listed FIRST on purpose: `here` starts with this
        // step's route too, so only a strictly exact match (not `startsWith`,
        // `includes`, or a route-pattern match) skips it in favour of the step
        // below whose route matches the full location, search included.
        unvisited("LIBRARY", null, "/communities/guild/library"),
        unvisited(
          "MOST_LOVED_ARTIFACT",
          null,
          "/communities/guild/library?focus=7",
        ),
      ]),
      visit,
    );

    await waitFor(() =>
      expect(visit).toHaveBeenCalledExactlyOnceWith("MOST_LOVED_ARTIFACT"),
    );
  });

  it("cannot tick two session steps from one visit", async () => {
    const visit = vi.fn();
    renderAt(
      "/communities/guild/sessions/4",
      pathWith([
        unvisited("LAST_HELD_SESSION", null, "/communities/guild/sessions/3"),
        unvisited("NEXT_SESSION", null, "/communities/guild/sessions/4"),
      ]),
      visit,
    );

    await waitFor(() =>
      expect(visit).toHaveBeenCalledExactlyOnceWith("NEXT_SESSION"),
    );
  });

  it("records nothing for a step already visited", async () => {
    const visit = vi.fn();
    renderAt("/communities/guild/health", pathWith([visited("HEALTH")]), visit);

    // A synchronous assertion here is vacuous: the mutation fn is only reached after
    // react-query awaits, so `visit` would look uncalled at t=0 even if the effect had
    // wrongly fired. Flush past that before asserting the absence.
    await waitFor(() => expect(visit).not.toHaveBeenCalled());
    expect(gateway.visit).not.toHaveBeenCalled();
  });

  it("mounts the recorder once per shell even though the affordance renders in two header slots", async () => {
    const path = pathWith([unvisited("HEALTH")]);
    gateway.view.mockResolvedValue({ state: "OPEN", path });
    gateway.visit.mockResolvedValue(undefined);

    function ShellLike() {
      useVisitRecorder(path);
      return (
        <>
          <OnboardingAffordance />
          <OnboardingAffordance />
        </>
      );
    }

    const client = new QueryClient({
      defaultOptions: {
        queries: { retry: false },
        mutations: { retry: false },
      },
    });
    render(
      <MemoryRouter initialEntries={["/communities/guild/health"]}>
        <QueryClientProvider client={client}>
          <ShellLike />
        </QueryClientProvider>
      </MemoryRouter>,
    );

    // A `waitFor` that stops at its first passing check would report success the moment
    // the correct call appears, even while a second, wrongly-mounted recorder's own
    // mutation is still in flight - so settle first, then count.
    await waitFor(() => expect(gateway.visit).toHaveBeenCalled());
    await new Promise((resolve) => setTimeout(resolve, 50));

    expect(gateway.visit).toHaveBeenCalledExactlyOnceWith("HEALTH");
  });
});
