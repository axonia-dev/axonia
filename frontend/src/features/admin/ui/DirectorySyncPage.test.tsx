import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type {
  GenerateTokenBody,
  GenerateTokenResponse,
  ScimToken,
} from "@/features/admin/domain/scim-token";
import { DirectorySyncPage } from "@/features/admin/ui/DirectorySyncPage";
import { permissionState } from "@/test/permissions";

type GenerateFn = (
  body: GenerateTokenBody,
  options: { onSuccess: (res: GenerateTokenResponse) => void },
) => void;

const SHARED_LABEL = "Entra provisioning";
const NEWER_CREATED_AT = "2026-07-19T00:00:00Z";
const OLDER_CREATED_AT = "2026-02-03T00:00:00Z";

const localDate = (iso: string) => new Date(iso).toLocaleDateString();
const revokeName = (iso: string) =>
  `Revoke ${SHARED_LABEL} created ${localDate(iso)}`;

function makeToken(overrides: Partial<ScimToken>): ScimToken {
  return {
    id: 1,
    label: SHARED_LABEL,
    lastUsedAt: null,
    expiresAt: null,
    createdAt: NEWER_CREATED_AT,
    status: "ACTIVE",
    ...overrides,
  };
}

/** Drives the themed DatePicker's segments: click each explicitly and type a zero-padded
 *  value, since a single-digit day would not auto-advance and would swallow later digits. */
async function pickExpiryDate(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByRole("spinbutton", { name: /month/i }));
  await user.keyboard("08");
  await user.click(screen.getByRole("spinbutton", { name: /day/i }));
  await user.keyboard("01");
  await user.click(screen.getByRole("spinbutton", { name: /year/i }));
  await user.keyboard("2027");
}

// The SCIM base URL is the API's own address.
vi.mock("@/lib/env", () => ({
  apiBaseUrl: () => "http://localhost:8080",
}));

const mockUsePermissions = vi.fn();
const mockUseScimTokens = vi.fn();
const mockGenerateMutate = vi.fn<GenerateFn>();
const mockRevokeMutate = vi.fn();

vi.mock("@/features/admin/api/use-permissions", () => ({
  usePermissions: () => mockUsePermissions() as never,
}));

vi.mock("@/features/admin/api/use-scim-token", () => ({
  useScimTokens: () => mockUseScimTokens() as never,
  useGenerateScimToken: () => ({
    mutate: mockGenerateMutate,
    isPending: false,
    isError: false,
  }),
  useRevokeScimToken: () => ({
    mutate: mockRevokeMutate,
    isPending: false,
  }),
}));

beforeEach(() => {
  vi.clearAllMocks();
  mockUsePermissions.mockReturnValue(permissionState(() => true));
  mockUseScimTokens.mockReturnValue({ data: [], isPending: false });
});

describe("DirectorySyncPage", () => {
  it("renders the Directory sync heading", () => {
    render(<DirectorySyncPage />);
    expect(
      screen.getByRole("heading", { name: "Directory sync" }),
    ).toBeInTheDocument();
  });

  it("shows the SCIM base URL beside a control that copies it", () => {
    render(<DirectorySyncPage />);
    expect(
      screen.getByText("http://localhost:8080/scim/v2"),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Copy SCIM base URL" }),
    ).toBeInTheDocument();
  });

  it("renders the no-access notice when scim:read is missing", () => {
    mockUsePermissions.mockReturnValue(permissionState(() => false));
    render(<DirectorySyncPage />);
    expect(screen.getByRole("alert")).toHaveTextContent("No access to this.");
  });

  it("hides New token button when caller only has scim:read", () => {
    mockUsePermissions.mockReturnValue(
      permissionState((p: string) => p === "scim:read"),
    );
    render(<DirectorySyncPage />);
    expect(
      screen.queryByRole("button", { name: "New token" }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Rotate token" }),
    ).not.toBeInTheDocument();
  });

  it("shows New token button when caller has scim:write", () => {
    mockUsePermissions.mockReturnValue(
      permissionState((p: string) => p === "scim:write" || p === "scim:read"),
    );
    render(<DirectorySyncPage />);
    expect(
      screen.getByRole("button", { name: "New token" }),
    ).toBeInTheDocument();
  });

  it("hides the disclosure button while the tokens query is pending", () => {
    mockUseScimTokens.mockReturnValue({ data: undefined, isPending: true });
    render(<DirectorySyncPage />);
    expect(
      screen.queryByRole("button", { name: "New token" }),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Rotate token" }),
    ).not.toBeInTheDocument();
  });

  it("shows Rotate token button when a token already exists", () => {
    mockUseScimTokens.mockReturnValue({
      data: [
        {
          id: 7,
          label: "Entra provisioning",
          lastUsedAt: null,
          expiresAt: null,
          createdAt: "2026-01-01T00:00:00Z",
          status: "ACTIVE",
        },
      ],
      isPending: false,
    });
    render(<DirectorySyncPage />);
    expect(
      screen.getByRole("button", { name: "Rotate token" }),
    ).toBeInTheDocument();
  });

  it("revokes by token id and renders same-labelled tokens distinctly", async () => {
    const user = userEvent.setup({ delay: null });
    mockUseScimTokens.mockReturnValue({
      data: [
        makeToken({ id: 7, createdAt: NEWER_CREATED_AT }),
        makeToken({ id: 9, createdAt: OLDER_CREATED_AT }),
      ],
      isPending: false,
    });

    render(<DirectorySyncPage />);

    expect(screen.getAllByText(/^Active · created/)).toHaveLength(2);
    expect(screen.getAllByRole("button", { name: /^Revoke/ })).toHaveLength(2);

    await user.click(
      screen.getByRole("button", { name: revokeName(OLDER_CREATED_AT) }),
    );
    expect(mockRevokeMutate).toHaveBeenCalledWith(9);
  });

  it("labels an ACTIVE token Active", () => {
    mockUseScimTokens.mockReturnValue({
      data: [makeToken({ id: 7, status: "ACTIVE" })],
      isPending: false,
    });
    render(<DirectorySyncPage />);
    expect(
      screen.getByText(`Active · created ${localDate(NEWER_CREATED_AT)}`),
    ).toBeInTheDocument();
    expect(screen.queryByText(/^Revoked · created/)).not.toBeInTheDocument();
    expect(screen.queryByText(/^Expired · created/)).not.toBeInTheDocument();
  });

  it("labels a REVOKED token Revoked and offers no Revoke button", () => {
    mockUseScimTokens.mockReturnValue({
      data: [makeToken({ id: 9, status: "REVOKED" })],
      isPending: false,
    });
    render(<DirectorySyncPage />);
    expect(
      screen.getByText(`Revoked · created ${localDate(NEWER_CREATED_AT)}`),
    ).toBeInTheDocument();
    expect(screen.queryByText(/^Active · created/)).not.toBeInTheDocument();
    expect(screen.queryByText(/^Expired · created/)).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /^Revoke/ }),
    ).not.toBeInTheDocument();
  });

  it("labels an EXPIRED token Expired and offers no Revoke button", () => {
    mockUseScimTokens.mockReturnValue({
      data: [makeToken({ id: 11, status: "EXPIRED" })],
      isPending: false,
    });
    render(<DirectorySyncPage />);
    expect(
      screen.getByText(`Expired · created ${localDate(NEWER_CREATED_AT)}`),
    ).toBeInTheDocument();
    expect(screen.queryByText(/^Active · created/)).not.toBeInTheDocument();
    expect(screen.queryByText(/^Revoked · created/)).not.toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /^Revoke/ }),
    ).not.toBeInTheDocument();
  });

  it("shows the generate form after clicking New token", async () => {
    const user = userEvent.setup({ delay: null });
    render(<DirectorySyncPage />);
    await user.click(screen.getByRole("button", { name: "New token" }));
    expect(
      screen.getByRole("button", { name: "Generate token" }),
    ).toBeInTheDocument();
    expect(screen.getByLabelText("Label")).toBeInTheDocument();
    expect(screen.getByText("Expires on")).toBeInTheDocument();
  });

  it("calls generate mutation with the label and the expiry", async () => {
    const user = userEvent.setup({ delay: null });
    render(<DirectorySyncPage />);

    await user.click(screen.getByRole("button", { name: "New token" }));
    await user.type(screen.getByLabelText("Label"), "My token");
    await pickExpiryDate(user);
    await user.click(screen.getByRole("button", { name: "Generate token" }));

    const [callBody, callOptions] = mockGenerateMutate.mock.calls[0]!;
    expect(callBody).toEqual({
      label: "My token",
      expiresAt: "2027-08-01T23:59:59Z",
    });
    expect(callOptions.onSuccess).toBeDefined();
  });

  it("refuses to generate a token with no expiry, naming the field, and never calls the mutation", async () => {
    const user = userEvent.setup({ delay: null });
    render(<DirectorySyncPage />);

    await user.click(screen.getByRole("button", { name: "New token" }));
    await user.type(screen.getByLabelText("Label"), "My token");
    await user.click(screen.getByRole("button", { name: "Generate token" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Expiry date is required",
    );
    expect(mockGenerateMutate).not.toHaveBeenCalled();
  });

  it("shows the plaintext token in the show-once panel after generate", async () => {
    const user = userEvent.setup({ delay: null });
    mockGenerateMutate.mockImplementation((_body, options) => {
      options.onSuccess({ token: "super-secret-abc" });
    });

    render(<DirectorySyncPage />);
    await user.click(screen.getByRole("button", { name: "New token" }));
    await user.type(screen.getByLabelText("Label"), "Test");
    await pickExpiryDate(user);
    await user.click(screen.getByRole("button", { name: "Generate token" }));

    await waitFor(() => {
      expect(screen.getByText("super-secret-abc")).toBeInTheDocument();
    });
    expect(screen.getByRole("note")).toHaveTextContent("shown only once");
  });

  it("does not show the secret when the component re-renders without the mutation result", () => {
    // The secret lives only in component state from the mutation result.
    // A fresh render (simulating a page reload / re-mount) never shows it.
    render(<DirectorySyncPage />);
    expect(
      screen.queryByRole("region", { name: /scim token/i }),
    ).not.toBeInTheDocument();
  });
});
