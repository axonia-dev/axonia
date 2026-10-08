import { act, fireEvent, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

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

function broken(overrides: Partial<ChatDestination> = {}): ChatDestination {
  return healthy({
    broken: true,
    // Noon in January in Los Angeles (PST, UTC-8, no DST) is 20:00 UTC.
    lastFailureAt: "2026-01-15T20:00:00Z",
    lastError: "axonia has stopped posting to this destination",
    ...overrides,
  });
}

const testMutate = vi.fn();

function mockDestinations(
  list: ChatDestination[],
  overrides: { isPending?: boolean; isError?: boolean } = {},
) {
  vi.mocked(useChatDestinationsQuery).mockReturnValue({
    data: list,
    isPending: overrides.isPending ?? false,
    isError: overrides.isError ?? false,
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
  testMutate.mockImplementation(
    (_id: unknown, options?: { onSuccess?: () => void }) => {
      options?.onSuccess?.();
    },
  );
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
  vi.mocked(useRemoveChatDestination).mockReturnValue({
    mutate: vi.fn(),
    isPending: false,
    isError: false,
    error: null,
  } as never);
});

afterEach(() => {
  vi.useRealTimers();
});

describe("DestinationsCard", () => {
  it("puts Email first in the list, carrying an Always on chip", () => {
    renderCard([healthy({ name: "#guild" })]);

    const rows = screen.getAllByRole("listitem");
    expect(rows[0]).toHaveTextContent("Email");
    expect(within(rows[0] as HTMLElement).getByText("Always on")).toBeVisible();
  });

  it("shows Email as a fixed first row that cannot be removed", () => {
    renderCard([]);

    expect(screen.getByText("Email")).toBeVisible();
    expect(
      screen.getByText("Every member, unless they opted out"),
    ).toBeVisible();
    expect(
      screen.queryByRole("button", { name: /Actions for Email/ }),
    ).toBeNull();
  });

  it("names an empty list without explaining the feature", () => {
    renderCard([]);

    expect(screen.getByText("No chat destinations")).toBeVisible();
  });

  it("shows a fetch error instead of the empty state when the query fails", () => {
    mockDestinations([], { isError: true });

    render(<DestinationsCard slug="backend-guild" />);

    const alert = screen.getByRole("alert");
    expect(alert).toHaveTextContent("Could not load chat destinations.");
    expect(alert).toHaveClass("text-danger");
    expect(screen.queryByText("No chat destinations")).toBeNull();
  });

  it("promotes Test sending out of the menu for a broken destination, and dates the failure", () => {
    renderCard([broken({ name: "#eng-leads", lastError: "410 Gone" })]);

    expect(
      screen.getByRole("button", { name: "Test sending #eng-leads" }),
    ).toBeVisible();
    expect(
      screen.getByText("Stopped posting on Jan 15, 2026, 12:00 PM: 410 Gone"),
    ).toBeVisible();
  });

  it("does not repeat Test sending inside the menu of a broken row", async () => {
    renderCard([broken({ name: "#eng-leads" })]);

    await openMenu("#eng-leads");

    expect(screen.queryByRole("menuitem", { name: "Test sending" })).toBeNull();
    expect(screen.getByRole("menuitem", { name: "Edit" })).toBeVisible();
    expect(screen.getByRole("menuitem", { name: "Remove" })).toBeVisible();
  });

  it("confirms a test post on the row it belonged to", async () => {
    renderCard([
      healthy({ id: 7, name: "#platform-guild" }),
      healthy({ id: 9, name: "#architecture" }),
    ]);

    await openMenu("#platform-guild");
    await user.click(screen.getByRole("menuitem", { name: "Test sending" }));

    expect(await screen.findByText("Posted, check the channel")).toBeVisible();
    expect(
      screen.getByRole("listitem", { name: "#platform-guild" }),
    ).toContainElement(screen.getByText("Posted, check the channel"));
  });

  it("clears the confirmation again once it fades, after exactly three seconds", async () => {
    vi.useFakeTimers();
    renderCard([healthy({ id: 7, name: "#platform-guild" })]);

    act(() => {
      fireEvent.click(
        screen.getByRole("button", { name: "Actions for #platform-guild" }),
      );
    });
    await act(async () => {
      fireEvent.click(screen.getByRole("menuitem", { name: "Test sending" }));
      await vi.advanceTimersByTimeAsync(0);
    });

    const status = screen.getByRole("status");
    expect(status).toHaveTextContent("Posted, check the channel");

    await act(() => vi.advanceTimersByTimeAsync(2999));
    expect(status).toHaveTextContent("Posted, check the channel");

    await act(() => vi.advanceTimersByTimeAsync(1));
    expect(status).toHaveTextContent("");
  });

  it("shows a test-post failure inline on the row it belonged to", async () => {
    renderCard([healthy({ id: 7, name: "#platform-guild" })]);
    testMutate.mockImplementation(
      (_id: unknown, options?: { onError?: (error: unknown) => void }) => {
        options?.onError?.(new ApiError(502, "x", "CHAT_SEND_FAILED"));
      },
    );

    await openMenu("#platform-guild");
    await user.click(screen.getByRole("menuitem", { name: "Test sending" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      /refused the test post/i,
    );
  });

  it("disables Test sending only on the row being tested, not every broken row", async () => {
    testMutate.mockImplementation(() => {
      // Never settles, so both rows' pending state can be observed mid-flight.
    });
    renderCard([
      broken({ id: 1, name: "#eng-leads" }),
      broken({ id: 2, name: "#other-team" }),
    ]);

    await user.click(
      screen.getByRole("button", { name: "Test sending #eng-leads" }),
    );

    expect(
      screen.getByRole("button", { name: "Test sending #eng-leads" }),
    ).toBeDisabled();
    expect(
      screen.getByRole("button", { name: "Test sending #other-team" }),
    ).toBeEnabled();
  });

  it("opens the edit dialog prefilled with the row's name when Edit is chosen", async () => {
    renderCard([healthy({ id: 7, name: "#platform-guild" })]);

    await openMenu("#platform-guild");
    await user.click(screen.getByRole("menuitem", { name: "Edit" }));

    expect(
      screen.getByRole("heading", { name: "Edit destination" }),
    ).toBeVisible();
    expect(screen.getByLabelText("Name")).toHaveValue("#platform-guild");
  });

  it("opens the remove confirmation naming the row when Remove is chosen", async () => {
    renderCard([healthy({ id: 7, name: "#platform-guild" })]);

    await openMenu("#platform-guild");
    await user.click(screen.getByRole("menuitem", { name: "Remove" }));

    expect(
      screen.getByRole("heading", { name: "Remove #platform-guild?" }),
    ).toBeVisible();
  });
});
