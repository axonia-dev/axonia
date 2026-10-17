import { act, render, screen } from "@testing-library/react";
import { Profiler } from "react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { useCommunity } from "@/features/community/api/use-communities";
import { useMe } from "@/features/identity/api/use-me";
import {
  useMarkImpactSeen,
  useNewLoves,
} from "@/features/recognition/api/use-new-loves";
import { useMyContributions } from "@/features/recognition/api/use-recognition";
import type { RecognizedContribution } from "@/features/recognition/domain/recognition";
import { MyImpactView } from "@/features/recognition/ui/MyImpactView";

vi.mock("@/features/recognition/api/use-recognition", () => ({
  useMyContributions: vi.fn(),
}));
vi.mock("@/features/recognition/api/use-new-loves", () => ({
  useNewLoves: vi.fn(),
  useMarkImpactSeen: vi.fn(),
}));
vi.mock("@/features/identity/api/use-me", () => ({ useMe: vi.fn() }));
vi.mock("@/features/community/api/use-communities", () => ({
  useCommunity: vi.fn(),
}));
vi.mock("@/features/recognition/ui/comet-trail", () => ({
  centreOf: vi.fn(() => ({ x: 0, y: 0 })),
  flyComet: vi.fn(() => Promise.resolve()),
}));
vi.mock("@/features/recognition/ui/land-pulse", () => ({
  landPulse: vi.fn(),
}));

const RUST: RecognizedContribution = {
  contributionType: "SLOT_PRESENTED",
  contributionId: 1,
  communityId: 1,
  communitySlug: "eng",
  communityName: "Engineering",
  title: "Rust talk",
  occurredAt: "2026-06-01T00:00:00Z",
  loveCount: 7,
  sessionId: null,
  typeId: null,
  typeLabel: null,
  typeIcon: null,
  typeAccentColor: null,
};

const NEW_LOVES = {
  asOf: "2026-10-17T09:00:00Z",
  capped: false,
  items: [
    { contributionType: "SLOT_PRESENTED", contributionId: 1, newLoves: 2 },
  ],
};

function contributionsAre(loaded: boolean) {
  vi.mocked(useMyContributions).mockReturnValue({
    data: loaded ? [RUST] : undefined,
    isLoading: !loaded,
    errorUpdateCount: 0,
  } as never);
}

function newLovesAre(read: "pending" | "loaded" | "failed") {
  vi.mocked(useNewLoves).mockReturnValue({
    data: read === "loaded" ? NEW_LOVES : undefined,
    isPending: read === "pending",
    isError: read === "failed",
  } as never);
}

const shown: string[] = [];

function recordCount() {
  const count = document.querySelector(
    '[aria-label^="Appreciated by 7 people"]',
  )?.children[1]?.textContent;
  if (count !== undefined) shown.push(count);
}

function view() {
  return (
    <MemoryRouter>
      <Profiler id="impact" onRender={recordCount}>
        <MyImpactView />
      </Profiler>
    </MemoryRouter>
  );
}

beforeEach(() => {
  vi.useFakeTimers();
  vi.clearAllMocks();
  shown.length = 0;
  vi.spyOn(HTMLElement.prototype, "getBoundingClientRect").mockReturnValue({
    top: 200,
    bottom: 216,
    left: 0,
    right: 16,
    width: 16,
    height: 16,
  } as DOMRect);
  vi.mocked(useMe).mockReturnValue({
    data: { id: "u1", displayName: "Gabriel Dinant" },
  } as never);
  vi.mocked(useCommunity).mockReturnValue({ data: undefined } as never);
  vi.mocked(useMarkImpactSeen).mockReturnValue({ mutate: vi.fn() } as never);
});

afterEach(() => {
  vi.useRealTimers();
  vi.restoreAllMocks();
});

async function playOut() {
  await act(() => vi.advanceTimersByTimeAsync(250));
  await act(() => vi.advanceTimersByTimeAsync(3000));
}

describe("MyImpactView replay", () => {
  it("never shows the final count before the first tick when the new loves come last", async () => {
    contributionsAre(true);
    newLovesAre("pending");
    const { rerender } = render(view());
    expect(screen.getByText("Loading…")).toBeInTheDocument();

    newLovesAre("loaded");
    rerender(view());
    await playOut();

    expect(shown[0]).toBe("5");
    expect(shown).toContain("6");
    expect(shown.slice(0, shown.indexOf("6"))).not.toContain("7");
    expect(shown.at(-1)).toBe("7");
  });

  it("never shows the final count before the first tick when the contributions come last", async () => {
    contributionsAre(false);
    newLovesAre("loaded");
    const { rerender } = render(view());
    expect(screen.getByText("Loading…")).toBeInTheDocument();

    contributionsAre(true);
    rerender(view());
    await playOut();

    expect(shown[0]).toBe("5");
    expect(shown).toContain("6");
    expect(shown.slice(0, shown.indexOf("6"))).not.toContain("7");
  });

  it("releases the page as today when the new loves fail", async () => {
    contributionsAre(true);
    newLovesAre("pending");
    const { rerender } = render(view());

    newLovesAre("failed");
    rerender(view());
    await playOut();

    expect(new Set(shown)).toEqual(new Set(["7"]));
    expect(screen.queryByText("Loading…")).not.toBeInTheDocument();
  });
});
