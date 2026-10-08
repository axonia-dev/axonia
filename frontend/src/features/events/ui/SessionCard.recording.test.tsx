import { render } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useRecordFollowUp } from "@/features/events/api/use-session";
import type { Session } from "@/features/events/domain/session";
import {
  sessionPhase,
  sessionStatusChip,
} from "@/features/events/domain/session-phase";
import { SessionCard } from "@/features/events/ui/SessionCard";
import { aScheduledSession } from "@/test/session-fixture";

vi.mock("@/features/events/ui/SectionBlock", () => ({
  SectionBlock: () => null,
}));
vi.mock("@/features/knowledge/ui/SessionOrphanResources", () => ({
  SessionOrphanResources: () => null,
}));
vi.mock("@/features/events/ui/SessionToVerify", () => ({
  SessionToVerify: () => null,
}));
vi.mock("@/features/events/ui/SessionActionPoints", () => ({
  SessionActionPoints: () => null,
}));
vi.mock("@/features/events/api/use-session", async (importOriginal) => ({
  ...(await importOriginal<object>()),
  useRecordFollowUp: vi.fn(),
}));

beforeEach(() => {
  vi.mocked(useRecordFollowUp).mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
    error: null,
  } as unknown as ReturnType<typeof useRecordFollowUp>);
});

function renderCard(session: Session, canManage = false) {
  return render(
    <SessionCard
      slug="backend-guild"
      session={session}
      phase={sessionPhase(session)}
      statusChip={sessionStatusChip(session)}
      canManage={canManage}
    />,
  );
}

const held = aScheduledSession({
  id: 9,
  scheduledAt: "2020-01-01T18:00:00Z",
  status: "HELD",
});

/** Each fact is drawn with a leading rule, so an empty one shows up as a divider against nothing. */
function facts(container: HTMLElement): string[] {
  return [...container.querySelectorAll("[aria-hidden].w-px")].map(
    (rule) => rule.nextElementSibling?.textContent ?? "",
  );
}

describe("SessionCard - the recording fact", () => {
  it("states the recording in the facts strip", () => {
    renderCard({ ...held, recordingUrl: "https://meet.example/rec/9" });

    expect(facts(document.body)).toContain("Recording");
  });

  it("spends no divider on a session with no recording to state", () => {
    const { container } = renderCard(
      aScheduledSession({ id: 9, scheduledAt: "2099-01-01T18:00:00Z" }),
      true,
    );

    expect(facts(container)).not.toContain("");
  });

  it("spends none on a held session a member cannot add one to either", () => {
    const { container } = renderCard(held);

    expect(facts(container)).not.toContain("");
  });
});
