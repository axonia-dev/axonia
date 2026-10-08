import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useLearningPage } from "@/features/learning/api/use-learning";
import type { LearningPage } from "@/features/learning/domain/learning";
import { LearnersBridge } from "@/features/learning/ui/LearnersBridge";

vi.mock("@/features/learning/api/use-learning", () => ({
  useLearningPage: vi.fn(),
}));

function page(overrides: Partial<LearningPage> | null) {
  vi.mocked(useLearningPage).mockReturnValue({
    data:
      overrides === null
        ? undefined
        : {
            member: true,
            archived: false,
            limit: 10,
            mine: [],
            wanted: [],
            ...overrides,
          },
  } as never);
}

function renderBridge(slug: string, term: string) {
  return render(
    <MemoryRouter>
      <LearnersBridge slug={slug} term={term} />
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe("LearnersBridge", () => {
  it("names how many want to learn the searched area and links to them", () => {
    // given
    page({ wanted: [{ tag: "kubernetes", learners: 12, canBeAsked: 1 }] });

    // when
    renderBridge("platform", "Kubernetes");

    // then
    expect(vi.mocked(useLearningPage)).toHaveBeenCalledWith("platform");
    expect(
      screen.getByRole("link", { name: "12 want to learn kubernetes" }),
    ).toHaveAttribute("href", "/communities/platform/learning?area=kubernetes");
  });

  it("says wants of a single learner", () => {
    // given
    page({ wanted: [{ tag: "rust", learners: 1, canBeAsked: 0 }] });

    // when
    renderBridge("platform", "rust");

    // then
    expect(
      screen.getByRole("link", { name: "1 wants to learn rust" }),
    ).toBeInTheDocument();
  });

  it("renders nothing when nobody wants it", () => {
    // given
    page({ wanted: [] });

    // when
    const { container } = renderBridge("platform", "kafka");

    // then
    expect(container).toBeEmptyDOMElement();
  });

  it("renders nothing while the learning page loads", () => {
    // given
    page(null);

    // when
    const { container } = renderBridge("platform", "kafka");

    // then
    expect(container).toBeEmptyDOMElement();
  });
});
