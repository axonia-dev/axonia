import { render } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { sessionMorphId } from "@/components/patterns/morph/MorphPart";
import {
  sessionPhase,
  sessionStatusChip,
} from "@/features/events/domain/session-phase";
import { SessionCard } from "@/features/events/ui/SessionCard";
import { morphNames } from "@/test/morph-names";
import { aScheduledSession } from "@/test/session-fixture";

vi.mock("react", async (original) => ({
  ...(await original<typeof import("react")>()),
  ViewTransition: (await import("@/test/morph-names")).ViewTransitionProbe,
}));
vi.mock("@/features/events/ui/SectionBlock", () => ({
  SectionBlock: () => null,
}));
vi.mock("@/features/knowledge/ui/SessionOrphanResources", () => ({
  SessionOrphanResources: () => null,
}));
vi.mock("@/features/events/ui/SessionToVerify", () => ({
  SessionToVerify: () => null,
}));
vi.mock("@/features/events/ui/SessionFeedback", () => ({
  SessionFeedback: () => null,
  SessionFeedbackResult: () => null,
}));
vi.mock("@/features/events/ui/SessionActionPoints", () => ({
  SessionActionPoints: () => null,
}));

const session = aScheduledSession({
  id: 9,
  scheduledAt: "2099-01-01T18:00:00Z",
  title: "A very long session title that has to ellipsize on a wide screen",
});

function renderCard(morphId?: string) {
  return render(
    <SessionCard
      slug="backend-guild"
      session={session}
      phase={sessionPhase(session)}
      statusChip={sessionStatusChip(session)}
      {...(morphId !== undefined && { morphId })}
    />,
  );
}

describe("SessionCard as a morph target", () => {
  it("names its surface, title and pill when given a morph id", () => {
    // when
    const { container } = renderCard(sessionMorphId(9));

    // then
    expect(morphNames(container)).toEqual([
      "morph-surface-session-9",
      "morph-title-session-9",
      "morph-pill-session-9",
    ]);
  });

  it("names nothing without one, as in the session feed", () => {
    // when
    const { container } = renderCard();

    // then
    expect(morphNames(container)).toEqual([]);
  });

  it("keeps the title ellipsized on wide screens and wrapping on phones", () => {
    // when
    const { getByRole } = renderCard(sessionMorphId(9));

    // then
    const heading = getByRole("heading", { level: 2 });
    expect(heading).toHaveClass("break-words");
    expect(heading.querySelector("span")).toHaveClass(
      "inline-block",
      "max-w-full",
      "sm:truncate",
    );
  });
});
