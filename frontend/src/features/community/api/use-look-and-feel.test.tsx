import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, renderHook } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { httpCommunityGateway } from "@/features/community/adapters/http-community-gateway";
import { useAddAsset } from "@/features/community/api/use-look-and-feel";
import { fitImage } from "@/lib/fit-image";

vi.mock("@/features/community/adapters/http-community-gateway", () => ({
  httpCommunityGateway: { addAsset: vi.fn() },
}));
vi.mock("@/lib/fit-image", () => ({ fitImage: vi.fn() }));

const gateway = vi.mocked(httpCommunityGateway);
const mockFitImage = vi.mocked(fitImage);

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient();
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

const picked = new File(["huge"], "banner.png", { type: "image/png" });
const fitted = new File(["small"], "banner.png", { type: "image/png" });

beforeEach(() => {
  vi.clearAllMocks();
  mockFitImage.mockResolvedValue(fitted);
  gateway.addAsset.mockResolvedValue(undefined as never);
});

describe("useAddAsset", () => {
  it.each([
    ["hero", { maxEdge: 2560, maxBytes: 5 * 1024 * 1024 }],
    ["badge", { maxEdge: 512, maxBytes: 2 * 1024 * 1024 }],
  ] as const)(
    "fits a %s to the limits the server enforces before uploading",
    async (asset, limits) => {
      // given
      const { result } = renderHook(() => useAddAsset("backend-guild"), {
        wrapper,
      });

      // when
      await act(() => result.current.mutateAsync({ asset, file: picked }));

      // then
      expect(mockFitImage).toHaveBeenCalledWith(picked, limits);
      expect(gateway.addAsset).toHaveBeenCalledWith(
        "backend-guild",
        asset,
        expect.anything(),
      );
      expect(gateway.addAsset.mock.calls[0]?.[2]).toBe(fitted);
    },
  );
});
