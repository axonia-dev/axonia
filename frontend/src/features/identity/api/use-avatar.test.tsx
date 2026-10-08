import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { httpIdentityGateway } from "@/features/identity/adapters/http-identity-gateway";
import { identityKeys } from "@/features/identity/api/query-keys";
import { useSetAvatar } from "@/features/identity/api/use-avatar";
import type { User } from "@/features/identity/domain/user";

describe("useSetAvatar", () => {
  const bare: User = {
    id: "u1",
    displayName: "Gabriel",
    email: "g@x.com",
    memberStatus: "PRESENT",
    avatarIsOwn: false,
    timeZoneIsOwn: false,
    avatarUrl: null,
  };
  /** What `/me/avatar` answers with: the whole profile, now with its picture. */
  const answered: User = { ...bare, avatarUrl: "https://cdn/me.png" };

  let queryClient: QueryClient;

  function wrapper({ children }: Readonly<{ children: ReactNode }>) {
    return (
      <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
    );
  }

  beforeEach(() => {
    queryClient = new QueryClient({
      defaultOptions: { mutations: { retry: false } },
    });
    queryClient.setQueryData(identityKeys.me, bare);
    vi.spyOn(httpIdentityGateway, "setAvatar").mockResolvedValue(answered);
  });

  it("writes the answered profile into the caller's cache as it is", async () => {
    const { result } = renderHook(() => useSetAvatar(), { wrapper });

    result.current.mutate(new File(["x"], "me.png", { type: "image/png" }));

    await waitFor(() => {
      expect(queryClient.getQueryData(identityKeys.me)).toEqual(answered);
    });
  });
});
