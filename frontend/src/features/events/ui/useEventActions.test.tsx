import { render, renderHook, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useContributionPolicy } from "@/features/community/api/use-contribution-policy";
import { useManagementAccess } from "@/features/community/api/use-management-access";
import { useIsOrganizer } from "@/features/community/api/use-organizer";
import { EMPTY_PLACE } from "@/features/events/domain/meeting-place";
import type { Section } from "@/features/events/domain/section";
import type { Session } from "@/features/events/domain/session";
import type { Slot } from "@/features/events/domain/slot";
import { useEventActions } from "@/features/events/ui/useEventActions";

vi.mock("@/features/events/api/use-member-zones", () => ({
  useMemberZones: vi.fn(() => ({ data: undefined })),
}));

vi.mock("@/features/community/api/use-organizer", () => ({
  useIsOrganizer: vi.fn(() => true),
}));
vi.mock("@/features/community/api/use-contribution-policy", () => ({
  useContributionPolicy: vi.fn(() => ({
    canStartSeries: false,
    canEditStructure: false,
    canQueueTopics: false,
    canManageSchedule: false,
  })),
}));
vi.mock("@/features/community/api/use-management-access", () => ({
  useManagementAccess: vi.fn(() => ({ communication: false })),
}));
vi.mock("@/features/knowledge/ui/AddResourceButton", () => ({
  AddResourceButton: (props: { defaultUrl?: string }) => (
    <div data-testid="add-resource">{props.defaultUrl}</div>
  ),
}));
vi.mock("@/features/recognition/ui/LoveAction", () => ({
  LoveAction: (props: {
    contribution: { contributionType: string };
    contributorUserIds?: string[];
  }) => (
    <div data-testid="love-action">
      {props.contribution.contributionType}
      {props.contributorUserIds?.join(",")}
    </div>
  ),
}));
vi.mock("@/features/recognition/ui/ReactionBar", () => ({
  ReactionBar: (props: { contribution: { contributionType: string } }) => (
    <div data-testid="reaction-bar">{props.contribution.contributionType}</div>
  ),
}));

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

const heldSession: Session = {
  id: 9,
  seriesId: 1,
  communityId: 1,
  status: "HELD",
  title: null,
  occurrenceDate: null,
  scheduledAt: "2026-01-01T18:00:00Z",
  hasTime: true,
  durationMinutes: 60,
  facilitatorUserId: "alice",
  attendeeCount: 4,
  notes: "Great session",
  recordingUrl: "https://example.com/rec",
  place: EMPTY_PLACE,
  placeOverride: EMPTY_PLACE,
  seriesPlace: EMPTY_PLACE,
  seriesTimeZone: "Europe/Paris",
  sections: [
    {
      id: 3,
      sessionId: 9,
      position: 0,
      sectionTypeId: 2,
      sectionType: "Talk",
      sectionTypeIcon: null,
      sectionTypeAccentColor: null,
      title: "Main talk",
      durationMinutes: 30,
      slots: [
        {
          id: 7,
          sectionId: 3,
          position: 0,
          presenterUserIds: ["bob"],
          topicId: null,
          title: "Hexagons",
          notes: null,
          durationMinutes: 20,
          tags: [],
        },
      ],
    },
  ],
  createdAt: "x",
  updatedAt: "x",
  closedAt: null,
  wrapUpReleaseAt: null,
  wrapUpStatus: null,
  upcomingReleaseAt: null,
  upcomingStatus: null,
};
const scheduledSession: Session = {
  ...heldSession,
  id: 10,
  status: "SCHEDULED",
};
const heldWithoutRecording: Session = {
  ...heldSession,
  id: 11,
  recordingUrl: null,
  place: EMPTY_PLACE,
  placeOverride: EMPTY_PLACE,
  seriesPlace: EMPTY_PLACE,
};
const filledSlot: Slot = heldSession.sections[0]!.slots[0]!;
const coPresenterSlot: Slot = {
  ...filledSlot,
  id: 98,
  presenterUserIds: ["alice", "mia"],
};
const openSlot: Slot = { ...filledSlot, id: 99, presenterUserIds: [] };
const section: Section = heldSession.sections[0]!;

function actions() {
  return renderHook(() => useEventActions("backend-guild")).result.current;
}

beforeEach(() => {
  vi.mocked(useIsOrganizer).mockReturnValue(true);
  vi.mocked(useContributionPolicy).mockReturnValue({
    canStartSeries: false,
    canEditStructure: false,
    canQueueTopics: false,
    canManageSchedule: false,
  });
  vi.mocked(useManagementAccess).mockReturnValue({
    communication: false,
  } as never);
});

describe("useEventActions - contribution flags", () => {
  it("grants every axis to an organizer regardless of policy", () => {
    vi.mocked(useIsOrganizer).mockReturnValue(true);
    const a = actions();
    expect(a.canManage).toBe(true);
    expect(a.canStartSeries).toBe(true);
    expect(a.canEditStructure).toBe(true);
    expect(a.canQueueTopics).toBe(true);
  });

  it("grants a member only the axes the policy opens", () => {
    vi.mocked(useIsOrganizer).mockReturnValue(false);
    vi.mocked(useContributionPolicy).mockReturnValue({
      canStartSeries: true,
      canEditStructure: false,
      canQueueTopics: true,
      canManageSchedule: false,
    });
    const a = actions();
    expect(a.canManage).toBe(false);
    expect(a.canStartSeries).toBe(true);
    expect(a.canEditStructure).toBe(false);
    expect(a.canQueueTopics).toBe(true);
  });

  it("grants a member nothing under a curated policy", () => {
    vi.mocked(useIsOrganizer).mockReturnValue(false);
    const a = actions();
    expect(a.canStartSeries).toBe(false);
    expect(a.canEditStructure).toBe(false);
    expect(a.canQueueTopics).toBe(false);
  });

  // Read off the management policy, not the contribution one: a session's mail is the community's
  // own communication right at the scale of one session.
  it("takes the mail right from the community's communication axis", () => {
    vi.mocked(useManagementAccess).mockReturnValue({
      communication: true,
    } as never);

    expect(actions().canManageMessages).toBe(true);
  });

  it("withholds the mail right where that axis is closed", () => {
    vi.mocked(useManagementAccess).mockReturnValue({
      communication: false,
    } as never);

    expect(actions().canManageMessages).toBe(false);
  });
});

describe("useEventActions - renderSessionActions", () => {
  it("renders AddResourceButton with the recording URL for a held session with a recording", () => {
    render(<>{actions().renderSessionActions?.(heldSession)}</>);
    expect(screen.getByTestId("add-resource")).toHaveTextContent(
      "https://example.com/rec",
    );
  });

  it("renders AddResourceButton (no URL) for a held session without a recording", () => {
    render(<>{actions().renderSessionActions?.(heldWithoutRecording)}</>);
    expect(screen.getByTestId("add-resource")).toBeInTheDocument();
    expect(screen.getByTestId("add-resource")).toHaveTextContent("");
  });

  it("returns null for a scheduled session", () => {
    render(<>{actions().renderSessionActions?.(scheduledSession)}</>);
    expect(screen.queryByTestId("add-resource")).toBeNull();
  });
});

describe("useEventActions - renderSlotActions", () => {
  it("renders a single love control for a co-presented slot", () => {
    render(
      <>
        {actions().renderSlotActions?.(coPresenterSlot, section, heldSession)}
      </>,
    );
    expect(screen.getAllByTestId("love-action")).toHaveLength(1);
    expect(screen.getByTestId("love-action")).toHaveTextContent(
      "SLOT_PRESENTED",
    );
    expect(screen.getByTestId("love-action")).toHaveTextContent("alice");
    expect(screen.getByTestId("love-action")).toHaveTextContent("mia");
    expect(screen.getByTestId("reaction-bar")).toHaveTextContent(
      "SLOT_PRESENTED",
    );
  });

  it("renders null for an open slot (no presenters)", () => {
    render(
      <>{actions().renderSlotActions?.(openSlot, section, heldSession)}</>,
    );
    expect(screen.queryByTestId("love-action")).toBeNull();
    expect(screen.queryByTestId("reaction-bar")).toBeNull();
  });
});
