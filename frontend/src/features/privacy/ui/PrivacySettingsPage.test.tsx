import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { MemoryRouter } from "react-router-dom";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { notifyError } from "@/components/ui/toast";
import { useMe } from "@/features/identity/api/use-me";
import { useDownloadExport } from "@/features/privacy/api/use-download-export";
import { PrivacySettingsPage } from "@/features/privacy/ui/PrivacySettingsPage";

vi.mock("@/components/ui/toast", () => ({ notifyError: vi.fn() }));
vi.mock("@/auth/session", () => ({
  useAuth: () => ({ user: { profile: { sub: "sub-1" } } }),
}));
vi.mock("@/features/identity/api/use-me", () => ({ useMe: vi.fn() }));
vi.mock("@/features/privacy/api/use-download-export", () => ({
  useDownloadExport: vi.fn(),
  classifyDownloadError: vi.fn(() => "unknown"),
  downloadExportErrorMessage: vi.fn(() => "Download failed."),
}));
vi.mock("@/features/privacy/adapters/http-privacy-gateway", () => ({
  httpPrivacyGateway: { erasurePreflight: vi.fn(), eraseAccount: vi.fn() },
}));

const mutate =
  vi.fn<
    (
      variables?: undefined,
      options?: { onError?: (error: unknown) => void },
    ) => void
  >();

function wrapper({ children }: { children: ReactNode }) {
  return (
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter>{children}</MemoryRouter>
    </QueryClientProvider>
  );
}

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  vi.clearAllMocks();
  // mockImplementation survives vi.clearAllMocks (it only clears calls/results),
  // so a per-test override in one test would otherwise leak into the next. Reset
  // it to a no-op default every time so each test starts from the same baseline.
  mutate.mockReset();
  vi.mocked(useMe).mockReturnValue({
    data: {
      id: "sub-1",
      displayName: "Alice Anderson",
      email: "alice@example.com",
      memberStatus: "PRESENT",
    },
  } as unknown as ReturnType<typeof useMe>);
  vi.mocked(useDownloadExport).mockReturnValue({
    isPending: false,
    mutate,
  } as unknown as ReturnType<typeof useDownloadExport>);
});

afterEach(() => {
  vi.restoreAllMocks();
});

describe("PrivacySettingsPage", () => {
  it("asks the hook to download", async () => {
    render(<PrivacySettingsPage />, { wrapper });
    await user.click(screen.getByRole("button", { name: "Download my data" }));
    expect(mutate).toHaveBeenCalledOnce();
  });

  it("disables the download button while the export is pending", () => {
    vi.mocked(useDownloadExport).mockReturnValue({
      isPending: true,
      mutate,
    } as unknown as ReturnType<typeof useDownloadExport>);
    render(<PrivacySettingsPage />, { wrapper });
    expect(screen.getByRole("button", { name: "Preparing…" })).toBeDisabled();
  });

  it("surfaces a failed download as a toast", async () => {
    mutate.mockImplementation((_input, options) =>
      options?.onError?.(new Error("boom")),
    );
    render(<PrivacySettingsPage />, { wrapper });
    await user.click(screen.getByRole("button", { name: "Download my data" }));
    expect(notifyError).toHaveBeenCalledWith("Download failed.");
  });

  it("opens the deletion dialog rather than deleting on the spot", async () => {
    render(<PrivacySettingsPage />, { wrapper });
    await user.click(screen.getByRole("button", { name: "Delete my account" }));
    expect(
      await screen.findByRole("alertdialog", { name: /Delete this account/ }),
    ).toBeInTheDocument();
  });

  it("gives the delete button the danger affordance, not a neutral one", () => {
    render(<PrivacySettingsPage />, { wrapper });
    expect(
      screen.getByRole("button", { name: "Delete my account" }),
    ).toHaveClass("bg-danger");
  });

  it("offers no privacy notice of its own", () => {
    render(<PrivacySettingsPage />, { wrapper });
    expect(
      screen.queryByRole("heading", { name: "Privacy notice" }),
    ).not.toBeInTheDocument();
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
  });
});

describe("PrivacySettingsPage own-export path", () => {
  // The tests above mock useDownloadExport wholesale, which only proves the page calls
  // whatever mutate it is handed - it would pass even if the page requested the organization-wide
  // export by mistake. This test runs the real hook against a spied fetch, the same way the
  // superseded AccountMenu.data-rights.test.tsx did, so the member-scoped path stays covered.
  it("requests the member's own export, not the organization-wide one", async () => {
    const actual = await vi.importActual<
      typeof import("@/features/privacy/api/use-download-export")
    >("@/features/privacy/api/use-download-export");
    vi.mocked(useDownloadExport).mockImplementation(actual.useDownloadExport);
    const fetchSpy = vi
      .spyOn(globalThis, "fetch")
      .mockResolvedValue(new Response("zip-bytes", { status: 200 }));

    render(<PrivacySettingsPage />, { wrapper });
    await user.click(screen.getByRole("button", { name: "Download my data" }));

    await waitFor(() => expect(fetchSpy).toHaveBeenCalled());
    const [requestUrl] = fetchSpy.mock.calls[0] ?? [];
    expect(requestUrl).toEqual(
      expect.stringContaining("/axonia/me/data-export"),
    );
  });
});

describe("PrivacySettingsPage for a person with no organization", () => {
  it("names what is held outside any community and keeps the anonymization clause", () => {
    // when
    render(<PrivacySettingsPage personal />, { wrapper });

    // then
    expect(
      screen.getByText(
        "A machine-readable copy of what is held for this account outside any community.",
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        "Permanently erases this account and anonymizes everything contributed under it. This cannot be undone, and nothing can be restored afterwards.",
      ),
    ).toBeInTheDocument();
    expect(screen.queryByText(/the contributions/)).not.toBeInTheDocument();
  });

  it("keeps the member copy for a hub member", () => {
    // when
    render(<PrivacySettingsPage />, { wrapper });

    // then
    expect(
      screen.getByText(
        "A machine-readable copy of everything recorded against this account: the profile, the contributions and the activity.",
      ),
    ).toBeInTheDocument();
  });
});
