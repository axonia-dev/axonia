import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it } from "vitest";

import type { Practitioner } from "@/features/expertise/domain/expertise";
import { PractitionerCard } from "@/features/expertise/ui/PractitionerCard";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

const base: Practitioner = {
  userId: "alice",
  displayName: "Alice Dupont",
  avatarUrl: null,
  orgUnit: "Platform Team",
  tier: "PRACTITIONER",
  evidence: {
    presentedCount: 2,
    contributedCount: 3,
    offeredCount: 1,
    recent: [
      {
        signalType: "PRESENTED",
        sourceType: "SLOT",
        sourceId: 10,
        label: "Hexagonal Architecture",
        communitySlug: "eng",
        occurredAt: "2026-01-15T10:00:00Z",
        sessionId: null,
        typeId: null,
        typeLabel: null,
        typeIcon: null,
        typeAccentColor: null,
      },
      {
        signalType: "CONTRIBUTED",
        sourceType: "ARTIFACT",
        sourceId: 20,
        label: null,
        communitySlug: "eng",
        occurredAt: "2026-02-20T10:00:00Z",
        sessionId: null,
        typeId: null,
        typeLabel: null,
        typeIcon: null,
        typeAccentColor: null,
      },
    ],
  },
  openToQuestions: false,
  declaredAt: null,
};

function renderCard(overrides: Partial<Practitioner> = {}) {
  return render(
    <MemoryRouter>
      <PractitionerCard practitioner={{ ...base, ...overrides }} />
    </MemoryRouter>,
  );
}

describe("PractitionerCard", () => {
  it("renders the display name and tier badge", () => {
    // when
    renderCard();

    // then
    expect(screen.getByText("Alice Dupont")).toBeInTheDocument();
    expect(screen.getByText("Practitioner")).toBeInTheDocument();
  });

  it("falls back to userId when displayName is null", () => {
    // when
    renderCard({ displayName: null });

    // then
    expect(screen.getByText("alice")).toBeInTheDocument();
  });

  it("shows initials in avatar when avatarUrl is null", () => {
    // when
    renderCard({ avatarUrl: null });

    // then
    // Avatar renders initials via aria-hidden span; the name prop drives it
    expect(screen.queryByRole("img")).not.toBeInTheDocument();
    expect(document.querySelector('[aria-hidden="true"]')).toBeInTheDocument();
  });

  it("renders an img when avatarUrl is set", () => {
    // when
    renderCard({ avatarUrl: "https://example.com/avatar.png" });

    // then
    const img = screen.getByRole("img");
    expect(img).toHaveAttribute("src", "https://example.com/avatar.png");
  });

  it("shows the counts summary with non-zero values", () => {
    // when
    renderCard();

    // then
    expect(screen.getByText(/hosted 2 sessions/)).toBeInTheDocument();
    expect(screen.getByText(/3 artifacts/)).toBeInTheDocument();
    expect(screen.getByText(/offered 1 topic/)).toBeInTheDocument();
  });

  it("omits zero-count parts from the summary", () => {
    // when
    renderCard({
      evidence: {
        presentedCount: 1,
        contributedCount: 0,
        offeredCount: 0,
        recent: [],
      },
    });

    // then
    const summaryText = screen.getByText(/hosted 1 session/);
    expect(summaryText).toBeInTheDocument();
    expect(screen.queryByText(/artifacts/)).not.toBeInTheDocument();
    expect(screen.queryByText(/offered/)).not.toBeInTheDocument();
  });

  it("shows evidence items after expanding", async () => {
    // when
    renderCard();
    await user.click(screen.getByRole("button", { name: /expand evidence/i }));

    // then
    expect(
      screen.getByText(/Presented: Hexagonal Architecture/),
    ).toBeInTheDocument();
    expect(screen.getByText("Contributed")).toBeInTheDocument();
  });

  it("collapses evidence on second click", async () => {
    // when
    renderCard();
    const btn = screen.getByRole("button", { name: /expand evidence/i });
    await user.click(btn);
    await user.click(
      screen.getByRole("button", { name: /collapse evidence/i }),
    );

    // then
    expect(
      screen.queryByText(/Presented: Hexagonal Architecture/),
    ).not.toBeInTheDocument();
  });

  it("renders the org unit when provided", () => {
    // when
    renderCard();

    // then
    expect(screen.getByText("Platform Team")).toBeInTheDocument();
  });

  it("omits the org unit when null", () => {
    // when
    renderCard({ orgUnit: null });

    // then
    expect(screen.queryByText("Platform Team")).not.toBeInTheDocument();
  });

  it("says a declared-only person has no activity yet, with nothing to expand", () => {
    // when
    renderCard({
      tier: "OPEN_TO_QUESTIONS",
      evidence: {
        presentedCount: 0,
        contributedCount: 0,
        offeredCount: 0,
        recent: [],
      },
      openToQuestions: true,
      declaredAt: "2026-09-12T12:00:00Z",
    });

    // then
    expect(screen.getByText("Open to questions")).toBeInTheDocument();
    expect(
      screen.getByText("Declared 12 Sep 2026, no activity yet"),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Expand evidence" }),
    ).not.toBeInTheDocument();
  });

  it("marks an evidenced person who is also open to questions", () => {
    // when
    renderCard({ openToQuestions: true, declaredAt: "2026-09-12T12:00:00Z" });

    // then
    expect(
      screen.getByText("Open to questions since 12 Sep 2026"),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Expand evidence" }),
    ).toBeInTheDocument();
  });

  it("carries no declaration line for a person who declared nothing", () => {
    // when
    renderCard();

    // then
    expect(screen.queryByText(/Open to questions/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Declared/)).not.toBeInTheDocument();
  });

  it("lets the openToQuestions flag decide the declaration line", () => {
    // when
    renderCard({ openToQuestions: false, declaredAt: "2026-09-12T12:00:00Z" });

    // then
    expect(
      screen.queryByText(/Open to questions since/),
    ).not.toBeInTheDocument();
  });
});
