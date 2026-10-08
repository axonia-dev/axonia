import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useUpdateCommunicationSettings } from "@/features/community/api/use-communication-settings";
import type { CommunicationSettings } from "@/features/community/domain/communication-settings";
import { SessionMessagesCard } from "@/features/community/ui/SessionMessagesCard";

vi.mock("@/features/community/api/use-communication-settings", () => ({
  useUpdateCommunicationSettings: vi.fn(),
}));

const INITIAL: CommunicationSettings = {
  notifySessionUpcoming: true,
  notifySessionWrapUp: true,
  sessionUpcomingLeadDays: 3,
  wrapUpGraceHours: 48,
};

function mockUpdate(overrides?: {
  isPending?: boolean;
  isError?: boolean;
  error?: unknown;
}) {
  const update = vi.fn();
  vi.mocked(useUpdateCommunicationSettings).mockReturnValue({
    mutate: update,
    isPending: false,
    isError: false,
    error: null,
    ...overrides,
  } as never);
  return update;
}

function renderCard(options?: {
  initial?: CommunicationSettings;
  communicationsEnabled?: boolean;
}) {
  const update = mockUpdate();
  render(
    <SessionMessagesCard
      slug="backend-guild"
      initial={options?.initial ?? INITIAL}
      communicationsEnabled={options?.communicationsEnabled ?? true}
    />,
  );
  return { update };
}

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.clearAllMocks();
});

describe("SessionMessagesCard", () => {
  it("carries no explanatory paragraph under either switch", () => {
    renderCard();

    expect(screen.queryByText(/heads-up before each session/i)).toBeNull();
    expect(screen.queryByText(/recording, notes/i)).toBeNull();
  });

  it("reads each timing as a sentence with its unit attached", () => {
    renderCard();

    expect(screen.getByText(/before the session/)).toBeVisible();
    expect(
      screen.getByLabelText("Days of notice before a session"),
    ).toHaveValue("3");
    expect(screen.getByText(/after a session is held/)).toBeVisible();
  });

  it("names each switch from its row's own visible label, rendered exactly once", () => {
    renderCard();

    expect(
      screen.getByRole("switch", { name: "Session reminders" }),
    ).toBeInTheDocument();
    expect(screen.getAllByText("Session reminders")).toHaveLength(1);
    expect(
      screen.getByRole("switch", { name: "Session wrap-ups" }),
    ).toBeInTheDocument();
    expect(screen.getAllByText("Session wrap-ups")).toHaveLength(1);
  });

  it("saves both switches and both numbers in one request", async () => {
    const { update } = renderCard();

    await user.click(screen.getByRole("switch", { name: "Session wrap-ups" }));
    await user.click(screen.getByRole("button", { name: "Save" }));

    expect(update).toHaveBeenCalledWith({
      notifySessionUpcoming: true,
      notifySessionWrapUp: false,
      sessionUpcomingLeadDays: 3,
      wrapUpGraceHours: 48,
    });
  });

  it("shows the organization kill-switch notice when communications are disabled", () => {
    renderCard({ communicationsEnabled: false });

    expect(
      screen.getByText(/turned off for the whole organization/i),
    ).toBeInTheDocument();
  });

  it("shows no organization notice when communications are enabled", () => {
    renderCard({ communicationsEnabled: true });

    expect(
      screen.queryByText(/turned off for the whole organization/i),
    ).toBeNull();
  });

  it("disables Save while the field is cleared mid-edit", async () => {
    renderCard();

    await user.clear(screen.getByLabelText("Days of notice before a session"));
    await user.tab();

    expect(screen.getByRole("button", { name: "Save" })).toBeDisabled();
  });

  it("states the bound on the field itself when a whole number exceeds it, rather than silently rewriting it", async () => {
    renderCard();

    const hours = screen.getByLabelText("Hours before the wrap-up sends");
    await user.clear(hours);
    await user.type(hours, "337");
    await user.tab();

    expect(hours).toHaveValue("337");
    expect(
      screen.getByText(/enter a whole number from 0 to 336/i),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Save" })).toBeDisabled();
  });

  it("states the bound on the field itself for a fractional value", async () => {
    renderCard();

    const days = screen.getByLabelText("Days of notice before a session");
    await user.clear(days);
    await user.type(days, "3.5");
    await user.tab();

    expect(days).toHaveValue("3.5");
    expect(
      screen.getByText(/enter a whole number from 0 to 30/i),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Save" })).toBeDisabled();
  });

  it("states the bound on the field itself for a pasted under-min value", async () => {
    renderCard();

    const days = screen.getByLabelText("Days of notice before a session");
    await user.clear(days);
    await user.paste("-5");

    expect(days).toHaveValue("-5");
    expect(
      screen.getByText(/enter a whole number from 0 to 30/i),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Save" })).toBeDisabled();
  });

  it("surfaces a save error", () => {
    mockUpdate({ isError: true, error: new Error("boom") });

    render(
      <SessionMessagesCard
        slug="backend-guild"
        initial={INITIAL}
        communicationsEnabled
      />,
    );

    expect(screen.getByRole("alert")).toHaveTextContent(
      "Something went wrong. Please try again.",
    );
  });
});
