import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { usePermissions } from "@/features/admin/api/use-permissions";
import { DataExportPage } from "@/features/privacy/ui/DataExportPage";
import { permissionState } from "@/test/permissions";

vi.mock("@/features/admin/api/use-permissions", () => ({
  usePermissions: vi.fn(),
}));

const mockUsePermissions = vi.mocked(usePermissions);

function renderPage() {
  return render(<DataExportPage />);
}

function successResponse() {
  return new Response("zip-bytes", {
    status: 200,
    headers: {
      "content-disposition":
        'attachment; filename="axonia-export-acme-2026-07-28.zip"',
    },
  });
}

function refusalResponse(status: number, code?: string) {
  const body = code === undefined ? {} : { code };
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/problem+json" },
  });
}

async function clickDownload() {
  const user = userEvent.setup({ delay: null });
  await user.click(await screen.findByRole("button", { name: "Download" }));
}

beforeEach(() => {
  mockUsePermissions.mockReturnValue(permissionState(() => true));
  // Never resolves: the point of the second test is that nothing streams
  // back before the assertion runs, exactly as a real export in flight would.
  vi.spyOn(globalThis, "fetch").mockReturnValue(new Promise(() => undefined));
});

describe("DataExportPage", () => {
  it("states what the package contains and what it does not before offering the download", async () => {
    renderPage();

    expect(
      await screen.findByRole("heading", { name: "Data export" }),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/every row of personal data this organization holds/i),
    ).toBeInTheDocument();
    // The claim that used to be here was false: no exporter emits the
    // community, artifact-type or tag catalogues, so the page must not promise
    // them and must say that a community reference shows an id.
    expect(
      screen.getByText(/shows its id rather than its name/i),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Download" })).toBeEnabled();
  });

  it("shows progress while the archive streams, since nothing is prepared in advance", async () => {
    const user = userEvent.setup({ delay: null });
    renderPage();

    await user.click(await screen.findByRole("button", { name: "Download" }));
    expect(screen.getByRole("button", { name: /Preparing/ })).toBeDisabled();
  });
});

describe("DataExportPage export outcomes", () => {
  it("returns to Download once the archive has streamed, with no error shown", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(successResponse());
    renderPage();

    await clickDownload();

    expect(
      await screen.findByRole("button", { name: "Download" }),
    ).toBeEnabled();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("tells a rate-limit-specific story on the coded 429", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      refusalResponse(429, "EXPORT_RATE_LIMITED"),
    );
    renderPage();

    await clickDownload();

    expect(await screen.findByRole("alert")).toHaveTextContent(/too many/i);
    expect(screen.getByRole("button", { name: "Download" })).toBeEnabled();
  });

  it("names the own sign-in only for the coded REQUIRES_OWN_SESSION refusal", async () => {
    vi.spyOn(globalThis, "fetch").mockResolvedValue(
      refusalResponse(403, "REQUIRES_OWN_SESSION"),
    );
    renderPage();

    await clickDownload();

    expect(await screen.findByRole("alert")).toHaveTextContent(/own sign-in/i);
    expect(screen.getByRole("button", { name: "Download" })).toBeEnabled();
  });

  it("never mentions the own sign-in for a plain, uncoded 403", async () => {
    // A missing permission or a Hub calling the whole-org export both come
    // back as a bare 403 with no code - this must read as a different, honest
    // refusal rather than the own sign-in message.
    vi.spyOn(globalThis, "fetch").mockResolvedValue(refusalResponse(403));
    renderPage();

    await clickDownload();

    const alert = await screen.findByRole("alert");
    expect(alert).not.toHaveTextContent(/own sign-in/i);
    expect(alert).toHaveTextContent(/access/i);
    expect(screen.getByRole("button", { name: "Download" })).toBeEnabled();
  });

  it("recovers from a network failure without leaving the button stuck", async () => {
    vi.spyOn(globalThis, "fetch").mockRejectedValue(
      new TypeError("Failed to fetch"),
    );
    renderPage();

    await clickDownload();

    expect(await screen.findByRole("alert")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Download" })).toBeEnabled();
  });
});
