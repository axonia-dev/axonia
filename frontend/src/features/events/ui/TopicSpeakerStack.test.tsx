import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useMembers } from "@/features/community/api/use-membership";
import {
  useAddSelfAsSpeaker,
  useAddSpeaker,
  useRemoveSpeaker,
  useWithdrawAsSpeaker,
} from "@/features/events/api/use-topic-speakers";
import type { Topic } from "@/features/events/domain/topic";
import { TopicSpeakerStack } from "@/features/events/ui/TopicSpeakerStack";
import { useUsers } from "@/features/identity/api/use-users";
import type { User } from "@/features/identity/domain/user";
import { ApiError } from "@/lib/api-client";

vi.mock("@/features/events/api/use-topic-speakers", () => ({
  useAddSelfAsSpeaker: vi.fn(),
  useAddSpeaker: vi.fn(),
  useWithdrawAsSpeaker: vi.fn(),
  useRemoveSpeaker: vi.fn(),
}));
vi.mock("@/features/community/api/use-membership", () => ({
  useMembers: vi.fn(),
  usePeopleSearch: vi.fn(() => ({ data: undefined })),
}));
vi.mock("@/features/identity/api/use-users", () => ({ useUsers: vi.fn() }));
vi.mock("@/auth/current-user", () => ({ currentUserId: () => "dev-user" }));

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

const users: Record<string, User> = {
  "dev-user": person("dev-user", "Dev User"),
  mia: person("mia", "Mia Eriksson"),
};

const request: Topic = {
  id: 5,
  communityId: 1,
  kind: "REQUEST",
  title: "Intro to event sourcing",
  description: "",
  tags: [],
  proposerUserId: "mia",
  proposerDisplayName: "Mia Eriksson",
  speakerUserIds: [],
  canManageSpeakers: false,
  canClose: false,
  canReopen: false,
  voteCount: 0,
  hasVoted: false,
  canEdit: false,
  canDelete: false,
  status: "PROPOSED",
  scheduledFor: null,
  createdAt: "x",
  updatedAt: "x",
};

function idle(overrides: object = {}) {
  return {
    mutate: vi.fn(),
    mutateAsync: vi.fn(),
    isPending: false,
    isError: false,
    isSuccess: false,
    error: null,
    ...overrides,
  } as never;
}

const ADD = "Add speaker to Intro to event sourcing";

let addSelf: ReturnType<typeof vi.fn>;
let addOther: ReturnType<typeof vi.fn>;
let withdraw: ReturnType<typeof vi.fn>;
let remove: ReturnType<typeof vi.fn>;
let user: ReturnType<typeof userEvent.setup>;

function renderStack(topic: Partial<Topic> = {}, interactive = true) {
  return render(
    <TopicSpeakerStack
      slug="eng"
      topic={{ ...request, ...topic }}
      users={users}
      interactive={interactive}
    />,
  );
}

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.clearAllMocks();
  addSelf = vi.fn().mockResolvedValue(request);
  addOther = vi
    .fn()
    .mockResolvedValue({ topic: request, assigned: true, reason: null });
  withdraw = vi.fn();
  remove = vi.fn();
  vi.mocked(useAddSelfAsSpeaker).mockReturnValue(
    idle({ mutateAsync: addSelf }),
  );
  vi.mocked(useAddSpeaker).mockReturnValue(idle({ mutateAsync: addOther }));
  vi.mocked(useWithdrawAsSpeaker).mockReturnValue(idle({ mutate: withdraw }));
  vi.mocked(useRemoveSpeaker).mockReturnValue(idle({ mutate: remove }));
  vi.mocked(useMembers).mockReturnValue({
    data: { content: [{ userId: "mia" }] },
  } as never);
  vi.mocked(useUsers).mockReturnValue({ data: users } as never);
});

describe("TopicSpeakerStack", () => {
  it("invites a speaker on a request with the accented add button", () => {
    // when
    renderStack();

    // then
    expect(screen.getByRole("button", { name: ADD })).toHaveClass(
      "border-primary",
    );
  });

  it("adds the viewer as a speaker from the self row", async () => {
    // given
    renderStack();
    await user.click(screen.getByRole("button", { name: ADD }));

    // when
    await user.click(
      screen.getByRole("button", { name: /i'll present this/i }),
    );

    // then
    expect(addSelf).toHaveBeenCalledOnce();
  });

  it("closes the picker once the viewer is a speaker", async () => {
    // given
    renderStack();
    await user.click(screen.getByRole("button", { name: ADD }));

    // when
    await user.click(
      screen.getByRole("button", { name: /i'll present this/i }),
    );

    // then
    await waitFor(() =>
      expect(
        screen.queryByRole("dialog", { name: "Add speaker" }),
      ).not.toBeInTheDocument(),
    );
  });

  it("offers the roster to whoever manages the speakers", async () => {
    // given
    renderStack({ canManageSpeakers: true });

    // when
    await user.click(screen.getByRole("button", { name: ADD }));

    // then
    expect(screen.getByLabelText("Search people")).toBeInTheDocument();
  });

  it("names a speaker from the roster", async () => {
    // given
    renderStack({ canManageSpeakers: true });
    await user.click(screen.getByRole("button", { name: ADD }));

    // when
    await user.click(screen.getByRole("button", { name: "Mia Eriksson" }));

    // then
    expect(addOther).toHaveBeenCalledWith("mia");
  });

  it("offers no roster to a plain member who is not a speaker", async () => {
    // given
    renderStack();

    // when
    await user.click(screen.getByRole("button", { name: ADD }));

    // then
    expect(screen.queryByLabelText("Search people")).not.toBeInTheDocument();
  });

  it("lets a speaker withdraw from their own face", async () => {
    // given
    renderStack({ kind: "OFFER", speakerUserIds: ["dev-user"] });
    await user.click(screen.getByRole("button", { name: "Dev User" }));

    // when
    await user.click(screen.getByRole("button", { name: "Withdraw" }));

    // then
    expect(withdraw).toHaveBeenCalledOnce();
  });

  it("offers no add button to a speaker with nobody else to name", () => {
    // when
    renderStack({ kind: "OFFER", speakerUserIds: ["dev-user"] });

    // then
    expect(screen.queryByRole("button", { name: ADD })).not.toBeInTheDocument();
  });

  it("lets whoever manages the speakers remove another one", async () => {
    // given
    renderStack({
      kind: "OFFER",
      speakerUserIds: ["mia"],
      canManageSpeakers: true,
    });
    await user.click(screen.getByRole("button", { name: "Mia Eriksson" }));

    // when
    await user.click(screen.getByRole("button", { name: "Remove speaker" }));

    // then
    expect(remove).toHaveBeenCalledWith("mia");
  });

  it("shows a refusal of a stale topic inside the manage popover", async () => {
    // given
    vi.mocked(useWithdrawAsSpeaker).mockReturnValue(
      idle({
        isError: true,
        error: new ApiError(409, "x", "NOT_PROPOSED"),
      }),
    );
    renderStack({ kind: "OFFER", speakerUserIds: ["dev-user"] });

    // when
    await user.click(screen.getByRole("button", { name: "Dev User" }));

    // then
    expect(screen.getByRole("alert")).toHaveTextContent(
      "This topic is no longer on the backlog.",
    );
  });

  it("shows read-only faces with no add button", () => {
    // when
    renderStack({ kind: "OFFER", speakerUserIds: ["mia"] }, false);

    // then
    expect(screen.queryByRole("button", { name: ADD })).not.toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Mia Eriksson" }),
    ).toBeInTheDocument();
  });

  it("renders nothing read-only when nobody speaks", () => {
    // when
    const { container } = renderStack({}, false);

    // then
    expect(container).toBeEmptyDOMElement();
  });
});
