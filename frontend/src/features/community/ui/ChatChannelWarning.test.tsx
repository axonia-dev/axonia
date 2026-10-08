import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useChatDestinationsQuery } from "@/features/community/api/use-chat-destinations";
import {
  type ManagementAccess,
  useManagementAccess,
} from "@/features/community/api/use-management-access";
import type { ChatDestination } from "@/features/community/domain/chat-destination";

import { ChatChannelWarning } from "./ChatChannelWarning";

vi.mock("@/features/community/api/use-chat-destinations", () => ({
  useChatDestinationsQuery: vi.fn(),
}));
vi.mock("@/features/community/api/use-management-access", () => ({
  useManagementAccess: vi.fn(),
}));

function destination(overrides: Partial<ChatDestination>): ChatDestination {
  return {
    id: 1,
    name: "#guild",
    platform: "SLACK",
    maskedUrl: "hooks.slack.com/...abcd",
    broken: false,
    lastPostedAt: null,
    lastFailureAt: null,
    lastError: null,
    ...overrides,
  };
}

function fullAccess(communication: boolean): ManagementAccess {
  return {
    editCommunity: true,
    lookAndFeel: true,
    members: true,
    contributionPolicy: true,
    archive: true,
    managementPolicy: true,
    curation: true,
    health: true,
    invite: true,
    communication,
    credit: true,
    any: true,
    isLoading: false,
  };
}

function mockAccess(communication: boolean) {
  vi.mocked(useManagementAccess).mockReturnValue(fullAccess(communication));
}

function mockQuery(state: {
  data: ChatDestination[] | undefined;
  isError?: boolean;
}) {
  vi.mocked(useChatDestinationsQuery).mockReturnValue({
    data: state.data,
    isError: state.isError ?? false,
  } as never);
}

function renderWarning() {
  return render(
    <MemoryRouter>
      <ChatChannelWarning slug="backend-guild" />
    </MemoryRouter>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  mockAccess(true);
});

describe("ChatChannelWarning", () => {
  it("names the one broken destination and links to communication settings", () => {
    // given
    mockQuery({
      data: [destination({ name: "#eng-leads", broken: true })],
    });

    // when
    renderWarning();

    // then
    expect(screen.getByRole("alert")).toHaveTextContent(
      "axonia has stopped posting to #eng-leads.",
    );
    const link = screen.getByRole("link", {
      name: /communication settings/i,
    });
    expect(link).toHaveAttribute(
      "href",
      "/communities/backend-guild/settings/communication",
    );
  });

  it("names both broken destinations, not just the first", () => {
    // given
    mockQuery({
      data: [
        destination({ id: 1, name: "#eng-leads", broken: true }),
        destination({ id: 2, name: "#other-team", broken: true }),
      ],
    });

    // when
    renderWarning();

    // then
    expect(screen.getByRole("alert")).toHaveTextContent(
      "axonia has stopped posting to #eng-leads and #other-team.",
    );
  });

  it("renders nothing when every destination is healthy", () => {
    // given
    mockQuery({
      data: [
        destination({ id: 1, name: "#eng-leads", broken: false }),
        destination({ id: 2, name: "#other-team", broken: false }),
      ],
    });

    // when
    renderWarning();

    // then
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("renders nothing when no destinations are configured", () => {
    // given
    mockQuery({ data: [] });

    // when
    renderWarning();

    // then
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  // The component destructures only `data` from the query result and never
  // reads `isError` - loading and a failed fetch both leave `data`
  // `undefined`, so they are the same code path from this component's point
  // of view. One test covers both rather than pretending they're distinguished.
  it("renders nothing while data is unavailable, whether still loading or after the query failed", () => {
    // given
    mockQuery({ data: undefined, isError: true });

    // when
    renderWarning();

    // then
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  // Regression guard for the disclosure this warning must never repeat: a
  // member (or non-member) without the `communication` axis must never learn
  // a community's chat destinations exist, let alone that one is broken - the
  // same axis CommunityMenu hides the Communication link behind.
  // The destinations query is mocked to still return broken data here on
  // purpose: in production a disabled query never fetches, but this proves
  // the component itself refuses to render the warning off stale or
  // unexpectedly present data, not only that it declined to ask for it.
  it("never renders for a user without the communication access axis, even if broken data is present", () => {
    // given
    mockAccess(false);
    mockQuery({
      data: [destination({ name: "#eng-leads", broken: true })],
    });

    // when
    renderWarning();

    // then
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    expect(useChatDestinationsQuery).toHaveBeenCalledWith("backend-guild", {
      enabled: false,
    });
  });
});
