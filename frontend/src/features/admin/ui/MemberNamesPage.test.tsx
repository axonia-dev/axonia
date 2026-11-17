import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { httpAdminGateway } from "@/features/admin/adapters/http-admin-gateway";
import { MemberNamesPage } from "@/features/admin/ui/MemberNamesPage";
import { ApiError } from "@/lib/api-client";
import { permissionState } from "@/test/permissions";

const mockUsePermissions = vi.fn();

vi.mock("@/features/admin/api/use-permissions", () => ({
  usePermissions: () => mockUsePermissions() as never,
}));

vi.mock("@/features/admin/adapters/http-admin-gateway", () => ({
  httpAdminGateway: {
    getMemberNamePatterns: vi.fn(),
    previewMemberNames: vi.fn(),
    saveMemberNamePatterns: vi.fn(),
  },
}));

const gateway = vi.mocked(httpAdminGateway);

const PATTERN = "{family}, {given}-ACME";
const ROW = {
  received: "Rochat, Aurélia-ACME",
  displayName: "Aurélia Rochat",
  givenName: "Aurélia",
  matched: 0,
};

function renderPage() {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return render(
    <QueryClientProvider client={client}>
      <MemberNamesPage />
    </QueryClientProvider>,
  );
}

async function pageWith(patterns: string[]) {
  gateway.getMemberNamePatterns.mockResolvedValue(patterns);
  renderPage();
  await screen.findByRole("button", { name: /Add pattern/ }).catch(() => null);
}

beforeEach(() => {
  vi.clearAllMocks();
  mockUsePermissions.mockReturnValue(permissionState(() => true));
  gateway.previewMemberNames.mockResolvedValue([ROW]);
  gateway.saveMemberNamePatterns.mockResolvedValue(2);
});

describe("MemberNamesPage", () => {
  it("renders the stored patterns in order", async () => {
    await pageWith([PATTERN, "{given} {family}-X"]);

    const fields = await screen.findAllByRole("textbox");
    expect(fields.map((f) => (f as HTMLInputElement).value)).toEqual([
      PATTERN,
      "{given} {family}-X",
    ]);
  });

  it("appends an empty field for Add pattern", async () => {
    const user = userEvent.setup();
    await pageWith([PATTERN]);
    await screen.findByDisplayValue(PATTERN);

    await user.click(screen.getByRole("button", { name: "Add pattern" }));

    expect(screen.getAllByRole("textbox")).toHaveLength(2);
  });

  it("inserts a placeholder at the caret", async () => {
    const user = userEvent.setup();
    await pageWith(["ab"]);
    const field = (await screen.findByDisplayValue("ab")) as HTMLInputElement;
    await user.click(field);
    field.setSelectionRange(1, 1);

    await user.click(screen.getByRole("button", { name: "Insert {given}" }));

    expect(field).toHaveValue("a{given}b");
  });

  it("previews the rows under Received, Shown as and Given name", async () => {
    await pageWith([PATTERN]);

    const table = await screen.findByRole("table");
    expect(
      within(table)
        .getAllByRole("columnheader")
        .map((h) => h.textContent),
    ).toEqual(["Received", "Shown as", "Given name", "Pattern"]);
    expect(await within(table).findByText("Aurélia Rochat")).toBeVisible();
  });

  it("refreshes the preview, debounced, as the patterns change", async () => {
    const user = userEvent.setup();
    await pageWith([PATTERN]);
    await screen.findByText("Aurélia Rochat");
    gateway.previewMemberNames.mockClear();

    await user.type(screen.getByDisplayValue(PATTERN), "Z");

    await waitFor(() =>
      expect(gateway.previewMemberNames).toHaveBeenCalledTimes(1),
    );
    expect(gateway.previewMemberNames).toHaveBeenCalledWith([`${PATTERN}Z`]);
  });

  it("shows the server message beside the invalid field and disables Save", async () => {
    const user = userEvent.setup();
    await pageWith([PATTERN]);
    await screen.findByText("Aurélia Rochat");
    gateway.previewMemberNames.mockRejectedValue(
      Object.assign(
        new ApiError(400, "bad", "NAME_PATTERN_INVALID", "Needs both parts."),
        { field: "patterns[0]" },
      ),
    );

    await user.type(screen.getByDisplayValue(PATTERN), "Z");

    expect(await screen.findByText("Needs both parts.")).toBeVisible();
    expect(screen.getByRole("button", { name: "Save" })).toBeDisabled();
  });

  it("saves and reports how many names changed", async () => {
    const user = userEvent.setup();
    await pageWith([PATTERN]);
    await screen.findByText("Aurélia Rochat");
    await user.type(screen.getByDisplayValue(PATTERN), "Z");
    await waitFor(() =>
      expect(screen.getByRole("button", { name: "Save" })).toBeEnabled(),
    );

    await user.click(screen.getByRole("button", { name: "Save" }));

    expect(await screen.findByText("2 names changed")).toBeVisible();
    expect(gateway.saveMemberNamePatterns).toHaveBeenCalledWith([
      `${PATTERN}Z`,
    ]);
  });

  it("reorders and removes patterns", async () => {
    const user = userEvent.setup();
    await pageWith(["a", "b"]);
    await screen.findByDisplayValue("a");

    await user.click(
      screen.getByRole("button", { name: "Move Pattern 1 down" }),
    );
    expect(
      screen.getAllByRole("textbox").map((f) => (f as HTMLInputElement).value),
    ).toEqual(["b", "a"]);

    await user.click(screen.getByRole("button", { name: "Remove Pattern 1" }));
    expect(screen.getAllByRole("textbox")).toHaveLength(1);
    expect(screen.getByDisplayValue("a")).toBeVisible();
  });

  it("is read-only without member:write", async () => {
    mockUsePermissions.mockReturnValue(
      permissionState((p: string) => p === "member:read"),
    );
    await pageWith([PATTERN]);

    const field = await screen.findByDisplayValue(PATTERN);

    expect(field).toHaveAttribute("readonly");
    expect(screen.queryByRole("button", { name: "Save" })).toBeNull();
    expect(screen.queryByRole("button", { name: "Add pattern" })).toBeNull();
  });

  it("renders the no-access notice without member:read", () => {
    mockUsePermissions.mockReturnValue(permissionState(() => false));
    renderPage();

    expect(screen.getByRole("alert")).toHaveTextContent("No access to this.");
  });
});
