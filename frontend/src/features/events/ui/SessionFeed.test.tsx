import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";

import { SessionFeed } from "@/features/events/ui/SessionFeed";
import { aScheduledSession } from "@/test/session-fixture";

vi.mock("@/features/events/ui/SessionCard", () => ({
  SessionCard: ({ session }: { session: { id: number } }) => (
    <div>session {session.id}</div>
  ),
}));

describe("SessionFeed", () => {
  it("hints when empty", () => {
    render(
      <SessionFeed
        slug="g"
        sessions={[]}
        isLoading={false}
        isError={false}
        canManage={false}
        canQueueTopics={false}
      />,
    );
    expect(screen.getByText(/no upcoming sessions yet/i)).toBeInTheDocument();
  });
  it("lists sessions", () => {
    render(
      <SessionFeed
        slug="g"
        sessions={[aScheduledSession({ id: 9 })]}
        isLoading={false}
        isError={false}
        canManage={false}
        canQueueTopics={false}
      />,
    );
    expect(screen.getByText("session 9")).toBeInTheDocument();
  });
  it("shows loading text while fetching", () => {
    render(
      <SessionFeed
        slug="g"
        sessions={[]}
        isLoading={true}
        isError={false}
        canManage={false}
        canQueueTopics={false}
      />,
    );
    expect(screen.getByText("Loading…")).toBeInTheDocument();
  });
  it("shows an alert on error", () => {
    render(
      <SessionFeed
        slug="g"
        sessions={[]}
        isLoading={false}
        isError={true}
        canManage={false}
        canQueueTopics={false}
      />,
    );
    expect(screen.getByRole("alert")).toHaveTextContent(
      "Could not load upcoming sessions.",
    );
  });
});
