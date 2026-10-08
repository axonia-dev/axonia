import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { describe, expect, it, vi } from "vitest";

import { ContributionStrip } from "@/features/expertise/ui/ContributionStrip";

const contribution = {
  signalId: 1,
  canonicalTag: "kafka",
  userId: "u1",
  reach: 42,
  evidence: {
    signalType: "PRESENTED" as const,
    sourceType: "SLOT" as const,
    sourceId: 10,
    label: "Scaling Kafka",
    communitySlug: "backend-guild",
    occurredAt: "2026-09-01T10:00:00Z",
    sessionId: 5,
    typeId: null,
    typeLabel: null,
    typeIcon: null,
    typeAccentColor: null,
  },
};

const author = { userId: "u1", displayName: "Ada Lovelace", avatarUrl: null };

function renderStrip(props: Parameters<typeof ContributionStrip>[0]) {
  return render(
    <MemoryRouter>
      <ContributionStrip {...props} />
    </MemoryRouter>,
  );
}

describe("ContributionStrip", () => {
  it("renders nothing when nothing is selected", () => {
    const { container } = renderStrip({ contribution: null, author: null });

    expect(container).toBeEmptyDOMElement();
  });

  it("names the contribution, its author and its area", () => {
    renderStrip({ contribution, author });

    expect(screen.getByText(/Scaling Kafka/)).toBeInTheDocument();
    expect(screen.getByText(/Ada Lovelace/)).toBeInTheDocument();
    expect(screen.getByText(/kafka/)).toBeInTheDocument();
  });

  it("names the contribution once, not twice", () => {
    renderStrip({ contribution, author });

    expect(screen.getAllByText(/Scaling Kafka/)).toHaveLength(1);
  });

  it("links where the evidence list links", () => {
    renderStrip({ contribution, author });

    expect(screen.getByRole("link")).toHaveAttribute(
      "href",
      "/communities/backend-guild/sessions/5",
    );
  });

  it("names the author by their id when the directory could not", () => {
    renderStrip({
      contribution,
      author: { userId: "u1", displayName: "u1", avatarUrl: null },
    });

    expect(screen.getByText(/u1/)).toBeInTheDocument();
  });

  it("sits in normal flow, where browser chrome cannot cover it", () => {
    renderStrip({ contribution, author });

    expect(screen.getByTestId("contribution-strip").className).not.toMatch(
      /absolute|fixed|sticky/,
    );
  });

  it("names the author as unknown when the directory holds nobody", () => {
    renderStrip({ contribution, author: null });

    expect(screen.getByText(/Unknown member/)).toBeInTheDocument();
  });

  it("still names the area when the source lost its title", () => {
    renderStrip({
      contribution: {
        ...contribution,
        evidence: { ...contribution.evidence, label: null },
      },
      author,
    });

    expect(screen.queryByText("Scaling Kafka")).not.toBeInTheDocument();
    expect(screen.getByText(/kafka/)).toBeInTheDocument();
  });

  it("says nothing about attendance, which the page states nowhere else", () => {
    renderStrip({ contribution, author });

    expect(screen.queryByText(/Reached/)).not.toBeInTheDocument();
    expect(screen.queryByText(/42/)).not.toBeInTheDocument();
  });

  it("brings itself into view, because a tall drawing leaves it below the fold", () => {
    const scrolled = vi.fn();
    vi.spyOn(Element.prototype, "scrollIntoView").mockImplementation(scrolled);

    renderStrip({ contribution, author });

    expect(scrolled).toHaveBeenCalled();
  });

  it("stays where it is while nothing is selected", () => {
    const scrolled = vi.fn();
    vi.spyOn(Element.prototype, "scrollIntoView").mockImplementation(scrolled);

    renderStrip({ contribution: null, author: null });

    expect(scrolled).not.toHaveBeenCalled();
  });
});
