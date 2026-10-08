import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { ApiError } from "@/lib/api-client";
import { ThemeProvider } from "@/theme/ThemeProvider";

import type { ConsentOrganizationState } from "./use-consent-organization";

const mockReview = vi.fn();
const mockOrganizationState = vi.fn<() => ConsentOrganizationState>();
const mockDelegated = vi.fn<() => boolean>();
const mockMe = vi.fn();
const mockOrganizations = vi.fn();
const mockApprove = vi.fn();
const mockApproveState = vi.fn();

vi.mock("../api/use-agent-connections", () => ({
  useAuthorizationReview: () => mockReview() as never,
  useApproveConnection: () => ({
    mutate: mockApprove,
    reset: vi.fn(),
    ...(mockApproveState() as object),
  }),
  useDenyConnection: () => ({ mutate: vi.fn(), reset: vi.fn() }),
}));
vi.mock("@/auth/token", () => ({ getAccessToken: () => "token" }));
vi.mock("@/auth/active-organization", () => ({
  activeOrganizationId: () => "acme",
}));
vi.mock("./use-consent-organization", () => ({
  useConsentOrganization: () => ({
    state: mockOrganizationState(),
    memberships: mockOrganizations() as unknown,
  }),
}));
vi.mock("@/auth/delegated-session", () => ({
  useIsDelegated: () => mockDelegated(),
}));
vi.mock("./ConsentOrganization", () => ({
  ConsentOrganization: () => <p>organization choice</p>,
}));
vi.mock("./leave-for", () => ({ leaveFor: vi.fn() }));
vi.mock("@/features/identity/api/use-me", () => ({
  useMe: () => mockMe() as never,
}));
vi.mock("@/features/admin/api/use-my-organizations", () => ({
  useMyOrganizations: () => mockOrganizations() as never,
}));

import { ConsentPage } from "./ConsentPage";

function renderPage(search = "?client_id=x") {
  render(
    <ThemeProvider>
      <MemoryRouter initialEntries={[`/oauth/authorize${search}`]}>
        <ConsentPage />
      </MemoryRouter>
    </ThemeProvider>,
  );
}

beforeEach(() => {
  vi.clearAllMocks();
  mockDelegated.mockReturnValue(false);
  mockOrganizationState.mockReturnValue("held");
  mockReview.mockReturnValue({ data: undefined, isError: false });
  mockMe.mockReturnValue({
    data: { id: "u-1", displayName: "Ada Lovelace" },
    isError: false,
  });
  mockOrganizations.mockReturnValue({
    data: [{ id: "acme", name: "Acme" }],
    isError: false,
  });
  mockApproveState.mockReturnValue({ isPending: false, isError: false });
});

describe("ConsentPage before the review", () => {
  it("announces that the request is loading", () => {
    renderPage();

    expect(screen.getByRole("status")).toHaveTextContent("Loading…");
  });

  it("waits for the organization to be entered before asking the backend", () => {
    mockOrganizationState.mockReturnValue("entering");

    renderPage();

    expect(screen.getByRole("status")).toHaveTextContent("Loading…");
    expect(mockReview).not.toHaveBeenCalled();
  });

  it("says agents need an organization when the member belongs to none", () => {
    mockOrganizationState.mockReturnValue("none");

    renderPage();

    expect(
      screen.getByRole("heading", { name: "No organization" }),
    ).toBeInTheDocument();
    expect(
      screen.getByText("Agents need an organization."),
    ).toBeInTheDocument();
    expect(mockReview).not.toHaveBeenCalled();
  });

  it("says when the organizations could not be read, and asks again when told to", async () => {
    const refetch = vi.fn();
    mockOrganizationState.mockReturnValue("failed");
    mockOrganizations.mockReturnValue({
      data: undefined,
      errorUpdateCount: 1,
      isFetching: false,
      refetch,
    });
    const user = userEvent.setup({ delay: null });

    renderPage();
    await user.click(screen.getByRole("button", { name: "Try again" }));

    expect(screen.getByRole("alert")).toHaveTextContent(
      "Could not load this request.",
    );
    expect(refetch).toHaveBeenCalledOnce();
  });

  it("says when the request could not be read, and asks again when told to", async () => {
    const refetch = vi.fn();
    mockReview.mockReturnValue({
      data: undefined,
      errorUpdateCount: 1,
      isFetching: false,
      refetch,
    });
    const user = userEvent.setup({ delay: null });

    renderPage();
    await user.click(screen.getByRole("button", { name: "Try again" }));

    expect(screen.getByRole("alert")).toHaveTextContent(
      "Could not load this request.",
    );
    expect(refetch).toHaveBeenCalledOnce();
  });

  it("refuses a delegated tab without asking the backend", () => {
    mockDelegated.mockReturnValue(true);

    renderPage();

    expect(
      screen.getByRole("heading", { name: "Connection refused" }),
    ).toBeInTheDocument();
    expect(
      screen.getByText("Needs the person’s own sign-in."),
    ).toBeInTheDocument();
    expect(mockReview).not.toHaveBeenCalled();
    expect(mockOrganizationState).not.toHaveBeenCalled();
  });
});

// The card names the person and the organization: rendered before both are known, those rows
// fill in late and push the answer buttons down under a pointer already on its way to them.
describe("ConsentPage before the card", () => {
  beforeEach(() => {
    mockReview.mockReturnValue({
      data: {
        outcome: "READY",
        clientName: "Claude Code",
        clientHost: "claude.ai",
        requestedScopes: ["read"],
      },
      isError: false,
    });
  });

  it("offers the answer once the person and the organizations are known", () => {
    renderPage();

    expect(screen.getByRole("button", { name: "Allow" })).toBeInTheDocument();
    expect(screen.getByText("Ada Lovelace")).toBeInTheDocument();
  });

  it("waits for the person before offering an answer", () => {
    mockMe.mockReturnValue({ data: undefined, isError: false });

    renderPage();

    expect(screen.getByRole("status")).toHaveTextContent("Loading…");
    expect(
      screen.queryByRole("button", { name: "Allow" }),
    ).not.toBeInTheDocument();
  });

  it("waits for the organizations before offering an answer", () => {
    mockOrganizations.mockReturnValue({ data: undefined, isError: false });

    renderPage();

    expect(screen.getByRole("status")).toHaveTextContent("Loading…");
    expect(
      screen.queryByRole("button", { name: "Allow" }),
    ).not.toBeInTheDocument();
  });

  it("says the request could not be loaded when the person could not be read, asking only for the person again", async () => {
    const refetchMe = vi.fn();
    const refetchOrganizations = vi.fn();
    mockMe.mockReturnValue({
      data: undefined,
      errorUpdateCount: 1,
      isFetching: false,
      refetch: refetchMe,
    });
    mockOrganizations.mockReturnValue({
      data: [{ id: "acme", name: "Acme" }],
      errorUpdateCount: 0,
      refetch: refetchOrganizations,
    });
    const user = userEvent.setup({ delay: null });

    renderPage();
    await user.click(screen.getByRole("button", { name: "Try again" }));

    expect(screen.getByRole("alert")).toHaveTextContent(
      "Could not load this request.",
    );
    expect(refetchMe).toHaveBeenCalledOnce();
    expect(refetchOrganizations).not.toHaveBeenCalled();
    expect(
      screen.queryByRole("button", { name: "Allow" }),
    ).not.toBeInTheDocument();
  });

  it("approves for the organization the card shows", async () => {
    const user = userEvent.setup({ delay: null });
    renderPage();

    await user.click(screen.getByRole("button", { name: "Allow" }));

    expect(mockApprove.mock.calls[0]?.[0]).toMatchObject({
      organizationId: "acme",
    });
  });

  it("says the organization changed when the server refuses a stale one", () => {
    mockApproveState.mockReturnValue({
      isPending: false,
      isError: true,
      error: new ApiError(409, "Conflict"),
    });

    renderPage();

    expect(screen.getByRole("alert")).toHaveTextContent(
      "The organization changed. Reload to review again.",
    );
  });

  it("asks to wait when answers come too fast", () => {
    mockApproveState.mockReturnValue({
      isPending: false,
      isError: true,
      error: new ApiError(429, "Too Many Requests"),
    });

    renderPage();

    expect(screen.getByRole("alert")).toHaveTextContent(
      "Too many attempts in a short time. Wait a minute, then try again.",
    );
  });

  it("says the request could not be loaded when the organizations could not be read", () => {
    mockOrganizations.mockReturnValue({
      data: undefined,
      errorUpdateCount: 1,
      isFetching: false,
      refetch: vi.fn(),
    });

    renderPage();

    expect(screen.getByRole("alert")).toHaveTextContent(
      "Could not load this request.",
    );
  });
});
