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
import { TIER_META } from "@/features/expertise/ui/tier-meta";
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
        <li
          key={a.key}
        >{`${a.label}|${a.reach ?? "-"}|${a.declared ? "d" : ""}${a.wantsToLearn ? "l" : ""}`}</li>
      ))}
    </ol>
  ),
}));

const mockMap = vi.mocked(useExpertiseMap);
const mockLearning = vi.mocked(useLearningPage);

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

// Below the vocabulary floor, so each area is its own axis.
const SMALL_MAP: TopicMap = {
  topics: ["kafka", "otel", "react", "go"].map(node),
  links: [],
  namedAreas: [],
};

function view(over: Partial<SelfView> = {}): SelfView {
  return {
    optedOut: false,
    areas: [
      {
        canonicalTag: "kafka",
        tier: "PRACTITIONER",
        hidden: false,
        communities: [PLATFORM, { id: 2, slug: "data", name: "Data Guild" }],
        communityTiers: [
          { communityId: 1, tier: "PRACTITIONER" },
          { communityId: 2, tier: "INTERESTED" },
        ],
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
    ...over,
  };
}

function givenMap(data: TopicMap | undefined) {
  mockMap.mockReturnValue({ data } as never);
}

function givenLearning(mine: { tag: string }[] | undefined) {
  mockLearning.mockReturnValue({
    data:
      mine === undefined
        ? undefined
        : { member: true, archived: false, limit: 10, mine, wanted: [] },
  } as never);
}

beforeEach(() => {
  givenMap(SMALL_MAP);
  givenLearning([]);
});

describe("ShapeSection", () => {
  it("titles the card with the community and draws its axes", () => {
    render(
      <ShapeSection view={view()} page={PLATFORM} lensId={ALL_COMMUNITIES} />,
    );
    expect(
      screen.getByRole("heading", { name: "Shape in Platform Guild" }),
    ).toBeInTheDocument();
    expect(screen.getByTestId("shape")).toHaveTextContent(
      "kafka|PRACTITIONER|",
    );
    expect(mockMap).toHaveBeenCalledWith("platform");
  });

  it("renders nothing with no community in scope", () => {
    const { container } = render(
      <ShapeSection view={view()} page={null} lensId={ALL_COMMUNITIES} />,
    );
    expect(container).toBeEmptyDOMElement();
  });

  it("renders nothing until the map is read, and when it fails", () => {
    givenMap(undefined);
    const { container } = render(
      <ShapeSection view={view()} page={PLATFORM} lensId={ALL_COMMUNITIES} />,
    );
    expect(container).toBeEmptyDOMElement();
  });

  it("renders nothing for an opted-out member", () => {
    const { container } = render(
      <ShapeSection
        view={view({ optedOut: true })}
        page={PLATFORM}
        lensId={ALL_COMMUNITIES}
      />,
    );
    expect(container).toBeEmptyDOMElement();
  });

  it("follows the lens to another community, its map and its tiers, never showing the previous community's axes", () => {
    mockMap.mockImplementation(
      (slug) => ({ data: slug === "data" ? undefined : SMALL_MAP }) as never,
    );
    const { container, rerender } = render(
      <ShapeSection view={view()} page={null} lensId={1} />,
    );
    expect(
      screen.getByRole("heading", { name: "Shape in Platform Guild" }),
    ).toBeInTheDocument();

    rerender(<ShapeSection view={view()} page={null} lensId={2} />);
    expect(mockMap).toHaveBeenLastCalledWith("data");
    expect(mockLearning).toHaveBeenLastCalledWith("data");
    expect(container).toBeEmptyDOMElement();

    mockMap.mockImplementation(() => ({ data: SMALL_MAP }) as never);
    rerender(<ShapeSection view={view()} page={null} lensId={2} />);
    expect(
      screen.getByRole("heading", { name: "Shape in Data Guild" }),
    ).toBeInTheDocument();
    expect(screen.getByTestId("shape")).toHaveTextContent("kafka|INTERESTED|");
  });

  it("marks what the member wants to learn and names what the map does not draw", () => {
    givenLearning([
      { tag: "go" },
      { tag: "threat-modeling" },
      { tag: "otel-tracing" },
    ]);
    render(
      <ShapeSection view={view()} page={PLATFORM} lensId={ALL_COMMUNITIES} />,
    );
    expect(screen.getByTestId("shape")).toHaveTextContent("go|-|l");
    expect(
      screen.getByText(/^Want to learn, not on the map:/),
    ).toHaveTextContent(
      "Want to learn, not on the map: otel-tracing, threat-modeling",
    );
  });

  it("keeps an unplaced tag whole where it fits and breaks only one wider than the line", () => {
    givenLearning([{ tag: "otel-tracing" }]);
    render(
      <ShapeSection view={view()} page={PLATFORM} lensId={ALL_COMMUNITIES} />,
    );
    const tag = screen.getByText("otel-tracing");
    expect(tag).toHaveClass("inline-block", "max-w-full", "wrap-anywhere");
    expect(tag).not.toHaveClass("whitespace-nowrap");
  });

  it("keeps each comma with the tag before it, so a wrapped tag never leaves one alone on a line", () => {
    givenLearning([{ tag: "otel-tracing" }, { tag: "threat-modeling" }]);
    render(
      <ShapeSection view={view()} page={PLATFORM} lensId={ALL_COMMUNITIES} />,
    );
    expect(screen.getByText("otel-tracing,")).toHaveClass("inline-block");
    expect(screen.getByText("threat-modeling")).toHaveClass("inline-block");
  });

  it("still draws when the learning read has failed", () => {
    givenLearning(undefined);
    render(
      <ShapeSection view={view()} page={PLATFORM} lensId={ALL_COMMUNITIES} />,
    );
    expect(screen.getByTestId("shape")).toHaveTextContent(
      "kafka|PRACTITIONER|",
    );
    expect(screen.queryByText(/not on the map/)).not.toBeInTheDocument();
  });

  it("carries the legend for the two marks", () => {
    render(
      <ShapeSection view={view()} page={PLATFORM} lensId={ALL_COMMUNITIES} />,
    );
    expect(screen.getByText("Open to questions")).toBeInTheDocument();
    expect(screen.getByText("Want to learn")).toBeInTheDocument();
  });

  it("paints the legend's dashed mark in the colour the drawing gives it", () => {
    const { container } = render(
      <ShapeSection view={view()} page={PLATFORM} lensId={ALL_COMMUNITIES} />,
    );
    expect(
      container.querySelector("[data-legend-mark='declared']"),
    ).toHaveAttribute("stroke", TIER_META.OPEN_TO_QUESTIONS.colour);
  });

  it("says the map does not group the areas yet, and draws nothing, when a large map is ungrouped", () => {
    givenMap({
      topics: Array.from({ length: 13 }, (_, i) =>
        node(i === 0 ? "kafka" : `tag-${i}`),
      ),
      links: [],
      namedAreas: [],
    });
    render(
      <ShapeSection view={view()} page={PLATFORM} lensId={ALL_COMMUNITIES} />,
    );
    expect(
      screen.getByText("The map does not group this community’s areas yet"),
    ).toBeInTheDocument();
    expect(screen.queryByTestId("shape")).not.toBeInTheDocument();
  });
});
