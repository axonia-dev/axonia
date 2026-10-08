import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { clearStoredActiveOrganizationId } from "@/auth/active-organization";
import { httpPrivacyGateway } from "@/features/privacy/adapters/http-privacy-gateway";
import type { ErasurePreflight } from "@/features/privacy/domain/erasure";
import { DeleteAccountDialog } from "@/features/privacy/ui/DeleteAccountDialog";

// The preflight-error/409/502/missing-email recovery paths live in
// DeleteAccountDialog.erasure-recovery.test.tsx (300-line ceiling); this file
// covers the ordinary shape of the dialog: blocked/permitted rendering, the
// typed confirmation, Cancel, success, and an unrelated failure.

const signoutRedirect = vi.fn();

vi.mock("@/auth/session", () => ({ useAuth: () => ({ signoutRedirect }) }));

vi.mock("@/auth/active-organization", async (importActual) => {
  const actual =
    await importActual<typeof import("@/auth/active-organization")>();
  return { ...actual, clearStoredActiveOrganizationId: vi.fn() };
});

vi.mock("@/features/privacy/adapters/http-privacy-gateway", () => ({
  httpPrivacyGateway: { erasurePreflight: vi.fn(), eraseAccount: vi.fn() },
}));

const DEFAULT_EMAIL = "member@example.test";

function wrapper({ children }: { children: ReactNode }) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false } },
  });
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}

function renderDialog(
  preflight: ErasurePreflight,
  overrides: { email?: string; onOpenChange?: (isOpen: boolean) => void } = {},
) {
  vi.mocked(httpPrivacyGateway.erasurePreflight).mockResolvedValue(preflight);
  const onOpenChange = overrides.onOpenChange ?? vi.fn();
  render(
    <DeleteAccountDialog
      email={overrides.email ?? DEFAULT_EMAIL}
      isOpen
      onOpenChange={onOpenChange}
    />,
    { wrapper },
  );
  return onOpenChange;
}

let user: ReturnType<typeof userEvent.setup>;

beforeEach(() => {
  user = userEvent.setup({ delay: null });
  signoutRedirect.mockClear();
  vi.mocked(clearStoredActiveOrganizationId).mockClear();
  vi.mocked(httpPrivacyGateway.erasurePreflight).mockReset();
  vi.mocked(httpPrivacyGateway.eraseAccount).mockReset();
});

describe("DeleteAccountDialog", () => {
  it("names each community the person must delete first and offers no way through", async () => {
    renderDialog({
      permitted: false,
      blockers: [
        {
          kind: "SOLE_OWNER",
          organizationId: "acme",
          communityName: "Rust Practitioners",
        },
      ],
    });

    expect(await screen.findByText(/Rust Practitioners/)).toBeInTheDocument();
    expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Delete" })).toBeDisabled();
  });

  it("explains that an enterprise account is not the member's to delete", async () => {
    renderDialog({
      permitted: false,
      blockers: [
        {
          kind: "ENTERPRISE_MEMBERSHIP",
          organizationId: "bigcorp",
          communityName: null,
        },
      ],
    });

    expect(
      await screen.findByText(/belongs to an organization/i),
    ).toBeInTheDocument();
  });

  it("enables the delete only once the email is typed exactly", async () => {
    renderDialog(
      { permitted: true, blockers: [] },
      { email: "nora@example.test" },
    );

    const confirm = await screen.findByRole("textbox");
    expect(screen.getByRole("button", { name: "Delete" })).toBeDisabled();

    await user.type(confirm, "nora@example.test");
    expect(screen.getByRole("button", { name: "Delete" })).toBeEnabled();
    // The "did not go through" notice is for an actual failed attempt, not a
    // permanent fixture of the permitted view.
    expect(screen.queryByText(/did not go through/i)).not.toBeInTheDocument();
  });

  it("does not arm on an empty confirmation even if the email is somehow blank", async () => {
    // Guards against `confirmation === email` trivially matching two empty
    // strings before the person has typed anything.
    renderDialog({ permitted: true, blockers: [] }, { email: "" });

    await screen.findByRole("textbox");
    expect(screen.getByRole("button", { name: "Delete" })).toBeDisabled();
  });

  it("hides the irreversibility warning once the person is known to be blocked", async () => {
    renderDialog({
      permitted: false,
      blockers: [
        { kind: "SOLE_OWNER", organizationId: "acme", communityName: "Rust" },
      ],
    });

    await screen.findByText(/Rust/);
    expect(screen.queryByText(/cannot be undone/i)).not.toBeInTheDocument();
  });

  it("asks to close when Cancel is pressed", async () => {
    const onOpenChange = renderDialog({ permitted: true, blockers: [] });

    await user.click(await screen.findByRole("button", { name: "Cancel" }));
    expect(onOpenChange).toHaveBeenCalledWith(false);
  });

  it("erases the account, clears the stored org and signs out on success", async () => {
    vi.mocked(httpPrivacyGateway.eraseAccount).mockResolvedValue(undefined);
    const onOpenChange = renderDialog({ permitted: true, blockers: [] });

    const confirm = await screen.findByRole("textbox");
    await user.type(confirm, DEFAULT_EMAIL);
    await user.click(screen.getByRole("button", { name: "Delete" }));

    await waitFor(() => {
      expect(onOpenChange).toHaveBeenCalledWith(false);
    });
    expect(clearStoredActiveOrganizationId).toHaveBeenCalledOnce();
    expect(signoutRedirect).toHaveBeenCalledOnce();
  });

  it("surfaces a generic failure without signing out or closing", async () => {
    vi.mocked(httpPrivacyGateway.eraseAccount).mockRejectedValue(
      new Error("boom"),
    );
    const onOpenChange = renderDialog({ permitted: true, blockers: [] });

    const confirm = await screen.findByRole("textbox");
    await user.type(confirm, DEFAULT_EMAIL);
    await user.click(screen.getByRole("button", { name: "Delete" }));

    expect(await screen.findByRole("alert")).toBeInTheDocument();
    expect(signoutRedirect).not.toHaveBeenCalled();
    expect(onOpenChange).not.toHaveBeenCalled();
  });
});
