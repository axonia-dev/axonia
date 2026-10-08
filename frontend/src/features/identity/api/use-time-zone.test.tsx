import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { httpIdentityGateway } from "@/features/identity/adapters/http-identity-gateway";
import { identityKeys } from "@/features/identity/api/query-keys";
import {
  useDeclareTimeZone,
  useReportObservedZone,
} from "@/features/identity/api/use-time-zone";
import type { User } from "@/features/identity/domain/user";

// vite.config.ts pins TZ=America/Los_Angeles, so that is what this browser reads.
const BROWSER_ZONE = "America/Los_Angeles";

const bare: User = {
  id: "u1",
  displayName: "Gabriel",
  email: "g@x.com",
  memberStatus: "PRESENT",
  avatarIsOwn: false,
  timeZoneIsOwn: false,
  avatarUrl: null,
};

let queryClient: QueryClient;

function wrapper({ children }: Readonly<{ children: ReactNode }>) {
  return (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
}

function givenRecordedProfile(me: User) {
  vi.spyOn(httpIdentityGateway, "me").mockResolvedValue(me);
  vi.spyOn(httpIdentityGateway, "resolve").mockResolvedValue([]);
}

beforeEach(() => {
  queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false },
      mutations: { retry: false },
    },
  });
});

describe("useReportObservedZone", () => {
  it("reports this browser's zone when the recorded one has drifted", async () => {
    const report = vi
      .spyOn(httpIdentityGateway, "reportObservedTimeZone")
      .mockResolvedValue({ ...bare, observedTimeZone: BROWSER_ZONE });
    givenRecordedProfile({ ...bare, observedTimeZone: "Europe/Brussels" });

    renderHook(() => useReportObservedZone(), { wrapper });

    await waitFor(() => expect(report).toHaveBeenCalledWith(BROWSER_ZONE));
  });

  it("sends nothing when the recorded zone already agrees", async () => {
    const report = vi.spyOn(httpIdentityGateway, "reportObservedTimeZone");
    givenRecordedProfile({ ...bare, observedTimeZone: BROWSER_ZONE });

    renderHook(() => useReportObservedZone(), { wrapper });

    // Awaiting the profile itself, not a bare tick: a `not.toHaveBeenCalled` immediately after
    // render would pass before the query had resolved, which is to say for the wrong reason.
    await waitFor(() =>
      expect(
        queryClient.getQueryData<User>(identityKeys.me)?.observedTimeZone,
      ).toBe(BROWSER_ZONE),
    );
    expect(report).not.toHaveBeenCalled();
  });

  it("reports a member nobody has measured yet", async () => {
    const report = vi
      .spyOn(httpIdentityGateway, "reportObservedTimeZone")
      .mockResolvedValue({ ...bare, observedTimeZone: BROWSER_ZONE });
    givenRecordedProfile(bare);

    renderHook(() => useReportObservedZone(), { wrapper });

    await waitFor(() => expect(report).toHaveBeenCalledWith(BROWSER_ZONE));
  });

  it("survives a report nobody could record", async () => {
    vi.spyOn(httpIdentityGateway, "reportObservedTimeZone").mockRejectedValue(
      new Error("offline"),
    );
    givenRecordedProfile({ ...bare, observedTimeZone: "Europe/Brussels" });

    const { result } = renderHook(() => useReportObservedZone(), { wrapper });

    await waitFor(() => expect(result.current).toBeUndefined());
  });
});

describe("useDeclareTimeZone", () => {
  it("writes the answered profile into the caller's cache as it is", async () => {
    queryClient.setQueryData<User>(identityKeys.me, bare);
    const declared: User = {
      ...bare,
      timeZone: "Asia/Kolkata",
      timeZoneIsOwn: true,
    };
    vi.spyOn(httpIdentityGateway, "declareTimeZone").mockResolvedValue(
      declared,
    );
    const { result } = renderHook(() => useDeclareTimeZone(), { wrapper });

    result.current.mutate("Asia/Kolkata");

    await waitFor(() => {
      expect(queryClient.getQueryData(identityKeys.me)).toEqual(declared);
    });
  });
});
