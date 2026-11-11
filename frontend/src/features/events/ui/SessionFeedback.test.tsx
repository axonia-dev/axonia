import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { DelegatedSessionProvider } from "@/auth/delegated-session";
import { httpEventsGateway } from "@/features/events/adapters/http-events-gateway";
import { sessionKeys } from "@/features/events/api/query-keys";
import type {
  FeedbackResult,
  SessionFeedback as Feedback,
} from "@/features/events/domain/session-feedback";
import {
  SessionFeedback,
  SessionFeedbackResult,
} from "@/features/events/ui/SessionFeedback";
import { prefersReducedMotion, restoreMotion } from "@/test/motion";
import { aScheduledSession } from "@/test/session-fixture";

vi.mock("@/features/events/adapters/http-events-gateway", () => ({
  httpEventsGateway: {
    getSessionFeedback: vi.fn(),
    giveSessionFeedback: vi.fn(),
  },
}));

const gateway = vi.mocked(httpEventsGateway);

const session = aScheduledSession({
  id: 42,
  status: "HELD",
  scheduledAt: "2026-11-06T12:00:00Z",
});

const REVEALED: FeedbackResult = {
  state: "REVEALED",
  answers: 9,
  enjoyed: 9.1,
  learned: 7.4,
  importedFrom: null,
};

const PENDING: FeedbackResult = {
  state: "PENDING",
  answers: 0,
  enjoyed: null,
  learned: null,
  importedFrom: null,
};

function feedback(overrides: Partial<Feedback>): Feedback {
  return {
    window: "OPEN",
    closesAt: "2026-11-13T12:00:00Z",
    role: "ATTENDEE",
    mine: null,
    result: null,
    ...overrides,
  };
}

async function renderFeedback(state: Feedback, delegated = false) {
  gateway.getSessionFeedback.mockResolvedValue(state);
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  const tree = (
    <QueryClientProvider client={client}>
      <section id="feedback">
        <SessionFeedback slug="dojo" session={session} />
      </section>
      <SessionFeedbackResult session={session} />
    </QueryClientProvider>
  );
  const view = render(
    delegated ? (
      <DelegatedSessionProvider>{tree}</DelegatedSessionProvider>
    ) : (
      tree
    ),
  );
  await waitFor(() => {
    expect(client.getQueryData(sessionKeys.feedback(42))).toBeDefined();
  });
  expect(gateway.getSessionFeedback).toHaveBeenCalledWith(42);
  return view;
}

type Shown = "card" | "receipt" | "line" | "result";

function shown(): Shown[] {
  const found: Shown[] = [];
  if (screen.queryByRole("heading", { name: "How was it?" }))
    found.push("card");
  if (screen.queryByText("Feedback sent")) found.push("receipt");
  if (screen.queryByText(/Attendees are sharing/)) found.push("line");
  if (screen.queryByRole("heading", { name: /How it landed/ }))
    found.push("result");
  return found;
}

function feedbackSection(): HTMLElement {
  const section = document.getElementById("feedback");
  if (!section) throw new Error("no feedback section");
  return section;
}

beforeEach(() => {
  vi.clearAllMocks();
});

afterEach(() => {
  restoreMotion();
  window.history.replaceState(null, "", "/");
});

describe("SessionFeedback", () => {
  it.each<[string, Partial<Feedback>, Shown[]]>([
    ["an attendee while the window is open", {}, ["card"]],
    [
      "an attendee who answered while it is open",
      { mine: { enjoyed: 9, learned: 7 } },
      ["receipt"],
    ],
    [
      "an attendee who answered once it closed",
      { window: "CLOSED", mine: { enjoyed: 9, learned: 7 } },
      ["receipt"],
    ],
    ["an attendee who never answered once it closed", { window: "CLOSED" }, []],
    [
      "an attendee of a session with no window",
      { window: "NONE", closesAt: null },
      [],
    ],
    ["a presenter while the window is open", { role: "PRESENTER" }, ["line"]],
    ["a presenter once it closed", { role: "PRESENTER", window: "CLOSED" }, []],
    ["anyone else while it is open", { role: "OTHER" }, []],
    ["an organizer while it is open", { role: "OTHER", result: PENDING }, []],
    [
      "an organizer once it closed",
      { role: "OTHER", window: "CLOSED", result: REVEALED },
      ["result"],
    ],
    [
      "an organizer of an imported session",
      {
        role: "OTHER",
        window: "NONE",
        closesAt: null,
        result: { ...REVEALED, importedFrom: "Microsoft Forms" },
      },
      ["result"],
    ],
    [
      "a presenter who organizes, while it is open",
      { role: "PRESENTER", result: PENDING },
      ["line"],
    ],
    [
      "a presenter who organizes, with a result to show",
      { role: "PRESENTER", result: REVEALED },
      ["line", "result"],
    ],
    [
      "a presenter who organizes, once it closed",
      { role: "PRESENTER", window: "CLOSED", result: REVEALED },
      ["result"],
    ],
  ])("shows %s exactly what is theirs", async (_, overrides, expected) => {
    // when
    const { container } = await renderFeedback(feedback(overrides));

    // then
    if (expected.length > 0) {
      await waitFor(() => {
        expect(shown()).toEqual(expected);
      });
    } else {
      expect(shown()).toEqual([]);
    }
    const asked = expected.some((part) => part !== "result");
    if (!asked) expect(feedbackSection()).toBeEmptyDOMElement();
    expect(container).not.toHaveTextContent(/going/i);
  });

  it("asks nothing in a delegated session, even of an attendee", async () => {
    await renderFeedback(feedback({}), true);
    expect(shown()).toEqual([]);
  });

  it("keeps how it landed outside the feedback section", async () => {
    // given
    await renderFeedback(feedback({ role: "PRESENTER", result: REVEALED }));

    // when
    const line = await screen.findByText(/Attendees are sharing/);

    // then
    expect(line).toHaveTextContent(
      "Attendees are sharing how it went until 13 Nov.",
    );
    expect(feedbackSection()).toContainElement(line);
    expect(feedbackSection()).not.toContainElement(
      screen.getByRole("heading", { name: /How it landed/ }),
    );
  });

  it("brings the section into view once when a link names it", async () => {
    // given
    prefersReducedMotion(false);
    window.history.replaceState(null, "", "/c/dojo/sessions/42#feedback");
    const scroll = vi.spyOn(Element.prototype, "scrollIntoView");

    // when
    await renderFeedback(feedback({}));

    // then
    await waitFor(() => {
      expect(scroll).toHaveBeenCalledOnce();
    });
    expect(scroll.mock.contexts[0]).toBe(feedbackSection());
    expect(scroll).toHaveBeenCalledWith({ block: "start", behavior: "smooth" });
    scroll.mockRestore();
  });

  it("jumps rather than glides under reduced motion", async () => {
    // given
    prefersReducedMotion(true);
    window.history.replaceState(null, "", "/c/dojo/sessions/42#feedback");
    const scroll = vi.spyOn(Element.prototype, "scrollIntoView");

    // when
    await renderFeedback(feedback({}));

    // then
    await waitFor(() => {
      expect(scroll).toHaveBeenCalledWith({
        block: "start",
        behavior: "instant",
      });
    });
    scroll.mockRestore();
  });

  it("leaves the page where it is without the fragment", async () => {
    // given
    const scroll = vi.spyOn(Element.prototype, "scrollIntoView");

    // when
    await renderFeedback(feedback({}));

    // then
    await screen.findByRole("heading", { name: "How was it?" });
    expect(scroll).not.toHaveBeenCalled();
    scroll.mockRestore();
  });

  it("does not scroll to a section with nothing in it", async () => {
    // given
    window.history.replaceState(null, "", "/c/dojo/sessions/42#feedback");
    const scroll = vi.spyOn(Element.prototype, "scrollIntoView");

    // when
    await renderFeedback(feedback({ role: "OTHER", window: "CLOSED" }));

    // then
    expect(feedbackSection()).toBeEmptyDOMElement();
    expect(scroll).not.toHaveBeenCalled();
    scroll.mockRestore();
  });
});
