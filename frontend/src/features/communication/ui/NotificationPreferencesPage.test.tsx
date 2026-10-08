import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { notifyError } from "@/components/ui/toast";

import type {
  MessageType,
  NotificationPreference,
} from "../domain/notification-preference";

const mockUseNotificationPreferences = vi.fn();
const mockMutateFns = new Map<MessageType, ReturnType<typeof vi.fn>>();

function mutateFor(type: MessageType) {
  let fn = mockMutateFns.get(type);
  if (fn === undefined) {
    fn = vi.fn();
    mockMutateFns.set(type, fn);
  }
  return fn;
}

vi.mock("@/components/ui/toast", () => ({ notifyError: vi.fn() }));

vi.mock("../api/use-notification-preferences", () => ({
  useNotificationPreferences: () => mockUseNotificationPreferences() as never,
  useSetNotificationPreference: (type: MessageType) => ({
    mutate: mutateFor(type),
  }),
}));

import { NotificationPreferencesPage } from "./NotificationPreferencesPage";

function renderPreferences(preferences: NotificationPreference[]) {
  mockUseNotificationPreferences.mockReturnValue({
    data: preferences,
    isLoading: false,
    isError: false,
  });
  render(<NotificationPreferencesPage />);
}

beforeEach(() => {
  vi.clearAllMocks();
  mockMutateFns.clear();
});

describe("NotificationPreferencesPage", () => {
  /**
   * A member's preference governs mail only - it cannot silence the in-app entry, which is
   * what makes turning mail off safe. The `Email` heading is what carries that, so it is
   * asserted as a heading rather than as prose: losing it would leave the switches reading as
   * if they decide whether the message exists at all.
   */
  it("groups the switches under a heading naming the channel they govern", () => {
    renderPreferences([]);

    expect(
      screen.getByRole("heading", { name: "Email", level: 2 }),
    ).toBeInTheDocument();
  });

  it("lists every message type with its current state", async () => {
    renderPreferences([
      { type: "session_upcoming", enabled: true },
      { type: "session_wrap_up", enabled: false },
    ]);

    expect(
      await screen.findByRole("switch", { name: /before a session/i }),
    ).toBeChecked();
    expect(
      screen.getByRole("switch", { name: /after a session/i }),
    ).not.toBeChecked();
  });

  it("defaults a missing type to enabled, mirroring the backend's own default", () => {
    renderPreferences([{ type: "session_upcoming", enabled: true }]);

    expect(
      screen.getByRole("switch", { name: /confirm a session happened/i }),
    ).toBeChecked();
  });

  it("saves a change on that switch's own mutation, not another switch's", async () => {
    const user = userEvent.setup({ delay: null });
    renderPreferences([
      { type: "session_upcoming", enabled: true },
      { type: "session_wrap_up", enabled: true },
    ]);

    await user.click(screen.getByRole("switch", { name: /after a session/i }));

    expect(mutateFor("session_wrap_up")).toHaveBeenCalledWith(
      false,
      expect.objectContaining({ onError: expect.any(Function) as unknown }),
    );
    expect(mutateFor("session_upcoming")).not.toHaveBeenCalled();
  });

  it("toasts an error when the save fails", async () => {
    const user = userEvent.setup({ delay: null });
    renderPreferences([{ type: "session_wrap_up", enabled: true }]);

    await user.click(screen.getByRole("switch", { name: /after a session/i }));

    const [, options] = mutateFor("session_wrap_up").mock.calls[0] as [
      unknown,
      { onError: () => void },
    ];
    options.onError();

    expect(notifyError).toHaveBeenCalledWith(
      "Could not save that change. Try again.",
    );
  });

  it("says the preferences could not load and asks again when told to", async () => {
    // given
    const refetch = vi.fn();
    mockUseNotificationPreferences.mockReturnValue({
      data: undefined,
      isLoading: true,
      isError: false,
      errorUpdateCount: 1,
      isFetching: false,
      refetch,
    });
    render(<NotificationPreferencesPage />);

    // when
    await userEvent.click(screen.getByRole("button", { name: "Try again" }));

    // then
    expect(screen.getByRole("alert")).toHaveTextContent(
      "Could not load notification preferences.",
    );
    expect(screen.queryByText("Loading…")).not.toBeInTheDocument();
    expect(refetch).toHaveBeenCalledOnce();
  });
});
