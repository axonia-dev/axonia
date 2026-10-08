import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { EditionSlots } from "@/edition";
import { EditionProvider } from "@/edition-context";
import { openSlots } from "@/open-slots";
import { ThemeProvider } from "@/theme/ThemeProvider";

import type { ConsentOrganization } from "./use-consent-organization";

// A token that holds no organization the auto-entry could settle: the page asks for one, through
// the edition's picker where there is one, and offers each membership to enter where there is not.

const mockReview = vi.fn();
const mockEnterOrganization = vi.fn();
const signinRedirect = vi.fn();

vi.mock("../api/use-agent-connections", () => ({
  useAuthorizationReview: () => mockReview() as never,
  useApproveConnection: () => ({ mutate: vi.fn(), reset: vi.fn() }),
  useDenyConnection: () => ({ mutate: vi.fn(), reset: vi.fn() }),
}));
vi.mock("./use-consent-organization", () => ({
  useConsentOrganization: (): ConsentOrganization => ({
    state: "choose",
    memberships: { isFetching: false, refetch: () => Promise.resolve() },
  }),
}));
vi.mock("@/auth/delegated-session", () => ({
  useIsDelegated: () => false,
}));
vi.mock("./ConsentOrganization", () => ({
  ConsentOrganization: () => <p>organization choice</p>,
}));
vi.mock("@/features/admin/api/use-my-organizations", () => ({
  useMyOrganizations: () => ({ data: [{ id: "acme", name: "Acme" }] }),
}));
vi.mock("@/auth/session", () => ({ useAuth: () => ({ signinRedirect }) }));
vi.mock("@/auth/enter-organization", () => ({
  enterOrganization: (...args: unknown[]) => {
    mockEnterOrganization(...args);
  },
}));

import { ConsentPage } from "./ConsentPage";

const withPicker: EditionSlots = {
  ...openSlots,
  OrganizationPicker: () => <p>picker</p>,
};

function renderPage(search = "?client_id=x", edition = withPicker) {
  render(
    <EditionProvider edition={edition}>
      <ThemeProvider>
        <MemoryRouter initialEntries={[`/oauth/authorize${search}`]}>
          <ConsentPage />
        </MemoryRouter>
      </ThemeProvider>
    </EditionProvider>,
  );
}

beforeEach(() => {
  mockReview.mockReturnValue({ data: undefined, isError: false });
});

describe("ConsentPage asking for an organization", () => {
  it("asks for an organization rather than calling the session refused", () => {
    renderPage();

    expect(
      screen.getByText("Choose an organization to connect."),
    ).toBeInTheDocument();
    expect(screen.getByText("organization choice")).toBeInTheDocument();
    expect(screen.queryByText(/not available/)).not.toBeInTheDocument();
    expect(mockReview).not.toHaveBeenCalled();
  });

  it("names the host the app claims while asking for an organization", () => {
    renderPage(
      "?client_id=https%3A%2F%2Fclaude.ai%2Foauth%2Fclaude-code-client-metadata",
    );

    expect(
      screen.getByRole("heading", { name: "Connect an app from claude.ai" }),
    ).toBeInTheDocument();
  });

  it("names no host while asking for an organization when the client_id is not an https URL", () => {
    renderPage("?client_id=http%3A%2F%2Fclaude.ai%2Fmetadata");

    expect(
      screen.getByRole("heading", { name: "Connect an agent" }),
    ).toBeInTheDocument();
  });

  it("offers each membership to enter, and no choice it cannot take, where the edition supplies no picker", async () => {
    const user = userEvent.setup({ delay: null });
    renderPage("?client_id=x", openSlots);

    expect(
      screen.getByRole("heading", { name: "Connect an agent" }),
    ).toBeInTheDocument();
    expect(
      screen.getByText("Enter an organization to connect."),
    ).toBeInTheDocument();
    expect(
      screen.queryByText("Choose an organization to connect."),
    ).not.toBeInTheDocument();
    expect(screen.queryByText("organization choice")).not.toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Enter Acme" }));

    expect(mockEnterOrganization).toHaveBeenCalledWith(
      { signinRedirect },
      "acme",
      "/oauth/authorize?client_id=x",
    );
    expect(mockReview).not.toHaveBeenCalled();
  });
});
