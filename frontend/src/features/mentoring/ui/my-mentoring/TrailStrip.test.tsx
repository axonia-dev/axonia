import { fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useMe } from "@/features/identity/api/use-me";
import type { PairingSummary } from "@/features/mentoring/domain/mentoring";
import { pairingOf } from "@/features/mentoring/test-data";
import { TrailStrip } from "@/features/mentoring/ui/my-mentoring/TrailStrip";

vi.mock("@/features/identity/api/use-me", () => ({ useMe: vi.fn() }));

const TODAY = "2026-11-17";

const walking = pairingOf({
  stops: ["DONE", "DONE", "DONE", "IN_PROGRESS", "TODO", "TODO", "TODO"],
  nowTopic: "Giving feedback",
  nextMeeting: { on: "2026-11-19", passed: false },
  meetings: 4,
  notes: 6,
  lastMetOn: "2026-11-09",
});

function strip(
  pairing: PairingSummary,
  {
    together = null,
    arriving = false,
    onArrived = vi.fn(),
  }: {
    together?: number | null;
    arriving?: boolean;
    onArrived?: () => void;
  } = {},
) {
  return render(
    <MemoryRouter>
      <ul>
        <TrailStrip
          pairing={pairing}
          to="/me/mentoring/11"
          together={together}
          today={TODAY}
          arriving={arriving}
          onArrived={onArrived}
        />
      </ul>
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.mocked(useMe).mockReturnValue({ data: undefined } as never);
});

describe("TrailStrip", () => {
  it("is one link to the journal, named by the partner, the role and the community", () => {
    // when
    strip(walking);

    // then
    expect(screen.getAllByRole("link")).toHaveLength(1);
    expect(
      screen.getByRole("link", { name: "Ada, Mentor, Platform" }),
    ).toHaveAttribute("href", "/me/mentoring/11");
  });

  it("describes the link by its next meeting and how far its trail has come", () => {
    // when
    strip(walking);

    // then
    const link = screen.getByRole("link", { name: "Ada, Mentor, Platform" });
    expect(link).toHaveAccessibleDescription(/Thu 19 next meeting · in 2 days/);
    expect(link).toHaveAccessibleDescription(
      /3 of 7 topics reached, now Giving feedback/,
    );
  });

  it("leads with the next meeting and sums the walk up underneath", () => {
    // when
    strip(walking);

    // then
    expect(screen.getByText("Thu 19")).toBeInTheDocument();
    expect(screen.getByText("next meeting · in 2 days")).toBeInTheDocument();
    expect(screen.getByText("Now · Giving feedback")).toBeInTheDocument();
    expect(screen.getByText("3 of 7")).toBeInTheDocument();
    expect(screen.getByText("meetings")).toBeInTheDocument();
    expect(screen.getByText("last note 9 Nov")).toBeInTheDocument();
  });

  it("with no date set says when the pair last met", () => {
    // when
    strip({ ...walking, nextMeeting: null });

    // then
    expect(screen.getByText("No date set")).toHaveClass("text-warning-ink");
    expect(screen.getByText("last met 9 Nov")).toBeInTheDocument();
  });

  it("names a pair's third pairing together", () => {
    // when
    strip(walking, { together: 3 });

    // then
    expect(screen.getByText(/third pairing together/)).toBeInTheDocument();
  });

  it("says nothing of a first pairing together", () => {
    // when
    strip(walking, { together: 1 });

    // then
    expect(screen.queryByText(/pairing together/)).not.toBeInTheDocument();
  });

  it("a pairing with no topic and no note has just set out", () => {
    // when
    strip(pairingOf({ stops: [], nowTopic: null, meetings: 0, notes: 0 }));

    // then
    expect(screen.getByText(/just set out/)).toBeInTheDocument();
    expect(
      screen.getByText("No topics yet", { ignore: ".sr-only" }),
    ).toBeInTheDocument();
    expect(screen.getByText("meetings")).toBeInTheDocument();
    expect(screen.queryByText(/Now ·/)).not.toBeInTheDocument();
  });

  it("counts one meeting in the singular", () => {
    // when
    strip({ ...walking, meetings: 1 });

    // then
    expect(screen.getByText("meeting")).toBeInTheDocument();
  });

  it("marks the strip of a pairing just accepted", () => {
    // when
    strip(walking, { arriving: true });

    // then
    expect(screen.getByRole("listitem")).toHaveAttribute("data-arriving");
  });

  it("says its arrival has played once its own animation ends, not a child's", () => {
    // given
    const onArrived = vi.fn();
    strip(walking, { arriving: true, onArrived });
    const item = screen.getByRole("listitem");

    // when
    fireEvent.animationEnd(screen.getByRole("img"));
    const afterChild = onArrived.mock.calls.length;
    fireEvent.animationEnd(item);

    // then
    expect(afterChild).toBe(0);
    expect(onArrived).toHaveBeenCalledOnce();
  });
});
