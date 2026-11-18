import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useExpertiseMap } from "@/features/expertise/api/use-expertise-map";
import { ALL_COMMUNITIES } from "@/features/expertise/domain/area-filter";
import type {
  SelfView,
  TopicMap,
  TopicNode,
} from "@/features/expertise/domain/expertise";
import type { ShapeAxis } from "@/features/expertise/domain/practitioner-shape";
import { ShapeSection } from "@/features/expertise/ui/ShapeSection";
import { useLearningPage } from "@/features/learning/api/use-learning";

vi.mock("@/features/expertise/api/use-expertise-map", () => ({
  useExpertiseMap: vi.fn(),
}));
vi.mock("@/features/learning/api/use-learning", () => ({
  useLearningPage: vi.fn(),
}));
vi.mock("@/features/expertise/ui/PractitionerShape", () => ({
  PractitionerShape: ({ axes }: { axes: ShapeAxis[] }) => (
    <ol data-testid="shape">
      {axes.map((a) => (
        <li key={a.key}>{a.label}</li>
      ))}
    </ol>
  ),
}));

const PLATFORM = { id: 1, slug: "platform", name: "Platform Guild" };

function node(canonicalTag: string): TopicNode {
  return {
    canonicalTag,
    practitionerCount: 1,
    signalCount: 1,
    topHolderShare: 1,
    topHolderName: "Ada",
    presentedCount: 1,
    contributedCount: 0,
    offeredCount: 0,
    lastActivityAt: "2026-09-01T00:00:00Z",
    openToQuestionsCount: 0,
  };
}

const VIEW: SelfView = {
  optedOut: false,
  paused: false,
  areas: [
    {
      canonicalTag: "kafka",
      tier: "PRACTITIONER",
      hidden: false,
      communities: [PLATFORM],
      communityTiers: [{ communityId: 1, tier: "PRACTITIONER" }],
      evidence: {
        presentedCount: 1,
        contributedCount: 0,
        offeredCount: 0,
        recent: [],
      },
    },
  ],
  declarations: [],
  declarableCommunities: [],
};

const NAMED_TAGS = Array.from({ length: 14 }, (_, i) => `area-${i}`);

function givenMap(data: TopicMap) {
  vi.mocked(useExpertiseMap).mockReturnValue({ data } as never);
}

function renderSection() {
  render(<ShapeSection view={VIEW} page={PLATFORM} lensId={ALL_COMMUNITIES} />);
}

describe("ShapeSection grouping", () => {
  beforeEach(() => {
    vi.mocked(useLearningPage).mockReturnValue({ data: undefined } as never);
  });

  it("says the map does not group the areas yet, and draws nothing, when a large map is ungrouped", () => {
    givenMap({
      topics: Array.from({ length: 13 }, (_, i) =>
        node(i === 0 ? "kafka" : `tag-${i}`),
      ),
      links: [],
      namedAreas: [],
    });
    renderSection();
    expect(
      screen.getByText("The map does not group this community’s areas yet"),
    ).toBeInTheDocument();
    expect(screen.queryByTestId("shape")).not.toBeInTheDocument();
  });

  it("draws the named areas and names a held area no group holds", () => {
    givenMap({
      topics: [...NAMED_TAGS, "kafka", "mailgun"].map(node),
      links: [],
      namedAreas: NAMED_TAGS.map((tag, i) => ({
        id: i + 1,
        name: `Named ${i}`,
        tags: [tag],
      })),
    });
    renderSection();
    expect(screen.getByTestId("shape").children).toHaveLength(14);
    expect(screen.getByText(/In no group:/)).toHaveTextContent(
      "In no group: kafka",
    );
    expect(
      screen.queryByText(/does not group this community/),
    ).not.toBeInTheDocument();
    expect(screen.queryByText(/Nothing drawn yet/)).not.toBeInTheDocument();
  });
});
