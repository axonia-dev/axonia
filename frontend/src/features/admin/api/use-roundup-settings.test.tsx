import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { renderHook, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { httpRoundupGateway } from "@/features/admin/adapters/http-roundup-gateway";
import {
  useRoundupPreview,
  useRoundupSettings,
} from "@/features/admin/api/use-roundup-settings";

vi.mock("@/features/admin/adapters/http-roundup-gateway", () => ({
  httpRoundupGateway: {
    getSettings: vi.fn(),
    getPreview: vi.fn(),
  },
}));

const gateway = vi.mocked(httpRoundupGateway);

let client: QueryClient;

function Wrapper({ children }: Readonly<{ children: ReactNode }>) {
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

beforeEach(() => {
  vi.clearAllMocks();
  client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
});

describe("useRoundupSettings", () => {
  it("reads the settings when the roundup applies to this tenant", async () => {
    gateway.getSettings.mockResolvedValue({
      organizationId: "acme",
      enabled: false,
    });

    const { result } = renderHook(() => useRoundupSettings(true), {
      wrapper: Wrapper,
    });

    await waitFor(() =>
      expect(result.current.data).toEqual({
        organizationId: "acme",
        enabled: false,
      }),
    );
  });

  it("asks for nothing where the roundup does not apply, so a hub never provokes a refusal", () => {
    renderHook(() => useRoundupSettings(false), { wrapper: Wrapper });

    expect(gateway.getSettings).not.toHaveBeenCalled();
  });
});

describe("useRoundupPreview", () => {
  it("renders the message only once the preview is actually asked for", () => {
    renderHook(() => useRoundupPreview(false), { wrapper: Wrapper });

    expect(gateway.getPreview).not.toHaveBeenCalled();
  });

  it("reads the rendered message when opened", async () => {
    gateway.getPreview.mockResolvedValue({
      subject: "Acme, last quarter",
      html: "<p>hi</p>",
    });

    const { result } = renderHook(() => useRoundupPreview(true), {
      wrapper: Wrapper,
    });

    await waitFor(() =>
      expect(result.current.data?.subject).toBe("Acme, last quarter"),
    );
  });
});
