import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { ExpertiseArea } from "@/features/expertise/domain/expertise";
import { CommunityKnows } from "@/features/expertise/ui/CommunityKnows";

vi.mock("@/features/expertise/api/use-expertise-areas", () => ({
  useExpertiseAreas: vi.fn(),
}));

vi.mock("@/features/expertise/ui/TagConstellation", () => ({
  TagConstellation: ({
    areas,
    onSelect,
  }: {
    areas: ExpertiseArea[];
    onSelect: (tag: string) => void;
  }) => (
    <div data-testid="constellation">
      {areas.map((a) => (
        <button
          key={a.canonicalTag}
          type="button"
          onClick={() => onSelect(a.canonicalTag)}
        >
          {a.canonicalTag}
        </button>
      ))}
    </div>
  ),
}));

import { useExpertiseAreas } from "@/features/expertise/api/use-expertise-areas";

const mockAreas = vi.mocked(useExpertiseAreas);

function area(over: Partial<ExpertiseArea> = {}): ExpertiseArea {
  return {
    canonicalTag: "kafka",
    variants: [],
    peopleCount: 4,
    ...over,
  };
}

function setAreas(state: Partial<ReturnType<typeof useExpertiseAreas>>): void {
  mockAreas.mockReturnValue({
    data: undefined,
    isLoading: false,
    isError: false,
    ...state,
  } as never);
}

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.clearAllMocks();
});

describe("CommunityKnows", () => {
  it("shows a loading hint while areas load", () => {
    setAreas({ isLoading: true });
    render(<CommunityKnows slug="eng" onSelect={vi.fn()} />);
    expect(screen.getByText(/loading/i)).toBeInTheDocument();
  });

  it("shows an error when areas fail to load", () => {
    setAreas({ isError: true });
    render(<CommunityKnows slug="eng" onSelect={vi.fn()} />);
    expect(screen.getByRole("alert")).toHaveTextContent(/could not load/i);
  });

  it("shows an empty state when the community knows nothing yet", () => {
    setAreas({ data: [] });
    render(<CommunityKnows slug="eng" onSelect={vi.fn()} />);
    expect(screen.getByText(/nothing shared yet/i)).toBeInTheDocument();
  });

  it("renders the constellation of topics when areas exist", () => {
    setAreas({
      data: [
        area({ canonicalTag: "kafka", peopleCount: 4 }),
        area({ canonicalTag: "hexagonal architecture", peopleCount: 6 }),
      ],
    });
    render(<CommunityKnows slug="eng" onSelect={vi.fn()} />);

    expect(screen.getByTestId("constellation")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "kafka" })).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "hexagonal architecture" }),
    ).toBeInTheDocument();
  });

  it("calls onSelect with the canonical tag when a topic is clicked", async () => {
    const onSelect = vi.fn();
    setAreas({ data: [area({ canonicalTag: "kafka" })] });
    render(<CommunityKnows slug="eng" onSelect={onSelect} />);

    await user.click(screen.getByRole("button", { name: /kafka/i }));

    expect(onSelect).toHaveBeenCalledWith("kafka");
  });
});
