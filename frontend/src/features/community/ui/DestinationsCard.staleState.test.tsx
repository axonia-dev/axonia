import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  useChatDestinationsQuery,
  useCreateChatDestination,
  useRemoveChatDestination,
  useTestChatDestination,
  useUpdateChatDestination,
} from "@/features/community/api/use-chat-destinations";
import type { ChatDestination } from "@/features/community/domain/chat-destination";
import { ApiError } from "@/lib/api-client";

import { DestinationsCard } from "./DestinationsCard";

/**
 * Split out of `DestinationsCard.test.tsx` (which was already at the eslint
 * line cap) rather than trimmed into it. The remove-dialog half of this class
 * of bug - a mutation's outcome outliving the destination it belonged to - is
 * pinned in `RemoveDestinationDialog.remount.test.tsx` instead, against the
 * real `useRemoveChatDestination` hook: fully mocking that hook here, the way
 * the rest of this file does, cannot actually observe a remount, since a
 * mocked hook's return value is whatever a test tells it on every call
 * regardless of whether the component under it remounted. `failure` below is
 * different - it is genuine `useState` inside `DestinationsCard`'s own
 * `useDestinationTest`, not a mocked hook's return value, so exercising the
 * real `onSaved` wiring here does observe the actual fix.
 */
vi.mock("@/features/community/api/use-chat-destinations", () => ({
  useChatDestinationsQuery: vi.fn(),
  useCreateChatDestination: vi.fn(),
  useUpdateChatDestination: vi.fn(),
  useRemoveChatDestination: vi.fn(),
  useTestChatDestination: vi.fn(),
}));

function healthy(overrides: Partial<ChatDestination> = {}): ChatDestination {
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

const testMutate = vi.fn();

function mockDestinations(list: ChatDestination[]) {
  vi.mocked(useChatDestinationsQuery).mockReturnValue({
    data: list,
    isPending: false,
    isError: false,
  } as never);
}

function renderCard(list: ChatDestination[]) {
  mockDestinations(list);
  return render(<DestinationsCard slug="backend-guild" />);
}

async function openMenu(name: string) {
  await user.click(screen.getByRole("button", { name: `Actions for ${name}` }));
}

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.clearAllMocks();
  vi.mocked(useTestChatDestination).mockReturnValue({
    mutate: testMutate,
    isPending: false,
  } as never);
  vi.mocked(useCreateChatDestination).mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
    isError: false,
    error: null,
  } as never);
  vi.mocked(useUpdateChatDestination).mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
    isError: false,
    error: null,
  } as never);
});

describe("DestinationsCard stale mutation state", () => {
  it("clears a stale test failure once the destination is edited and saved", async () => {
    vi.mocked(useRemoveChatDestination).mockReturnValue({
      mutate: vi.fn(),
      isPending: false,
      isError: false,
      error: null,
    } as never);
    renderCard([healthy({ id: 7, name: "#platform-guild" })]);
    testMutate.mockImplementation(
      (_id: unknown, options?: { onError?: (error: unknown) => void }) => {
        options?.onError?.(new ApiError(502, "x", "CHAT_SEND_FAILED"));
      },
    );
    await openMenu("#platform-guild");
    await user.click(screen.getByRole("menuitem", { name: "Test sending" }));
    expect(await screen.findByRole("alert")).toBeVisible();

    vi.mocked(useUpdateChatDestination).mockReturnValue({
      mutate: vi.fn((_vars: unknown, options?: { onSuccess?: () => void }) => {
        options?.onSuccess?.();
      }),
      isPending: false,
      isError: false,
      error: null,
    } as never);
    await openMenu("#platform-guild");
    await user.click(screen.getByRole("menuitem", { name: "Edit" }));
    await user.click(screen.getByRole("button", { name: "Save" }));

    expect(screen.queryByRole("alert")).toBeNull();
  });
});
