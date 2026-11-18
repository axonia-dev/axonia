import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, type Mock, vi } from "vitest";

import { httpMentoringGateway } from "@/features/mentoring/adapters/http-mentoring-gateway";
import { mentoringKeys } from "@/features/mentoring/api/query-keys";
import { useMeetingPlan } from "@/features/mentoring/api/use-mentoring";

vi.mock("@/features/mentoring/adapters/http-mentoring-gateway", () => ({
  httpMentoringGateway: {
    planMeeting: vi.fn(),
    changeMeeting: vi.fn(),
    removeMeeting: vi.fn(),
  },
}));

const gateway = vi.mocked(httpMentoringGateway);

let client: QueryClient;

function wrapper({ children }: { children: ReactNode }) {
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

beforeEach(() => {
  vi.clearAllMocks();
  client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
});

type Meeting = ReturnType<typeof useMeetingPlan>;
const input = { on: "2026-11-26", topicIds: [3] };

describe("useMeetingPlan", () => {
  it.each([
    {
      act: (m: Meeting) => m.plan.mutate(input),
      method: (): Mock => gateway.planMeeting,
      args: [11, input],
    },
    {
      act: (m: Meeting) => m.change.mutate({ id: 12, input }),
      method: (): Mock => gateway.changeMeeting,
      args: [11, 12, input],
    },
    {
      act: (m: Meeting) => m.remove.mutate(12),
      method: (): Mock => gateway.removeMeeting,
      args: [11, 12],
    },
  ])(
    "refreshes the journal and My mentoring",
    async ({ act, method, args }) => {
      // given
      method().mockResolvedValue(undefined);
      const invalidate = vi.spyOn(client, "invalidateQueries");
      const { result } = renderHook(() => useMeetingPlan(11), { wrapper });

      // when
      act(result.current);

      // then
      await waitFor(() => expect(method()).toHaveBeenCalled());
      expect(method().mock.calls[0]).toEqual(args);
      await waitFor(() =>
        expect(
          invalidate.mock.calls.map(([filters]) => filters?.queryKey),
        ).toEqual([mentoringKeys.journal(11), mentoringKeys.mine()]),
      );
    },
  );
});
