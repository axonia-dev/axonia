import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useParams } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useRemoveDeclaration } from "@/features/expertise/api/use-declarations";
import {
  useDismissSignal,
  useHideTag,
  useSetOptOut,
  useUnhideTag,
} from "@/features/expertise/api/use-expertise-prefs";
import { useSelfView } from "@/features/expertise/api/use-self-view";
import type { SelfArea } from "@/features/expertise/domain/expertise";
import { MyExpertiseView } from "@/features/expertise/ui/MyExpertiseView";
import { useMe } from "@/features/identity/api/use-me";

vi.mock("@/features/expertise/api/use-self-view", () => ({
  useSelfView: vi.fn(),
}));
vi.mock("@/features/expertise/api/use-expertise-prefs", () => ({
  useSetOptOut: vi.fn(),
  useHideTag: vi.fn(),
  useUnhideTag: vi.fn(),
  useDismissSignal: vi.fn(),
}));
vi.mock("@/features/identity/api/use-me", () => ({ useMe: vi.fn() }));
vi.mock("react-router-dom", async (importOriginal) => ({
  ...(await importOriginal<typeof import("react-router-dom")>()),
  useParams: vi.fn(() => ({})),
}));
vi.mock("@/features/expertise/api/use-declarations", () => ({
  useRemoveDeclaration: vi.fn(),
}));
vi.mock("@/features/expertise/ui/ShapeSection", () => ({
  ShapeSection: () => null,
}));

const mockUseSelfView = vi.mocked(useSelfView);
const mockUseSetOptOut = vi.mocked(useSetOptOut);
const mockUseHideTag = vi.mocked(useHideTag);
const mockUseUnhideTag = vi.mocked(useUnhideTag);
const mockUseDismissSignal = vi.mocked(useDismissSignal);
const mockUseMe = vi.mocked(useMe);
const mockUseParams = vi.mocked(useParams);
const mockUseRemoveDeclaration = vi.mocked(useRemoveDeclaration);

function mutation(mutate = vi.fn()) {
  return { mutate, isPending: false } as never;
}

// AreaRow's evidence panel pages older proof via useAreaEvidence
// (useInfiniteQuery), which needs a QueryClientProvider even when disabled.
function renderView() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <MyExpertiseView />
    </QueryClientProvider>,
  );
}

const AREA: SelfArea = {
  canonicalTag: "typescript",
  tier: "PRACTITIONER",
  hidden: false,
  communities: [{ id: 1, slug: "engineering", name: "Engineering" }],
  evidence: {
    presentedCount: 1,
    contributedCount: 2,
    offeredCount: 0,
    recent: [
      {
        signalType: "PRESENTED" as const,
        sourceType: "SLOT" as const,
        sourceId: 9,
        label: "Intro to TS",
        communitySlug: null,
        occurredAt: "2026-01-01T00:00:00Z",
        sessionId: null,
        typeId: null,
        typeLabel: null,
        typeIcon: null,
        typeAccentColor: null,
      },
    ],
  },
  communityTiers: [],
};

const PYTHON_AREA: SelfArea = {
  canonicalTag: "python",
  tier: "CONTRIBUTOR",
  hidden: false,
  communities: [{ id: 2, slug: "data", name: "Data" }],
  evidence: {
    presentedCount: 0,
    contributedCount: 1,
    offeredCount: 0,
    recent: [
      {
        signalType: "CONTRIBUTED" as const,
        sourceType: "ARTIFACT" as const,
        sourceId: 5,
        label: "Data pipeline notes",
        communitySlug: null,
        occurredAt: "2026-02-01T00:00:00Z",
        sessionId: null,
        typeId: null,
        typeLabel: null,
        typeIcon: null,
        typeAccentColor: null,
      },
    ],
  },
  communityTiers: [],
};

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.clearAllMocks();
  mockUseSelfView.mockReturnValue({
    data: {
      optedOut: false,
      areas: [AREA],
      declarations: [],
      declarableCommunities: [],
    },
    isLoading: false,
  } as never);
  mockUseSetOptOut.mockReturnValue(mutation());
  mockUseHideTag.mockReturnValue(mutation());
  mockUseUnhideTag.mockReturnValue(mutation());
  mockUseDismissSignal.mockReturnValue(mutation());
  mockUseRemoveDeclaration.mockReturnValue({
    mutate: vi.fn(),
    isRemoving: () => false,
  } as never);
  mockUseParams.mockReturnValue({});
  mockUseMe.mockReturnValue({
    data: { id: "u1", displayName: "Gabriel Dinant" },
  } as never);
});

describe("MyExpertiseView", () => {
  it("shows a loading state", () => {
    mockUseSelfView.mockReturnValue({
      isLoading: true,
      data: undefined,
    } as never);
    renderView();
    expect(screen.getByText(/loading/i)).toBeInTheDocument();
  });

  it("renders the profile header with the standing sentence", () => {
    renderView();
    expect(screen.getByText("Gabriel Dinant")).toBeInTheDocument();
    expect(screen.getByText("Practitioner in 1 area.")).toBeInTheDocument();
  });

  it("hides an area via the eye control", async () => {
    const mutate = vi.fn();
    mockUseHideTag.mockReturnValue(mutation(mutate));
    renderView();

    await user.click(screen.getByRole("button", { name: "Hide typescript" }));
    expect(mutate).toHaveBeenCalledWith("typescript");
  });

  it("opts out through the visibility menu", async () => {
    const mutate = vi.fn();
    mockUseSetOptOut.mockReturnValue(mutation(mutate));
    renderView();

    await user.click(
      screen.getByRole("button", { name: /visible to members/i }),
    );
    await user.click(screen.getByRole("button", { name: /^opt out$/i }));
    expect(mutate).toHaveBeenCalledWith(true);
  });

  it("dismisses a signal from the evidence panel", async () => {
    const mutate = vi.fn();
    mockUseDismissSignal.mockReturnValue(mutation(mutate));
    renderView();

    await user.click(screen.getByRole("button", { name: "Show evidence" }));
    await user.click(
      screen.getByRole("button", { name: "Dismiss Presented: Intro to TS" }),
    );
    expect(mutate).toHaveBeenCalledWith({
      sourceType: "SLOT",
      sourceId: 9,
      tag: "typescript",
    });
  });

  it("shows the empty state when there are no areas", () => {
    mockUseSelfView.mockReturnValue({
      data: {
        optedOut: false,
        areas: [],
        declarations: [],
        declarableCommunities: [],
      },
      isLoading: false,
    } as never);
    renderView();
    expect(screen.getByText("No expertise areas yet")).toBeInTheDocument();
  });

  describe("with areas across multiple communities", () => {
    beforeEach(() => {
      mockUseSelfView.mockReturnValue({
        data: {
          optedOut: false,
          areas: [AREA, PYTHON_AREA],
          declarations: [],
          declarableCommunities: [],
        },
        isLoading: false,
      } as never);
    });

    it("renders the community select once areas span more than one community", () => {
      renderView();
      expect(
        screen.getByRole("button", { name: /community/i }),
      ).toBeInTheDocument();
    });

    it("narrows areas across lanes when typing in search", async () => {
      renderView();
      expect(screen.getByText("typescript")).toBeInTheDocument();
      expect(screen.getByText("python")).toBeInTheDocument();

      await user.type(
        screen.getByRole("searchbox", { name: /search expertise/i }),
        "python",
      );

      expect(screen.queryByText("typescript")).not.toBeInTheDocument();
      expect(screen.getByText("python")).toBeInTheDocument();
    });

    it("hides talk-less areas when toggling Has talks", async () => {
      renderView();
      await user.click(screen.getByRole("button", { name: /has talks/i }));

      expect(screen.getByText("typescript")).toBeInTheDocument();
      expect(screen.queryByText("python")).not.toBeInTheDocument();
    });

    it("shows a filtered-empty state with a working clear action, and keeps the header standing unchanged while filtered", async () => {
      renderView();
      const standing = "Practitioner in 1 area and growing in 1 more.";
      expect(screen.getByText(standing)).toBeInTheDocument();

      await user.type(
        screen.getByRole("searchbox", { name: /search expertise/i }),
        "zzz",
      );

      expect(
        screen.getByText(/no areas match these filters/i),
      ).toBeInTheDocument();
      expect(screen.getByText(standing)).toBeInTheDocument();

      await user.click(screen.getByRole("button", { name: /clear filters/i }));

      expect(screen.getByText("typescript")).toBeInTheDocument();
      expect(screen.getByText("python")).toBeInTheDocument();
      expect(screen.getByText(standing)).toBeInTheDocument();
    });
  });
});
