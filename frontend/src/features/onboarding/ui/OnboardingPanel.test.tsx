import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";

import type {
  OnboardingPath,
  OnboardingStep,
  OnboardingStepKey,
} from "@/features/onboarding/domain/onboarding";

import { OnboardingPanel } from "./OnboardingPanel";

function routeFor(key: OnboardingStepKey): string {
  return `/communities/guild/${key.toLowerCase().replaceAll("_", "-")}`;
}

function visited(
  key: OnboardingStepKey,
  detail: string | null = null,
): OnboardingStep {
  return { key, route: routeFor(key), detail, visited: true };
}

function unvisited(
  key: OnboardingStepKey,
  detail: string | null = null,
): OnboardingStep {
  return { key, route: routeFor(key), detail, visited: false };
}

function pathWith(
  steps: OnboardingStep[],
  variant: OnboardingPath["variant"] = "ORGANIZER",
): OnboardingPath {
  return { variant, communitySlug: "guild", steps };
}

function renderPanel(path: OnboardingPath, onDismiss: () => void = vi.fn()) {
  return render(
    <MemoryRouter>
      <OnboardingPanel path={path} onDismiss={onDismiss} />
    </MemoryRouter>,
  );
}

describe("OnboardingPanel", () => {
  it("strikes through a step already visited and offers it no link", () => {
    renderPanel(pathWith([visited("LIBRARY"), unvisited("HEALTH")]));

    const label = screen.getByText("Library");
    const row = label.closest("li");
    expect(label.closest("a")).toBeNull();
    expect(label).toHaveClass("line-through");
    expect(within(row!).getByText("Done.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /health/i })).toHaveAttribute(
      "href",
      "/communities/guild/health",
    );
  });

  it("names every step with the word the menu uses for the place it opens", () => {
    renderPanel(
      pathWith([
        unvisited("LOOK_AND_FEEL"),
        unvisited("FIRST_SESSION"),
        unvisited("FIRST_PEOPLE"),
      ]),
    );

    expect(screen.getByText("Look & feel")).toBeVisible();
    expect(screen.getByText("Schedule")).toBeVisible();
    expect(screen.getByText("Members & roles")).toBeVisible();
  });

  it("says what is kept in a place under its name", () => {
    renderPanel(pathWith([unvisited("BACKLOG")]));

    expect(
      screen.getByText("Requests, offers and actions, ranked by votes"),
    ).toBeVisible();
  });

  it("prefixes the frozen detail so two steps opening the same place read apart", () => {
    // Community-authored session titles, not product copy - the copy-voice guard only scans
    // shipped .ts/.tsx modules (see isShippedModule), never .test.tsx.
    renderPanel(
      pathWith(
        [
          unvisited("LAST_HELD_SESSION", "Runtime upgrades"),
          unvisited("NEXT_SESSION", "Build caches"),
        ],
        "MEMBER",
      ),
    );

    expect(screen.getAllByText("Schedule")).toHaveLength(2);
    expect(screen.getByText("Last session: Runtime upgrades")).toBeVisible();
    expect(screen.getByText("Next session: Build caches")).toBeVisible();
  });

  it("titles the panel by variant and counts what has been seen", () => {
    renderPanel(pathWith([visited("LIBRARY"), unvisited("HEALTH")]));

    expect(
      screen.getByRole("heading", { name: "Get the community going" }),
    ).toBeVisible();
    expect(screen.getByText("1 of 2 seen")).toBeVisible();
  });

  it("gives the member variant its own title", () => {
    renderPanel(
      pathWith([unvisited("TOP_TOPIC", "One build cache")], "MEMBER"),
    );

    expect(screen.getByRole("heading", { name: "The way in" })).toBeVisible();
  });

  it("offers Open on the first unvisited step alone, not on the ones behind it", () => {
    renderPanel(
      pathWith([
        visited("LOOK_AND_FEEL"),
        unvisited("BACKLOG"),
        unvisited("HEALTH"),
      ]),
    );

    const next = screen.getByRole("link", { name: /backlog/i });
    const later = screen.getByRole("link", { name: /health/i });
    expect(within(next).getByText("Open")).toBeInTheDocument();
    expect(within(later).queryByText("Open")).toBeNull();
  });

  it("closes the path when the dismiss is pressed", async () => {
    const close = vi.fn();
    renderPanel(pathWith([unvisited("HEALTH")]), close);

    await userEvent.click(
      screen.getByRole("button", { name: "Hide the getting-started steps" }),
    );

    expect(close).toHaveBeenCalledOnce();
  });
});
