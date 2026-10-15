import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { Community } from "@/features/community/domain/community";
import {
  HeroPulseField,
  toLight,
} from "@/features/community/ui/HeroPulseField";
import { useActivityTrace } from "@/features/health/api/use-activity-trace";

vi.mock("@/features/health/api/use-activity-trace", () => ({
  useActivityTrace: vi.fn(),
}));

const fieldRenders = vi.fn();
vi.mock("@/components/patterns/ambient-field/AmbientField", () => ({
  AmbientField: (props: {
    lights: unknown[];
    inks: { upper: string; lower: string };
  }) => {
    fieldRenders(props);
    return (
      <div
        data-testid="field"
        data-lights={props.lights.length}
        data-upper={props.inks.upper}
        data-lower={props.inks.lower}
      />
    );
  },
}));

const zeroes = () => Array.from({ length: 90 }, () => 0);
const trace = {
  days: 90,
  sessionsHeld: zeroes(),
  libraryAdditions: zeroes(),
  joins: zeroes(),
};
trace.joins[89] = 2;

const community: Community = {
  id: 1,
  slug: "backend-guild",
  name: "Backend Guild",
  kindId: 1,
  kind: "Guild",
  description: "",
  status: "ACTIVE",
  memberCount: 120,
  badgeUrl: null,
  heroUrl: null,
  accentColor: "#6e3ceb",
  createdAt: "2026-05-31T10:00:00Z",
  updatedAt: "2026-05-31T10:00:00Z",
};

function traceIs(value: object) {
  vi.mocked(useActivityTrace).mockReturnValue(value as never);
}

beforeEach(() => {
  vi.clearAllMocks();
  document.documentElement.classList.remove("theme-dark");
});

describe("HeroPulseField", () => {
  it("draws one light per visible act", async () => {
    traceIs({ data: trace, isSuccess: true });
    render(<HeroPulseField community={community} />);
    expect(await screen.findByTestId("field")).toHaveAttribute(
      "data-lights",
      "2",
    );
  });

  it("draws nothing while the trace is loading or failed", () => {
    traceIs({ data: undefined, isPending: true });
    const { container, rerender } = render(
      <HeroPulseField community={community} />,
    );
    expect(container).toBeEmptyDOMElement();
    traceIs({ data: undefined, isError: true });
    rerender(<HeroPulseField community={community} />);
    expect(container).toBeEmptyDOMElement();
  });

  it("draws nothing for an archived community", () => {
    traceIs({ data: trace, isSuccess: true });
    const { container } = render(
      <HeroPulseField community={{ ...community, status: "ARCHIVED" }} />,
    );
    expect(container).toBeEmptyDOMElement();
  });

  it("inks a photo in light, the identity in the accent, on the light theme", async () => {
    traceIs({ data: trace, isSuccess: true });
    render(
      <HeroPulseField
        community={{ ...community, heroUrl: "https://x/y.jpg" }}
      />,
    );
    const field = await screen.findByTestId("field");
    expect(field).toHaveAttribute("data-upper", "light");
    expect(field).toHaveAttribute("data-lower", "accent");
  });

  it("inks everything in light on the dark theme", async () => {
    document.documentElement.classList.add("theme-dark");
    traceIs({ data: trace, isSuccess: true });
    render(<HeroPulseField community={community} />);
    const field = await screen.findByTestId("field");
    await waitFor(() => expect(field).toHaveAttribute("data-upper", "light"));
    expect(field).toHaveAttribute("data-lower", "light");
  });

  it("hands the field the same references when a re-render changes nothing", async () => {
    traceIs({ data: trace, isSuccess: true });
    const { rerender } = render(<HeroPulseField community={community} />);
    await screen.findByTestId("field");

    rerender(<HeroPulseField community={{ ...community }} />);

    const [first, second] = fieldRenders.mock.calls.slice(-2).map(([p]) => p);
    expect(second.lights).toBe(first.lights);
    expect(second.inks).toBe(first.inks);
    expect(second.seam).toBe(first.seam);
  });
});

describe("toLight", () => {
  it("puts today on the right and the window's first day on the left", () => {
    expect(toLight({ kind: "join", daysAgo: 0 }, 90).position).toBeGreaterThan(
      0.99,
    );
    expect(toLight({ kind: "join", daysAgo: 89 }, 90).position).toBeLessThan(
      0.01,
    );
  });

  it("sizes a session large, an addition medium and blooming, a join small", () => {
    expect(toLight({ kind: "session", daysAgo: 0 }, 90).size).toBe("large");
    expect(toLight({ kind: "addition", daysAgo: 0 }, 90)).toMatchObject({
      size: "medium",
      blooms: true,
    });
    expect(toLight({ kind: "join", daysAgo: 0 }, 90).size).toBe("small");
  });
});
