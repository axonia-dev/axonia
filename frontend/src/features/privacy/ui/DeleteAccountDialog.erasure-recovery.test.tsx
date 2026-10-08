import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type { ReactNode } from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { clearStoredActiveOrganizationId } from "@/auth/active-organization";
import { httpPrivacyGateway } from "@/features/privacy/adapters/http-privacy-gateway";
import { DeleteAccountDialog } from "@/features/privacy/ui/DeleteAccountDialog";
import { ApiError } from "@/lib/api-client";

// Split out of DeleteAccountDialog.test.tsx (300-line ceiling): the paths where
// something other than a plain permitted-or-blocked answer shows up - an unknown
// email, a failed preflight check, and the backend's own guard biting again (or
// its recovery failing) at erase time.

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

// No default for `email`: a default parameter also fires on an explicitly-passed
// `undefined`, which is exactly the value the missing-email tests need to send.
function renderOpen(email: string | undefined) {
  const onOpenChange = vi.fn();
  render(
    <DeleteAccountDialog email={email} isOpen onOpenChange={onOpenChange} />,
    { wrapper },
  );
  return onOpenChange;
}

beforeEach(() => {
  signoutRedirect.mockClear();
  vi.mocked(clearStoredActiveOrganizationId).mockClear();
  vi.mocked(httpPrivacyGateway.erasurePreflight).mockReset();
  vi.mocked(httpPrivacyGateway.eraseAccount).mockReset();
});

describe("DeleteAccountDialog erasure recovery", () => {
  it("says so explicitly when the account email is not known yet, rather than a blank confirmation phrase", () => {
    renderOpen(undefined);

    expect(
      screen.getByText(/no email address is on file/i),
    ).toBeInTheDocument();
    expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Delete" }),
    ).not.toBeInTheDocument();
  });

  it("never asks the backend whether a still-unknown account may be erased", async () => {
    renderOpen(undefined);

    await screen.findByText(/no email address is on file/i);
    expect(httpPrivacyGateway.erasurePreflight).not.toHaveBeenCalled();
  });

  it("offers a retry when the preflight check itself fails", async () => {
    const user = userEvent.setup({ delay: null });
    vi.mocked(httpPrivacyGateway.erasurePreflight)
      .mockRejectedValueOnce(new Error("network down"))
      .mockResolvedValueOnce({ permitted: true, blockers: [] });

    renderOpen(DEFAULT_EMAIL);

    const retry = await screen.findByRole("button", { name: /try again/i });
    expect(screen.queryByRole("textbox")).not.toBeInTheDocument();

    await user.click(retry);
    expect(await screen.findByRole("textbox")).toBeInTheDocument();
  });

  it("re-checks the preflight and shows the blockers when the backend still refuses at erase time", async () => {
    const user = userEvent.setup({ delay: null });
    vi.mocked(httpPrivacyGateway.erasurePreflight)
      .mockResolvedValueOnce({ permitted: true, blockers: [] })
      .mockResolvedValueOnce({
        permitted: false,
        blockers: [
          {
            kind: "SOLE_OWNER",
            organizationId: "acme",
            communityName: "Rust Practitioners",
          },
        ],
      });
    vi.mocked(httpPrivacyGateway.eraseAccount).mockRejectedValue(
      new ApiError(409, "still blocked", "ACCOUNT_ERASURE_BLOCKED"),
    );

    renderOpen(DEFAULT_EMAIL);
    const confirm = await screen.findByRole("textbox");
    await user.type(confirm, DEFAULT_EMAIL);
    await user.click(screen.getByRole("button", { name: "Delete" }));

    // The blocker view proves the re-fetch actually happened and was rendered,
    // not just that the button went back to disabled for some unrelated reason.
    expect(await screen.findByText(/Rust Practitioners/)).toBeInTheDocument();
    expect(screen.queryByRole("textbox")).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Delete" })).toBeDisabled();
    expect(signoutRedirect).not.toHaveBeenCalled();
  });

  it("acknowledges a failed attempt even when the re-checked preflight comes back permitted", async () => {
    // The rare case: someone else's action that caused the 409 is undone again
    // before the refetch lands, so the blocker view never appears - without an
    // explicit notice, a re-armed Delete button would look like the press did nothing.
    const user = userEvent.setup({ delay: null });
    vi.mocked(httpPrivacyGateway.erasurePreflight)
      .mockResolvedValueOnce({ permitted: true, blockers: [] })
      .mockResolvedValueOnce({ permitted: true, blockers: [] });
    vi.mocked(httpPrivacyGateway.eraseAccount).mockRejectedValue(
      new ApiError(409, "still blocked", "ACCOUNT_ERASURE_BLOCKED"),
    );

    renderOpen(DEFAULT_EMAIL);
    const confirm = await screen.findByRole("textbox");
    await user.type(confirm, DEFAULT_EMAIL);
    await user.click(screen.getByRole("button", { name: "Delete" }));

    expect(await screen.findByText(/did not go through/i)).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Delete" })).toBeEnabled();
  });

  it("still acknowledges a failed attempt when the re-check itself fails", async () => {
    // A narrower case of the one above: the 409's own refetch can fail outright,
    // which must not silently swallow the fact that Delete was pressed and refused.
    const user = userEvent.setup({ delay: null });
    vi.mocked(httpPrivacyGateway.erasurePreflight)
      .mockResolvedValueOnce({ permitted: true, blockers: [] })
      .mockRejectedValueOnce(new Error("network down"));
    vi.mocked(httpPrivacyGateway.eraseAccount).mockRejectedValue(
      new ApiError(409, "still blocked", "ACCOUNT_ERASURE_BLOCKED"),
    );

    renderOpen(DEFAULT_EMAIL);
    const confirm = await screen.findByRole("textbox");
    await user.type(confirm, DEFAULT_EMAIL);
    await user.click(screen.getByRole("button", { name: "Delete" }));

    expect(await screen.findByText(/did not go through/i)).toBeInTheDocument();
    expect(
      screen.getByText(/could not check whether this is possible/i),
    ).toBeInTheDocument();
  });

  it("tells the person their data is gone, signs them out, and still offers a way to close when only the identity-provider step is left", async () => {
    const user = userEvent.setup({ delay: null });
    vi.mocked(httpPrivacyGateway.erasurePreflight).mockResolvedValue({
      permitted: true,
      blockers: [],
    });
    vi.mocked(httpPrivacyGateway.eraseAccount).mockRejectedValue(
      new ApiError(502, "retry later", "ACCOUNT_ERASURE_INCOMPLETE"),
    );
    const onOpenChange = renderOpen(DEFAULT_EMAIL);

    const confirm = await screen.findByRole("textbox");
    await user.type(confirm, DEFAULT_EMAIL);
    await user.click(screen.getByRole("button", { name: "Delete" }));

    expect(
      await screen.findByText(/data has been erased/i),
    ).toBeInTheDocument();
    expect(clearStoredActiveOrganizationId).toHaveBeenCalledOnce();
    expect(signoutRedirect).toHaveBeenCalledOnce();

    // A stalled redirect must not leave the alertdialog (isDismissable={false},
    // Escape disabled) with no way out at all.
    await user.click(screen.getByRole("button", { name: "Close" }));
    expect(onOpenChange).toHaveBeenCalledWith(false);
  });

  // Defence in depth: the settings hide both privacy items from a delegated
  // session, so this refusal should not reach the dialog. If it ever does, the
  // generic "Something went wrong" would send the person hunting for a fault
  // that is not there.
  it("names the own sign-in as the reason rather than falling through to a generic failure", async () => {
    const user = userEvent.setup({ delay: null });
    vi.mocked(httpPrivacyGateway.erasurePreflight).mockResolvedValue({
      permitted: true,
      blockers: [],
    });
    vi.mocked(httpPrivacyGateway.eraseAccount).mockRejectedValue(
      new ApiError(403, "own session required", "REQUIRES_OWN_SESSION"),
    );
    renderOpen(DEFAULT_EMAIL);

    const confirm = await screen.findByRole("textbox");
    await user.type(confirm, DEFAULT_EMAIL);
    await user.click(screen.getByRole("button", { name: "Delete" }));

    expect(
      await screen.findByText(/needs the person’s own sign-in/i),
    ).toBeInTheDocument();
    expect(screen.queryByText(/Something went wrong/i)).not.toBeInTheDocument();
  });
});
