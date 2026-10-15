import { render, screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { AmbientFieldProps } from "@/components/patterns/ambient-field/AmbientField";
import { buildMotes } from "@/components/patterns/ambient-field/mote-field";
import type { Community } from "@/features/community/domain/community";
import {
  HeroAmbientField,
  toLight,
} from "@/features/community/ui/HeroAmbientField";
import { useActivityTrace } from "@/features/health/api/use-activity-trace";
import { visibleActsOf } from "@/features/health/domain/activity-trace";

vi.mock("@/features/health/api/use-activity-trace", () => ({
  useActivityTrace: vi.fn(),
}));

const fieldRenders = vi.fn<(props: AmbientFieldProps) => void>();
vi.mock("@/components/patterns/ambient-field/AmbientField", () => ({
  AmbientField: (props: AmbientFieldProps) => {
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

describe("HeroAmbientField", () => {
  it("draws one light per visible act", async () => {
    traceIs({ data: trace, isSuccess: true });
    render(<HeroAmbientField community={community} />);
    expect(await screen.findByTestId("field")).toHaveAttribute(
      "data-lights",
      "2",
    );
  });

  it("draws nothing while the trace is loading or failed", () => {
    traceIs({ data: undefined, isPending: true });
    const { container, rerender } = render(
      <HeroAmbientField community={community} />,
    );
    expect(container).toBeEmptyDOMElement();
    traceIs({ data: undefined, isError: true });
    rerender(<HeroAmbientField community={community} />);
    expect(container).toBeEmptyDOMElement();
  });

  it("neither reads the trace nor draws for an archived community", () => {
    traceIs({ data: trace, isSuccess: true });
    const { container } = render(
      <HeroAmbientField community={{ ...community, status: "ARCHIVED" }} />,
    );
    expect(container).toBeEmptyDOMElement();
    expect(useActivityTrace).toHaveBeenCalledWith("backend-guild", {
      enabled: false,
    });
  });

  it("inks a photo in light, the identity in the accent, on the light theme", async () => {
    traceIs({ data: trace, isSuccess: true });
    render(
      <HeroAmbientField
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
    render(<HeroAmbientField community={community} />);
    const field = await screen.findByTestId("field");
    await waitFor(() => expect(field).toHaveAttribute("data-upper", "light"));
    expect(field).toHaveAttribute("data-lower", "light");
  });

  it("hands the field the same references when a re-render changes nothing", async () => {
    traceIs({ data: trace, isSuccess: true });
    const { rerender } = render(<HeroAmbientField community={community} />);
    await screen.findByTestId("field");

    rerender(<HeroAmbientField community={{ ...community }} />);

    const [first, second] = fieldRenders.mock.calls
      .slice(-2)
      .map(([props]) => props);
    expect(second?.lights).toBe(first?.lights);
    expect(second?.inks).toBe(first?.inks);
    expect(second?.seam).toBe(first?.seam);
  });
});

describe("toLight", () => {
  it("names a light after its act, so it keeps its place as others come and go", () => {
    expect(toLight({ kind: "addition", daysAgo: 3, ordinal: 2 }, 90).key).toBe(
      "addition:3:2",
    );
  });

  it("puts today on the right and the window's first day on the left", () => {
    expect(
      toLight({ kind: "join", daysAgo: 0, ordinal: 0 }, 90).position,
    ).toBeGreaterThan(0.99);
    expect(
      toLight({ kind: "join", daysAgo: 89, ordinal: 0 }, 90).position,
    ).toBeLessThan(0.01);
  });

  it("sizes a session large, an addition medium and blooming, a join small", () => {
    expect(toLight({ kind: "session", daysAgo: 0, ordinal: 0 }, 90).size).toBe(
      "large",
    );
    expect(
      toLight({ kind: "addition", daysAgo: 0, ordinal: 0 }, 90),
    ).toMatchObject({
      size: "medium",
      blooms: true,
    });
    expect(toLight({ kind: "join", daysAgo: 0, ordinal: 0 }, 90).size).toBe(
      "small",
    );
  });
});

describe("a new act on the sky", () => {
  const sky = (joinsToday: number) => {
    const joins = zeroes();
    joins[60] = 4;
    joins[89] = joinsToday;
    const lights = visibleActsOf({ ...trace, joins }).map((act) =>
      toLight(act, 90),
    );
    const motes = buildMotes({ lights, ambient: 22, seed: 5 });
    return new Map(lights.map((light, index) => [light.key, motes.at(index)]));
  };

  it("leaves the motes already shown where they were", () => {
    const before = sky(2);
    const after = sky(3);

    expect(after.size).toBe(before.size + 1);
    for (const [key, mote] of before) {
      expect(after.get(key)).toEqual(mote);
    }
  });
});
