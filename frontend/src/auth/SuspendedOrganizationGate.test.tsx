import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { SuspendedOrganizationGate } from "@/auth/SuspendedOrganizationGate";
import {
  notifyOrganizationOffline,
  type OrganizationOfflineReason,
} from "@/auth/token";

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
});

vi.mock("@/auth/OrganizationOffline", () => ({
  OrganizationOffline: ({
    reason,
    onRetry,
  }: {
    reason: OrganizationOfflineReason;
    onRetry: () => void;
  }) => (
    <div>
      <p>offline screen: {reason}</p>
      <button onClick={onRetry}>retry</button>
    </div>
  ),
}));

function renderGate() {
  const queryClient = new QueryClient();
  function Wrapper({ children }: Readonly<{ children: ReactNode }>) {
    return (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    );
  }
  return render(
    <Wrapper>
      <SuspendedOrganizationGate>
        <p>the app</p>
      </SuspendedOrganizationGate>
    </Wrapper>,
  );
}

describe("SuspendedOrganizationGate", () => {
  it("renders its children until the backend says the Organization is not serving", () => {
    renderGate();

    expect(screen.getByText("the app")).toBeInTheDocument();
  });

  it.each<OrganizationOfflineReason>(["SUSPENDED", "CLOSED"])(
    "replaces the whole app once notified of %s",
    async (reason) => {
      renderGate();

      act(() => {
        notifyOrganizationOffline(reason);
      });

      expect(
        await screen.findByText(`offline screen: ${reason}`),
      ).toBeInTheDocument();
      expect(screen.queryByText("the app")).not.toBeInTheDocument();
    },
  );

  it("gives the app back on retry, so an unsuspended tab is not stuck forever", async () => {
    renderGate();

    act(() => {
      notifyOrganizationOffline("SUSPENDED");
    });
    await user.click(screen.getByRole("button", { name: "retry" }));

    expect(screen.getByText("the app")).toBeInTheDocument();
    expect(screen.queryByText(/offline screen/)).not.toBeInTheDocument();
  });
});
