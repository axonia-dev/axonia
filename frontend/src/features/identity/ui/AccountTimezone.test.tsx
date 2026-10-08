import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { httpIdentityGateway } from "@/features/identity/adapters/http-identity-gateway";
import { identityKeys } from "@/features/identity/api/query-keys";
import type { User } from "@/features/identity/domain/user";
import { AccountTimezone } from "@/features/identity/ui/AccountTimezone";

const base: User = {
  id: "u1",
  displayName: "Gabriel",
  email: "g@x.com",
  memberStatus: "PRESENT",
  avatarIsOwn: false,
  timeZoneIsOwn: false,
  avatarUrl: null,
};

let queryClient: QueryClient;

function renderCard(me: Partial<User>) {
  queryClient.setQueryData<User>(identityKeys.me, { ...base, ...me });
  function Wrapper({ children }: Readonly<{ children: ReactNode }>) {
    return (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    );
  }
  return render(<AccountTimezone />, { wrapper: Wrapper });
}

afterEach(() => {
  vi.useRealTimers();
});

beforeEach(() => {
  queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });
});

describe("AccountTimezone", () => {
  it("states the zone with Automatic selected when nobody declared one", () => {
    renderCard({
      timeZone: "Europe/Brussels",
      observedTimeZone: "Europe/Brussels",
    });

    expect(screen.getByText("Europe/Brussels")).toBeInTheDocument();
    expect(screen.getByRole("radio", { name: "Automatic" })).toBeChecked();
    expect(screen.queryByRole("combobox")).not.toBeInTheDocument();
  });

  it("reveals the picker only once the member takes it over", () => {
    renderCard({
      timeZone: "America/New_York",
      observedTimeZone: "Europe/Brussels",
      timeZoneIsOwn: true,
    });

    expect(screen.getByRole("radio", { name: "Manual" })).toBeChecked();
    expect(screen.getByRole("combobox")).toHaveValue("America/New_York");
    // The picker states the zone, so a line under the clock would be the same name twice.
    expect(screen.queryByText("America/New_York")).not.toBeInTheDocument();
  });

  it("clears rather than writing a second value when Automatic is chosen again", async () => {
    const clear = vi
      .spyOn(httpIdentityGateway, "clearTimeZone")
      .mockResolvedValue(base);
    const declare = vi.spyOn(httpIdentityGateway, "declareTimeZone");
    const user = userEvent.setup();
    renderCard({
      timeZone: "America/New_York",
      observedTimeZone: "Europe/Brussels",
      timeZoneIsOwn: true,
    });

    await user.click(screen.getByRole("radio", { name: "Automatic" }));

    expect(clear).toHaveBeenCalled();
    expect(declare).not.toHaveBeenCalled();
  });

  it("declares the zone already on screen when the member takes it over", async () => {
    const declare = vi
      .spyOn(httpIdentityGateway, "declareTimeZone")
      .mockResolvedValue(base);
    const user = userEvent.setup();
    renderCard({
      timeZone: "Europe/Brussels",
      observedTimeZone: "Europe/Brussels",
    });

    await user.click(screen.getByRole("radio", { name: "Manual" }));

    expect(declare).toHaveBeenCalledWith("Europe/Brussels");
  });

  it("keeps the clock on the time it is, not the time the page was opened", () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date("2026-06-15T10:59:30Z"));
    renderCard({
      timeZone: "Europe/Brussels",
      observedTimeZone: "Europe/Brussels",
    });
    expect(screen.getByText("12:59")).toBeInTheDocument();

    act(() => {
      vi.advanceTimersByTime(31_000);
    });

    expect(screen.getByText("13:00")).toBeInTheDocument();
  });

  /** Facts and one control. A two-option toggle beside a named zone needs no sentence under it. */
  it("carries no explanatory prose", () => {
    renderCard({
      timeZone: "Europe/Brussels",
      observedTimeZone: "Europe/Brussels",
    });

    expect(screen.getByText("Timezone")).toBeInTheDocument();
    expect(screen.queryByText(/browser/i)).not.toBeInTheDocument();
    expect(screen.queryByText(/session/i)).not.toBeInTheDocument();
  });
});
