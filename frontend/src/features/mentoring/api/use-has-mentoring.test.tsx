import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { httpMentoringGateway } from "@/features/mentoring/adapters/http-mentoring-gateway";
import { useHasMentoring } from "@/features/mentoring/api/use-mentoring";

vi.mock("@/features/mentoring/adapters/http-mentoring-gateway", () => ({
  httpMentoringGateway: { mine: vi.fn(), presence: vi.fn() },
}));

const gateway = vi.mocked(httpMentoringGateway);

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe("useHasMentoring", () => {
  it.each([
    ["sits in a mentoring guild", true],
    ["sits in none", false],
  ])("follows the presence probe when the member %s", async (_name, value) => {
    // given
    gateway.presence.mockResolvedValue({ inAMentoringGuild: value });

    // when
    const { result } = renderHook(() => useHasMentoring(), { wrapper });

    // then
    await waitFor(() => expect(gateway.presence).toHaveBeenCalled());
    await waitFor(() => expect(result.current).toBe(value));
    expect(gateway.mine).not.toHaveBeenCalled();
  });

  it("is false while loading", () => {
    // given
    gateway.presence.mockReturnValue(new Promise(() => {}));

    // when
    const { result } = renderHook(() => useHasMentoring(), { wrapper });

    // then
    expect(result.current).toBe(false);
  });
});
