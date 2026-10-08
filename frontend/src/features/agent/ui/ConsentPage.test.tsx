import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { ThemeProvider } from "@/theme/ThemeProvider";

const mockReview = vi.fn();
const mockApprove = vi.fn();
const mockApproveState = vi.fn();
const mockApproveReset = vi.fn();
const mockDeny = vi.fn();
const mockDenyState = vi.fn();
const mockLeaveFor = vi.fn();

vi.mock("../api/use-agent-connections", () => ({
  useAuthorizationReview: () => mockReview() as never,
  useApproveConnection: () => ({
    mutate: mockApprove,
    reset: mockApproveReset,
    ...(mockApproveState() as object),
  }),
  useDenyConnection: () => ({
    mutate: mockDeny,
    reset: vi.fn(),
    ...(mockDenyState() as object),
  }),
}));
vi.mock("./leave-for", () => ({
  leaveFor: (url: string) => {
    mockLeaveFor(url);
  },
}));
vi.mock("./use-consent-organization", () => ({
  useConsentOrganization: () => ({ state: "held" }),
}));
vi.mock("./ConsentOrganization", () => ({
  ConsentOrganization: () => <p>Acme</p>,
}));
vi.mock("@/features/identity/api/use-me", () => ({
  useMe: () => ({ data: { id: "u-1", displayName: "Ada Lovelace" } }),
}));
vi.mock("@/features/admin/api/use-my-organizations", () => ({
  useMyOrganizations: () => ({ data: [{ id: "acme", name: "Acme" }] }),
}));

import { ConsentPage } from "./ConsentPage";

const QUERY =
  "?response_type=code&client_id=https%3A%2F%2Fclaude.ai%2Foauth%2Fx&redirect_uri=http%3A%2F%2Flocalhost%3A54321%2Fcallback" +
  "&code_challenge=E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM&code_challenge_method=S256&scope=read+topic%3Apropose&state=s-1";

function renderAt(search = QUERY) {
  render(
    <ThemeProvider>
      <MemoryRouter initialEntries={[`/oauth/authorize${search}`]}>
        <Routes>
          <Route path="/oauth/authorize" element={<ConsentPage />} />
        </Routes>
      </MemoryRouter>
    </ThemeProvider>,
  );
}

const READY = {
  data: {
    outcome: "READY",
    clientName: "Claude Code",
    clientHost: "claude.ai",
    requestedScopes: ["read", "topic:propose"],
  },
  isLoading: false,
  isError: false,
};

beforeEach(() => {
  vi.clearAllMocks();
  mockReview.mockReturnValue(READY);
  mockApproveState.mockReturnValue({ isPending: false, isError: false });
  mockDenyState.mockReturnValue({ isPending: false, isError: false });
});

describe("ConsentPage", () => {
  it("names the app, its host, the person, the organization and where it returns", () => {
    renderAt();

    expect(
      screen.getByRole("heading", { name: "Connect Claude Code" }),
    ).toBeInTheDocument();
    expect(screen.getByText("claude.ai")).toBeInTheDocument();
    expect(screen.getByText("Ada Lovelace")).toBeInTheDocument();
    expect(screen.getByText("Acme")).toBeInTheDocument();
    expect(screen.getByText("localhost")).toBeInTheDocument();
  });

  it("starts with the requested permissions switched on", () => {
    renderAt();

    expect(
      screen.getByRole("switch", { name: /read schedule/i }),
    ).toBeChecked();
    expect(
      screen.getByRole("switch", { name: /propose topics/i }),
    ).toBeChecked();
    expect(
      screen.getByRole("switch", { name: /raise action items/i }),
    ).not.toBeChecked();
  });

  function approveRedirects() {
    mockApprove.mockImplementation(
      (_vars, options: { onSuccess: (o: unknown) => void }) =>
        options.onSuccess({
          outcome: "REDIRECT",
          redirectUrl: "http://localhost:54321/callback?code=c",
        }),
    );
  }

  it("approves with the chosen permissions and leaves for the client", async () => {
    const user = userEvent.setup({ delay: null });
    approveRedirects();
    renderAt();

    await user.click(screen.getByRole("switch", { name: /propose topics/i }));
    await user.click(screen.getByRole("button", { name: "Allow" }));

    expect(mockApprove.mock.calls[0]?.[0]).toMatchObject({
      request: { client_id: "https://claude.ai/oauth/x", state: "s-1" },
      scopes: ["read"],
    });
    expect(mockLeaveFor).toHaveBeenCalledWith(
      "http://localhost:54321/callback?code=c",
    );
  });

  it("says where it returns and offers no second answer while leaving", async () => {
    const user = userEvent.setup({ delay: null });
    approveRedirects();
    renderAt();

    await user.click(screen.getByRole("button", { name: "Allow" }));

    expect(screen.getByRole("status")).toHaveTextContent(
      "Returning to localhost…",
    );
    expect(
      screen.queryByRole("button", { name: /allow|deny/i }),
    ).not.toBeInTheDocument();
  });

  it("says it is returning without a host when the request names none", async () => {
    const user = userEvent.setup({ delay: null });
    approveRedirects();
    renderAt("?client_id=x");

    await user.click(screen.getByRole("button", { name: "Allow" }));

    expect(screen.getByRole("status")).toHaveTextContent(/^Returning…$/);
  });

  it("cannot allow with nothing selected", async () => {
    const user = userEvent.setup({ delay: null });
    renderAt();

    await user.click(screen.getByRole("switch", { name: /read schedule/i }));
    await user.click(screen.getByRole("switch", { name: /propose topics/i }));

    expect(screen.getByRole("button", { name: "Allow" })).toBeDisabled();
  });

  it("denies and leaves for the client", async () => {
    const user = userEvent.setup({ delay: null });
    mockDeny.mockImplementation(
      (_request, options: { onSuccess: (o: unknown) => void }) =>
        options.onSuccess({
          outcome: "REDIRECT",
          redirectUrl: "http://localhost:54321/callback?error=access_denied",
        }),
    );
    renderAt();

    await user.click(screen.getByRole("button", { name: "Deny" }));

    expect(mockLeaveFor).toHaveBeenCalledWith(
      "http://localhost:54321/callback?error=access_denied",
    );
  });

  it("shows a refusal and never leaves", () => {
    mockReview.mockReturnValue({
      data: { outcome: "REFUSED", reason: "REDIRECT_MISMATCH" },
      isLoading: false,
      isError: false,
    });

    renderAt();

    expect(
      screen.getByRole("heading", { name: "Connection refused" }),
    ).toBeInTheDocument();
    expect(
      screen.getByText(
        "This request would return to an address the app never registered.",
      ),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Allow" }),
    ).not.toBeInTheDocument();
    expect(mockLeaveFor).not.toHaveBeenCalled();
  });

  it("offers the organization choice when agents are unavailable here", () => {
    mockReview.mockReturnValue({
      data: { outcome: "REFUSED", reason: "NO_AGENT_ACCESS" },
      isLoading: false,
      isError: false,
    });

    renderAt();

    expect(screen.getByText("Acme")).toBeInTheDocument();
  });

  it("follows an error redirect the backend answers straight away", () => {
    mockReview.mockReturnValue({
      data: {
        outcome: "REDIRECT",
        redirectUrl: "http://localhost:54321/callback?error=invalid_request",
      },
      isLoading: false,
      isError: false,
    });

    renderAt();

    expect(mockLeaveFor).toHaveBeenCalledWith(
      "http://localhost:54321/callback?error=invalid_request",
    );
  });

  it("says when allowing failed", () => {
    mockApproveState.mockReturnValue({ isPending: false, isError: true });

    renderAt();

    expect(screen.getByRole("alert")).toHaveTextContent(
      "Could not connect. Try again.",
    );
  });

  it("says when denying failed, without calling it a failed connection", () => {
    mockDenyState.mockReturnValue({ isPending: false, isError: true });

    renderAt();

    expect(screen.getByRole("alert")).toHaveTextContent(
      "Could not send the answer. Try again.",
    );
  });

  it("drops a failed approval's message once the member denies instead", async () => {
    const user = userEvent.setup({ delay: null });
    renderAt();

    await user.click(screen.getByRole("button", { name: "Deny" }));

    expect(mockApproveReset).toHaveBeenCalled();
  });

  it("shows the refusal an approval answers instead of leaving", async () => {
    const user = userEvent.setup({ delay: null });
    mockApprove.mockImplementation(
      (_vars, options: { onSuccess: (o: unknown) => void }) =>
        options.onSuccess({ outcome: "REFUSED", reason: "NO_AGENT_ACCESS" }),
    );
    renderAt();

    await user.click(screen.getByRole("button", { name: "Allow" }));

    expect(
      screen.getByRole("heading", { name: "Connection refused" }),
    ).toBeInTheDocument();
    expect(mockLeaveFor).not.toHaveBeenCalled();
  });
});
