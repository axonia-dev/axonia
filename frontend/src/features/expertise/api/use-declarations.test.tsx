import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { expertiseKeys } from "@/features/expertise/api/query-keys";
import {
  useDeclareArea,
  useRemoveDeclaration,
} from "@/features/expertise/api/use-declarations";
import { learningKeys } from "@/features/learning/api/query-keys";

vi.mock("@/features/expertise/adapters/http-expertise-gateway", () => ({
  httpExpertiseGateway: { declare: vi.fn(), removeDeclaration: vi.fn() },
}));
vi.mock("@/components/ui/toast", () => ({ notifyError: vi.fn() }));

import { notifyError } from "@/components/ui/toast";
import { httpExpertiseGateway } from "@/features/expertise/adapters/http-expertise-gateway";
import { ApiError } from "@/lib/api-client";

const gateway = vi.mocked(httpExpertiseGateway);

let client: QueryClient;

function wrapper({ children }: { children: ReactNode }) {
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

beforeEach(() => {
  vi.clearAllMocks();
  client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
});

describe("useDeclareArea", () => {
  it("declares in the community and refreshes the profile, the community's search and the Learning page", async () => {
    // given
    gateway.declare.mockResolvedValue({
      tag: "kafka",
      declaredAt: "2026-09-12T12:00:00Z",
    });
    const invalidate = vi.spyOn(client, "invalidateQueries");
    const { result } = renderHook(() => useDeclareArea("platform"), {
      wrapper,
    });

    // when
    result.current.mutate("Kafka");

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(gateway.declare.mock.calls[0]).toEqual(["platform", "Kafka"]);
    expect(invalidate).toHaveBeenCalledWith({
      queryKey: expertiseKeys.selfView(),
    });
    expect(invalidate).toHaveBeenCalledWith({
      queryKey: expertiseKeys.community("platform"),
    });
    expect(invalidate).toHaveBeenCalledWith({
      queryKey: learningKeys.community("platform"),
    });
  });
});

describe("useRemoveDeclaration", () => {
  it("removes from the declaration's own community", async () => {
    // given
    gateway.removeDeclaration.mockResolvedValue(undefined);
    const invalidate = vi.spyOn(client, "invalidateQueries");
    const { result } = renderHook(() => useRemoveDeclaration(), { wrapper });

    // when
    result.current.mutate({ slug: "data", tag: "spark" });

    // then
    await waitFor(() => expect(result.current.isSuccess).toBe(true));
    expect(gateway.removeDeclaration.mock.calls[0]).toEqual(["data", "spark"]);
    expect(invalidate).toHaveBeenCalledWith({
      queryKey: expertiseKeys.selfView(),
    });
    expect(invalidate).toHaveBeenCalledWith({
      queryKey: expertiseKeys.community("data"),
    });
    expect(invalidate).toHaveBeenCalledWith({
      queryKey: learningKeys.community("data"),
    });
  });

  it("sends one removal for a declaration pressed from two controls before the first lands, and still sends others", async () => {
    // given
    gateway.removeDeclaration.mockReturnValue(new Promise(() => {}));
    const { result } = renderHook(
      () => ({ lane: useRemoveDeclaration(), marker: useRemoveDeclaration() }),
      { wrapper },
    );

    // when
    result.current.lane.mutate({ slug: "data", tag: "spark" });
    result.current.marker.mutate({ slug: "data", tag: "spark" });
    result.current.lane.mutate({ slug: "data", tag: "kafka" });
    result.current.lane.mutate({ slug: "platform", tag: "spark" });

    // then
    await waitFor(() =>
      expect(gateway.removeDeclaration).toHaveBeenCalledTimes(3),
    );
    expect(gateway.removeDeclaration.mock.calls).toEqual([
      ["data", "spark"],
      ["data", "kafka"],
      ["platform", "spark"],
    ]);
  });

  it("reports only the declaration being removed as removing, to every control", async () => {
    // given
    gateway.removeDeclaration.mockReturnValue(new Promise(() => {}));
    const { result } = renderHook(
      () => ({ lane: useRemoveDeclaration(), marker: useRemoveDeclaration() }),
      { wrapper },
    );

    // when
    result.current.lane.mutate({ slug: "data", tag: "spark" });

    // then
    await waitFor(() =>
      expect(
        result.current.marker.isRemoving({ slug: "data", tag: "spark" }),
      ).toBe(true),
    );
    expect(result.current.lane.isRemoving({ slug: "data", tag: "kafka" })).toBe(
      false,
    );
    expect(
      result.current.lane.isRemoving({ slug: "platform", tag: "spark" }),
    ).toBe(false);
  });

  it("says a removal failed, since the row has nowhere to show it", async () => {
    // given
    gateway.removeDeclaration.mockRejectedValue(new Error("offline"));
    const { result } = renderHook(() => useRemoveDeclaration(), { wrapper });

    // when
    result.current.mutate({ slug: "data", tag: "spark" });

    // then
    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(notifyError).toHaveBeenCalledWith("Could not remove that area.");
  });

  it("names the own sign-in the removal needs", async () => {
    // given
    gateway.removeDeclaration.mockRejectedValue(
      new ApiError(403, "refused", "REQUIRES_OWN_SESSION"),
    );
    const { result } = renderHook(() => useRemoveDeclaration(), { wrapper });

    // when
    result.current.mutate({ slug: "data", tag: "spark" });

    // then
    await waitFor(() => expect(result.current.isError).toBe(true));
    expect(notifyError).toHaveBeenCalledWith(
      "Declarations need the person’s own sign-in.",
    );
  });
});
