import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { notifyError } from "@/components/ui/toast";
import { httpEventsGateway } from "@/features/events/adapters/http-events-gateway";
import { useSessionFeedback } from "@/features/events/api/use-session-feedback";
import type { SessionFeedback } from "@/features/events/domain/session-feedback";
import { SessionFeedbackCard } from "@/features/events/ui/SessionFeedbackCard";
import { prefersReducedMotion, restoreMotion } from "@/test/motion";

vi.mock("@/components/ui/toast", () => ({ notifyError: vi.fn() }));
vi.mock("@/features/events/adapters/http-events-gateway", () => ({
  httpEventsGateway: {
    getSessionFeedback: vi.fn(),
    giveSessionFeedback: vi.fn(),
  },
}));

const gateway = vi.mocked(httpEventsGateway);

const OPEN: SessionFeedback = {
  window: "OPEN",
  closesAt: "2026-11-13T12:00:00Z",
  role: "ATTENDEE",
  mine: null,
  result: null,
};

function Harness() {
  const { data } = useSessionFeedback(42);
  if (!data) return null;
  return <SessionFeedbackCard slug="dojo" sessionId={42} feedback={data} />;
}

function renderCard(feedback: SessionFeedback) {
  gateway.getSessionFeedback.mockResolvedValue(feedback);
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <Harness />
    </QueryClientProvider>,
  );
}

function radiosOf(name: string) {
  return within(screen.getByRole("radiogroup", { name })).getAllByRole("radio");
}

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.clearAllMocks();
  prefersReducedMotion(false);
});

afterEach(() => {
  restoreMotion();
});

describe("SessionFeedbackCard", () => {
  it("asks how it was and says until when", async () => {
    // when
    renderCard(OPEN);

    // then
    expect(
      await screen.findByRole("heading", { name: "How was it?" }),
    ).toBeInTheDocument();
    expect(screen.getByText("Open until 13 Nov")).toBeInTheDocument();
    expect(screen.getByText("Anonymous")).toBeInTheDocument();
    expect(screen.getByText("Not really")).toBeInTheDocument();
    expect(
      screen.getByText("A lot", { selector: "span:not(.sr-only)" }),
    ).toBeInTheDocument();
  });

  it("keeps Send disabled until both rows are answered", async () => {
    // given
    renderCard(OPEN);
    const send = await screen.findByRole("button", { name: "Send" });
    expect(send).toBeDisabled();

    // when
    await user.click(radiosOf("Enjoyed it")[8]!);

    // then
    expect(send).toBeDisabled();

    // when
    await user.click(radiosOf("Learned something")[6]!);

    // then
    expect(send).toBeEnabled();
  });

  it("sends both answers and folds into the receipt", async () => {
    // given
    gateway.giveSessionFeedback.mockResolvedValue({
      ...OPEN,
      mine: { enjoyed: 9, learned: 7 },
    });
    renderCard(OPEN);
    await user.click((await screen.findAllByRole("radio"))[8]!);
    await user.click(radiosOf("Learned something")[6]!);

    // when
    await user.click(screen.getByRole("button", { name: "Send" }));

    // then
    expect(gateway.giveSessionFeedback).toHaveBeenCalledWith(42, {
      enjoyed: 9,
      learned: 7,
    });
    expect(await screen.findByText("Feedback sent")).toBeInTheDocument();
    expect(
      screen.getByText("Anonymous · open until 13 Nov"),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("img", { name: "Enjoyed Loved it" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("img", { name: "Learned Useful" }),
    ).toBeInTheDocument();
    expect(screen.queryByRole("radiogroup")).not.toBeInTheDocument();
  });

  it("folds at once under reduced motion", async () => {
    // given
    prefersReducedMotion(true);
    gateway.giveSessionFeedback.mockResolvedValue({
      ...OPEN,
      mine: { enjoyed: 2, learned: 3 },
    });
    renderCard(OPEN);
    await user.click((await screen.findAllByRole("radio"))[1]!);
    await user.click(radiosOf("Learned something")[2]!);

    // when
    await user.click(screen.getByRole("button", { name: "Send" }));

    // then
    expect(await screen.findByText("Feedback sent")).toBeInTheDocument();
  });

  it("says so when the answer does not go through", async () => {
    // given
    gateway.giveSessionFeedback.mockRejectedValue(new Error("boom"));
    renderCard(OPEN);
    await user.click((await screen.findAllByRole("radio"))[8]!);
    await user.click(radiosOf("Learned something")[6]!);

    // when
    await user.click(screen.getByRole("button", { name: "Send" }));

    // then
    await vi.waitFor(() => {
      expect(notifyError).toHaveBeenCalledWith("Feedback not sent");
    });
    expect(screen.getByRole("button", { name: "Send" })).toBeInTheDocument();
  });

  it("reopens the rows with the answer already given", async () => {
    // given
    renderCard({ ...OPEN, mine: { enjoyed: 9, learned: 7 } });

    // when
    await user.click(await screen.findByRole("button", { name: "Change" }));

    // then
    expect(radiosOf("Enjoyed it")[8]).toBeChecked();
    expect(radiosOf("Learned something")[6]).toBeChecked();
    expect(screen.getByRole("button", { name: "Send" })).toBeEnabled();
  });

  it("reads back a closed window without a way to change it", async () => {
    // when
    renderCard({ ...OPEN, window: "CLOSED", mine: { enjoyed: 9, learned: 7 } });

    // then
    expect(await screen.findByText("Feedback sent")).toBeInTheDocument();
    expect(screen.getByText("Anonymous · closed 13 Nov")).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Change" }),
    ).not.toBeInTheDocument();
  });

  it("never says going", async () => {
    // when
    const { container } = renderCard({
      ...OPEN,
      mine: { enjoyed: 9, learned: 7 },
    });
    await screen.findByText("Feedback sent");

    // then
    expect(container).not.toHaveTextContent(/going/i);
    await user.click(screen.getByRole("button", { name: "Change" }));
    expect(container).not.toHaveTextContent(/going/i);
  });
});
