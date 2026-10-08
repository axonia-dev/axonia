import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
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

import { OnboardingChip } from "./OnboardingChip";

function routeFor(key: OnboardingStepKey): string {
  return `/communities/guild/${key.toLowerCase().replaceAll("_", "-")}`;
}

function step(key: OnboardingStepKey, visited: boolean): OnboardingStep {
  return { key, route: routeFor(key), detail: null, visited };
}

function pathWith(steps: OnboardingStep[]): OnboardingPath {
  return { variant: "ORGANIZER", communitySlug: "guild", steps };
}

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return (
    <MemoryRouter>
      <QueryClientProvider client={client}>{children}</QueryClientProvider>
    </MemoryRouter>
  );
}

function renderChip(path: OnboardingPath) {
  return render(<OnboardingChip path={path} />, { wrapper });
}

beforeEach(() => {
  vi.mocked(httpOnboardingGateway.close).mockReset();
});

describe("OnboardingChip", () => {
  it("names the trigger with the visited count", () => {
    renderChip(
      pathWith([
        step("LOOK_AND_FEEL", true),
        step("BACKLOG", false),
        step("FIRST_PEOPLE", false),
      ]),
    );

    expect(
      screen.getByRole("button", { name: "Getting started, 1 of 3 seen" }),
    ).toBeInTheDocument();
  });

  it("opens a dialog with a step label when the trigger is pressed", async () => {
    const user = userEvent.setup({ delay: null });
    renderChip(
      pathWith([
        step("LOOK_AND_FEEL", true),
        step("BACKLOG", false),
        step("FIRST_PEOPLE", false),
      ]),
    );

    await user.click(
      screen.getByRole("button", { name: "Getting started, 1 of 3 seen" }),
    );

    expect(
      await screen.findByRole("heading", { name: "Get the community going" }),
    ).toBeInTheDocument();
    expect(screen.getByText("Backlog")).toBeInTheDocument();
  });
});
