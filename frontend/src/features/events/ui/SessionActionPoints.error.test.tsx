import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { httpEventsGateway } from "@/features/events/adapters/http-events-gateway";
import { SessionActionPoints } from "@/features/events/ui/SessionActionPoints";
import { useUsers } from "@/features/identity/api/use-users";

vi.mock("@/components/ui/toast", () => ({ notifyError: vi.fn() }));
vi.mock("@/features/events/adapters/http-events-gateway", () => ({
  httpEventsGateway: { listActionItems: vi.fn() },
}));
vi.mock("@/features/identity/api/use-users", () => ({ useUsers: vi.fn() }));

const gateway = vi.mocked(httpEventsGateway);

function renderComponent() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <SessionActionPoints slug="backend-guild" sessionId={42} />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(useUsers).mockReturnValue({ data: {} } as never);
});

/**
 * The empty state here tells an organizer "capture something". Rendering a failed read as that
 * same state tells them the opposite of the truth, and this section is the only place an action
 * point is visible on the session that raised it, so no other view catches the mistake.
 */
describe("SessionActionPoints when the read fails", () => {
  it("says the list failed rather than showing the empty state", async () => {
    gateway.listActionItems.mockRejectedValue(new Error("boom"));

    renderComponent();

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Could not load action points.",
    );
    expect(screen.queryByText(/No action points/i)).not.toBeInTheDocument();
  });
});
