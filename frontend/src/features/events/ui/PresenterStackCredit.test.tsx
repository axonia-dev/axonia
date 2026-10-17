import { render } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { useMembers } from "@/features/community/api/use-membership";
import {
  useAddPresenter,
  useRemovePresenter,
} from "@/features/events/api/use-slot-presenters";
import {
  usePresentSlot,
  useWithdrawSlot,
} from "@/features/events/api/use-slot-sign-up";
import { PresenterStack } from "@/features/events/ui/PresenterStack";
import { useUsers } from "@/features/identity/api/use-users";
import type { User } from "@/features/identity/domain/user";
import {
  creditTargetsOf,
  resetCreditTargets,
} from "@/features/recognition/ui/credit-targets";

vi.mock("@/features/events/api/use-slot-sign-up", () => ({
  usePresentSlot: vi.fn(),
  useWithdrawSlot: vi.fn(),
}));
vi.mock("@/features/events/api/use-slot-presenters", () => ({
  useAddPresenter: vi.fn(),
  useRemovePresenter: vi.fn(),
}));
vi.mock("@/features/community/api/use-membership", () => ({
  useMembers: vi.fn(),
}));
vi.mock("@/features/identity/api/use-users", () => ({ useUsers: vi.fn() }));
vi.mock("@/auth/current-user", () => ({ currentUserId: () => "mia" }));

const idle = { mutate: vi.fn(), isPending: false, isError: false } as never;

function person(id: string, displayName: string): User {
  return {
    id,
    displayName,
    email: null,
    memberStatus: "PRESENT",
    avatarIsOwn: false,
    timeZoneIsOwn: false,
  };
}

beforeEach(() => {
  vi.mocked(usePresentSlot).mockReturnValue(idle);
  vi.mocked(useWithdrawSlot).mockReturnValue(idle);
  vi.mocked(useAddPresenter).mockReturnValue(idle);
  vi.mocked(useRemovePresenter).mockReturnValue(idle);
  vi.mocked(useMembers).mockReturnValue({ data: { content: [] } } as never);
  vi.mocked(useUsers).mockReturnValue({ data: {} } as never);
});

afterEach(resetCreditTargets);

describe("PresenterStack credit targets", () => {
  it("registers every presenter avatar for the slot", () => {
    const { container } = render(
      <PresenterStack
        slug="eng"
        sessionId={1}
        sectionId={1}
        slot={{
          id: 4,
          sectionId: 1,
          position: 0,
          presenterUserIds: ["mia", "noa"],
          topicId: null,
          title: "Kafka",
          notes: null,
          durationMinutes: 30,
          tags: [],
        }}
        users={{ mia: person("mia", "Mia E"), noa: person("noa", "Noa K") }}
        canManageSchedule={false}
        interactive={true}
      />,
    );

    const targets = creditTargetsOf({
      contributionType: "SLOT_PRESENTED",
      contributionId: 4,
    });
    expect(targets.map((t) => t.userId)).toEqual(["mia", "noa"]);
    expect(targets.every((t) => container.contains(t.element))).toBe(true);
  });
});
