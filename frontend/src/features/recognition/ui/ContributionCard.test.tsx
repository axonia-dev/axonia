import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it } from "vitest";

import type { RecognizedContribution } from "@/features/recognition/domain/recognition";
import { ContributionCard } from "@/features/recognition/ui/ContributionCard";

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

function renderCard(over: Partial<RecognizedContribution>) {
  render(
    <MemoryRouter>
      <ContributionCard contribution={contribution(over)} />
    </MemoryRouter>,
  );
}

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
    const reaction = screen.getByLabelText("Appreciated by 7 people");
    expect(reaction).toHaveTextContent("7");
  });

  it("pluralises the accessible label to the singular for one love", () => {
    renderCard({ loveCount: 1 });
    expect(
      screen.getByLabelText("Appreciated by 1 person"),
    ).toBeInTheDocument();
  });

  it("omits the reaction line when there is none yet", () => {
    renderCard({ loveCount: 0 });
    expect(screen.queryByLabelText(/appreciated by/i)).not.toBeInTheDocument();
  });
});
