import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";

import type { RecognizedContribution } from "@/features/recognition/domain/recognition";
import { ContributionCard } from "@/features/recognition/ui/ContributionCard";
import type { CardReplay } from "@/features/recognition/ui/use-impact-replay";

function contribution(
  over: Partial<RecognizedContribution>,
): RecognizedContribution {
  return {
    contributionType: "SLOT_PRESENTED",
    contributionId: 9,
    communityId: 1,
    communitySlug: "eng",
    communityName: "Engineering",
    title: "Resilience talk",
    occurredAt: "2026-06-01T00:00:00Z",
    loveCount: 7,
    sessionId: null,
    typeId: null,
    typeLabel: null,
    typeIcon: null,
    typeAccentColor: null,
    ...over,
  };
}

function renderCard(
  over: Partial<RecognizedContribution>,
  replay?: CardReplay,
  heartRef?: (element: HTMLElement | null) => void,
) {
  render(
    <MemoryRouter>
      <ContributionCard
        contribution={contribution(over)}
        replay={replay}
        heartRef={heartRef}
      />
    </MemoryRouter>,
  );
}

const countOf = () => document.querySelector("[data-love-count]");

describe("ContributionCard", () => {
  it("links a talk to the session it happened in", () => {
    renderCard({
      contributionType: "SLOT_PRESENTED",
      communitySlug: "eng",
      sessionId: 42,
    });
    expect(
      screen.getByRole("link", { name: "Resilience talk" }),
    ).toHaveAttribute("href", "/communities/eng/sessions/42");
  });

  it("falls back to the schedule when the talk's session is unknown", () => {
    renderCard({
      contributionType: "SLOT_PRESENTED",
      communitySlug: "eng",
      sessionId: null,
    });
    expect(
      screen.getByRole("link", { name: "Resilience talk" }),
    ).toHaveAttribute("href", "/communities/eng/schedule");
  });

  it("filters the library to just the loved artifact by title", () => {
    renderCard({
      contributionType: "ARTIFACT_CAPTURED",
      communitySlug: "eng",
      title: "Runbook v2",
    });
    expect(screen.getByRole("link", { name: "Runbook v2" })).toHaveAttribute(
      "href",
      "/communities/eng/library?q=Runbook%20v2",
    );
  });

  it("shows the real type label instead of the generic kind", () => {
    renderCard({ typeId: 3, typeLabel: "Workshop" });
    expect(screen.getByText("Workshop")).toBeInTheDocument();
  });

  it("renders a plain title (no link) when there is no slug", () => {
    renderCard({ communitySlug: null });
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
    expect(screen.getByText("Resilience talk")).toBeInTheDocument();
  });

  it("shows the love count with the app-wide heart reaction", () => {
    renderCard({ loveCount: 7 });
    expect(countOf()).toHaveTextContent("7");
    expect(screen.getByText("Appreciated by 7 people")).toHaveClass("sr-only");
  });

  it("pluralises the spoken sentence to the singular for one love", () => {
    renderCard({ loveCount: 1 });
    expect(screen.getByText("Appreciated by 1 person")).toBeInTheDocument();
  });

  it("names the line in text rather than an aria-label a paragraph cannot carry", () => {
    renderCard({ loveCount: 7 });
    const line = screen.getByText("Appreciated by 7 people").parentElement!;
    expect(line).not.toHaveAttribute("aria-label");
    const visible = [...line.children].filter(
      (child) => !child.classList.contains("sr-only"),
    );
    visible.forEach((child) =>
      expect(child).toHaveAttribute("aria-hidden", "true"),
    );
  });

  it("omits the reaction line when there is none yet", () => {
    renderCard({ loveCount: 0 });
    expect(screen.queryByText(/appreciated by/i)).not.toBeInTheDocument();
  });

  describe("during the replay", () => {
    it("holds back the pending loves while saying the final count", () => {
      renderCard({ loveCount: 7 }, { pending: 2, newLoves: 2, marked: false });
      expect(countOf()).toHaveTextContent("5");
      expect(screen.getByText("Appreciated by 7 people")).toBeInTheDocument();
      expect(screen.queryByText("+2")).not.toBeInTheDocument();
      expect(screen.getByRole("listitem")).toHaveClass("border-border");
    });

    it("follows a refetched total instead of freezing the seeded one", () => {
      renderCard({ loveCount: 9 }, { pending: 1, newLoves: 2, marked: true });
      expect(countOf()).toHaveTextContent("8");
      expect(
        screen.getByText("Appreciated by 9 people, 2 new"),
      ).toBeInTheDocument();
    });

    it("marks the card fresh with its new loves once marked", () => {
      renderCard({ loveCount: 7 }, { pending: 0, newLoves: 2, marked: true });
      expect(screen.getByText("Appreciated by 7 people, 2 new")).toHaveClass(
        "sr-only",
      );
      expect(screen.getByText("+2")).toHaveAttribute("aria-hidden", "true");
      expect(screen.getByRole("listitem")).toHaveClass(
        "border-primary/35",
        "ring-3",
        "ring-primary/10",
      );
    });

    it("names a single new love in the singular", () => {
      renderCard({ loveCount: 1 }, { pending: 0, newLoves: 1, marked: true });
      expect(
        screen.getByText("Appreciated by 1 person, 1 new"),
      ).toBeInTheDocument();
    });

    it("hands its heart over as the trail origin", () => {
      const heartRef = vi.fn();
      renderCard({ loveCount: 7 }, undefined, heartRef);
      expect(heartRef).toHaveBeenCalledWith(
        screen.getByText("Appreciated by 7 people").parentElement!
          .firstElementChild,
      );
    });
  });

  describe("count motion", () => {
    const POP = /animate-\[count-pop/;

    it("renders a card outside the replay without the pop", () => {
      renderCard({ loveCount: 7 });
      expect(countOf()?.outerHTML).not.toMatch(POP);
    });

    it("holds a replayed card still until its first tick", () => {
      renderCard({ loveCount: 7 }, { pending: 2, newLoves: 2, marked: false });
      expect(countOf()?.outerHTML).not.toMatch(POP);
    });

    it("pops a replayed card's count once it ticks", () => {
      renderCard({ loveCount: 7 }, { pending: 1, newLoves: 2, marked: false });
      expect(countOf()?.outerHTML).toMatch(POP);
    });
  });
});
