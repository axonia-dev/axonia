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

const SAVED = { ...OPEN, mine: { enjoyed: 9, learned: 7 } };

async function answerBoth(enjoyed: number, learned: number) {
  await user.click((await screen.findAllByRole("radio"))[enjoyed - 1]!);
  await user.click(radiosOf("Learned something")[learned - 1]!);
}

function deferred() {
  let resolve!: (value: SessionFeedback) => void;
  const promise = new Promise<SessionFeedback>((settle) => {
    resolve = settle;
  });
  return { promise, resolve };
}

async function settled() {
  await new Promise((resolve) => setTimeout(resolve, 700));
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
    expect(
      screen.getByText("Anonymous · saves once both are set"),
    ).toBeInTheDocument();
    expect(screen.getByText("Not really")).toBeInTheDocument();
    expect(
      screen.getByText("A lot", { selector: "span:not(.sr-only)" }),
    ).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Send" })).toBeNull();
  });

  it("saves nothing until both rows are answered", async () => {
    // given
    renderCard(OPEN);

    // when
    await user.click((await screen.findAllByRole("radio"))[8]!);
    await settled();

    // then
    expect(gateway.giveSessionFeedback).not.toHaveBeenCalled();
  });

  it("saves both answers on its own and keeps the rows open", async () => {
    // given
    gateway.giveSessionFeedback.mockResolvedValue(SAVED);
    renderCard(OPEN);

    // when
    await answerBoth(9, 7);

    // then
    expect(screen.getByText("Saving…")).toBeInTheDocument();
    expect(await screen.findByText("Saved · anonymous")).toBeInTheDocument();
    expect(gateway.giveSessionFeedback).toHaveBeenCalledExactlyOnceWith(42, {
      enjoyed: 9,
      learned: 7,
    });
    expect(screen.getAllByRole("radiogroup")).toHaveLength(2);
  });

  it("saves only the last of quick changes", async () => {
    // given
    gateway.giveSessionFeedback.mockResolvedValue(SAVED);
    renderCard(OPEN);

    // when
    await answerBoth(9, 7);
    await user.click(radiosOf("Enjoyed it")[4]!);
    await screen.findByText("Saved · anonymous");

    // then
    expect(gateway.giveSessionFeedback).toHaveBeenCalledExactlyOnceWith(42, {
      enjoyed: 5,
      learned: 7,
    });
  });

  it("sends a change made during a save once that save lands", async () => {
    // given
    const first = deferred();
    gateway.giveSessionFeedback
      .mockReturnValueOnce(first.promise)
      .mockResolvedValue(SAVED);
    renderCard(OPEN);
    await answerBoth(9, 7);
    await vi.waitFor(() => {
      expect(gateway.giveSessionFeedback).toHaveBeenCalledTimes(1);
    });

    // when
    await user.click(radiosOf("Learned something")[2]!);
    await settled();
    expect(gateway.giveSessionFeedback).toHaveBeenCalledTimes(1);
    first.resolve(SAVED);

    // then
    await vi.waitFor(() => {
      expect(gateway.giveSessionFeedback).toHaveBeenLastCalledWith(42, {
        enjoyed: 9,
        learned: 3,
      });
    });
    expect(await screen.findByText("Saved · anonymous")).toBeInTheDocument();
  });

  it("saves a pending answer when the card goes away", async () => {
    // given
    gateway.giveSessionFeedback.mockResolvedValue(SAVED);
    const { unmount } = renderCard(OPEN);
    await answerBoth(4, 6);

    // when
    unmount();

    // then
    await vi.waitFor(() => {
      expect(gateway.giveSessionFeedback).toHaveBeenCalledExactlyOnceWith(42, {
        enjoyed: 4,
        learned: 6,
      });
    });
  });

  it("says so when the answer does not go through", async () => {
    // given
    gateway.giveSessionFeedback.mockRejectedValue(new Error("boom"));
    renderCard(OPEN);

    // when
    await answerBoth(9, 7);

    // then
    expect(
      await screen.findByText("Not saved · a tap tries again"),
    ).toBeInTheDocument();
    expect(notifyError).toHaveBeenCalledWith("Feedback not saved");
  });

  it("reopens the rows with the answer already given, and saves a change", async () => {
    // given
    gateway.giveSessionFeedback.mockResolvedValue(SAVED);
    renderCard(SAVED);

    // when
    await user.click(await screen.findByRole("button", { name: "Change" }));

    // then
    expect(radiosOf("Enjoyed it")[8]).toBeChecked();
    expect(radiosOf("Learned something")[6]).toBeChecked();
    expect(screen.getByText("Saved · anonymous")).toBeInTheDocument();

    // when
    await user.click(radiosOf("Learned something")[9]!);

    // then
    await vi.waitFor(() => {
      expect(gateway.giveSessionFeedback).toHaveBeenCalledWith(42, {
        enjoyed: 9,
        learned: 10,
      });
    });
  });

  it("reads back a saved answer as one line", async () => {
    // when
    renderCard(SAVED);

    // then
    expect(await screen.findByText("Feedback saved")).toBeInTheDocument();
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

  it("reads back a closed window without a way to change it", async () => {
    // when
    renderCard({ ...SAVED, window: "CLOSED" });

    // then
    expect(await screen.findByText("Feedback saved")).toBeInTheDocument();
    expect(screen.getByText("Anonymous · closed 13 Nov")).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Change" }),
    ).not.toBeInTheDocument();
  });

  it("never says going", async () => {
    // when
    const { container } = renderCard(SAVED);
    await screen.findByText("Feedback saved");

    // then
    expect(container).not.toHaveTextContent(/going/i);
    await user.click(screen.getByRole("button", { name: "Change" }));
    expect(container).not.toHaveTextContent(/going/i);
  });
});
