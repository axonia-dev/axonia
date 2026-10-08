import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { MemoryRouter } from "react-router-dom";
import { beforeEach, describe, expect, it, vi } from "vitest";

const ACTIVE_TOKEN = {
  id: 1,
  name: "cli",
  scopes: ["read", "topic:propose"],
  createdAt: "2026-07-07T10:00:00Z",
  lastUsedAt: null,
  expiresAt: null,
  status: "ACTIVE",
};

const mockUseMyTokens = vi.fn();
const mockUseRevokeToken = vi.fn();
const mockCreateMutate = vi.fn();
const mockRevokeMutate = vi.fn();
const mockRevokeReset = vi.fn();
const mockUpdateMutate = vi.fn();

vi.mock("../api/use-personal-access-tokens", () => ({
  useMyTokens: () => mockUseMyTokens() as never,
  useCreateToken: () => ({ mutate: mockCreateMutate, isPending: false }),
  useRevokeToken: () => mockUseRevokeToken() as never,
  useUpdateToken: () => ({ mutate: mockUpdateMutate, isPending: false }),
}));

vi.mock("./ConnectedApps", () => ({
  ConnectedApps: () => <p>connected-apps</p>,
}));

import { ApiKeysView } from "./ApiKeysView";

beforeEach(() => {
  vi.clearAllMocks();
  mockUseMyTokens.mockReturnValue({ data: [ACTIVE_TOKEN], isLoading: false });
  mockUseRevokeToken.mockReturnValue({
    mutate: mockRevokeMutate,
    reset: mockRevokeReset,
    isPending: false,
    isError: false,
  });
});

describe("ApiKeysView", () => {
  it("lists tokens with their status", () => {
    render(
      <MemoryRouter>
        <ApiKeysView />
      </MemoryRouter>,
    );
    expect(
      screen.getByRole("heading", { name: /api keys/i }),
    ).toBeInTheDocument();
    expect(screen.getByText("cli")).toBeInTheDocument();
    expect(screen.getByText("Active")).toBeInTheDocument();
  });

  it("shows connected apps above the key list", () => {
    render(
      <MemoryRouter>
        <ApiKeysView />
      </MemoryRouter>,
    );

    const connectedApps = screen.getByText("connected-apps");
    const keyRow = screen.getByText("cli");
    expect(
      connectedApps.compareDocumentPosition(keyRow) &
        Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
  });

  it("states the endpoint an MCP client can actually be pointed at", () => {
    render(
      <MemoryRouter>
        <ApiKeysView />
      </MemoryRouter>,
    );
    expect(screen.getByText("http://localhost:3000/mcp")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Copy the MCP endpoint" }),
    ).toBeInTheDocument();
  });

  it("renders no subtitle in the page header and no narration under the copy field", () => {
    render(
      <MemoryRouter>
        <ApiKeysView />
      </MemoryRouter>,
    );

    const heading = screen.getByRole("heading", { name: /api keys/i });
    expect(heading.parentElement?.querySelector("p")).toBeNull();

    const link = screen.getByRole("link", { name: /read the connect guide/i });
    expect(link.parentElement?.querySelectorAll("p")).toHaveLength(0);
  });

  it("shows an empty state that does not address the reader", () => {
    mockUseMyTokens.mockReturnValue({ data: [], isLoading: false });
    render(
      <MemoryRouter>
        <ApiKeysView />
      </MemoryRouter>,
    );
    expect(screen.getByText("No keys yet")).toBeInTheDocument();
    expect(
      screen.getByText(
        "A key lets an AI agent act on behalf of this account, limited to the permissions it is granted.",
      ),
    ).toBeInTheDocument();
  });

  it("revokes a token after confirming", async () => {
    const user = userEvent.setup({ delay: null });
    render(
      <MemoryRouter>
        <ApiKeysView />
      </MemoryRouter>,
    );
    await user.click(screen.getByRole("button", { name: /revoke/i }));
    const dialog = await screen.findByRole("alertdialog");
    await user.click(
      within(dialog).getByRole("button", { name: /revoke|delete/i }),
    );
    expect(mockRevokeMutate).toHaveBeenCalledWith(1, expect.anything());
  });

  it("keeps the revoke dialog open and shows the error when the revoke fails", async () => {
    mockUseRevokeToken.mockReturnValue({
      mutate: mockRevokeMutate,
      reset: mockRevokeReset,
      isPending: false,
      isError: true,
    });
    const user = userEvent.setup({ delay: null });
    render(
      <MemoryRouter>
        <ApiKeysView />
      </MemoryRouter>,
    );
    await user.click(screen.getByRole("button", { name: /revoke/i }));
    const dialog = await screen.findByRole("alertdialog");
    await user.click(
      within(dialog).getByRole("button", { name: /revoke|delete/i }),
    );

    expect(mockRevokeMutate).toHaveBeenCalledWith(1, expect.anything());
    expect(screen.getByRole("alertdialog")).toBeInTheDocument();
    expect(screen.getByText(/could not revoke the key/i)).toBeInTheDocument();
  });

  it("clears the revoke dialog when cancelled", async () => {
    const user = userEvent.setup({ delay: null });
    render(
      <MemoryRouter>
        <ApiKeysView />
      </MemoryRouter>,
    );
    await user.click(screen.getByRole("button", { name: /revoke/i }));
    const dialog = await screen.findByRole("alertdialog");
    await user.click(within(dialog).getByRole("button", { name: /cancel/i }));

    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
  });

  it("resets a previous revoke error when the dialog reopens for a different key", async () => {
    const revokeState = { isError: false };
    mockUseRevokeToken.mockImplementation(() => ({
      mutate: (id: number, opts?: { onSuccess?: () => void }) => {
        mockRevokeMutate(id, opts);
        revokeState.isError = true;
      },
      reset: () => {
        mockRevokeReset();
        revokeState.isError = false;
      },
      isPending: false,
      isError: revokeState.isError,
    }));
    mockUseMyTokens.mockReturnValue({
      data: [ACTIVE_TOKEN, { ...ACTIVE_TOKEN, id: 2, name: "desktop" }],
      isLoading: false,
    });

    const user = userEvent.setup({ delay: null });
    const { rerender } = render(
      <MemoryRouter>
        <ApiKeysView />
      </MemoryRouter>,
    );

    await user.click(screen.getAllByRole("button", { name: /revoke/i })[0]!);
    const firstDialog = await screen.findByRole("alertdialog");
    await user.click(
      within(firstDialog).getByRole("button", { name: /revoke|delete/i }),
    );
    rerender(
      <MemoryRouter>
        <ApiKeysView />
      </MemoryRouter>,
    );
    expect(screen.getByText(/could not revoke the key/i)).toBeInTheDocument();

    await user.click(
      within(screen.getByRole("alertdialog")).getByRole("button", {
        name: /cancel/i,
      }),
    );
    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();

    await user.click(screen.getAllByRole("button", { name: /revoke/i })[1]!);
    await screen.findByRole("alertdialog");
    expect(mockRevokeReset).toHaveBeenCalled();
    expect(
      screen.queryByText(/could not revoke the key/i),
    ).not.toBeInTheDocument();
  });

  it("opens the edit dialog through the page when a row's edit control is used", async () => {
    const user = userEvent.setup({ delay: null });
    render(
      <MemoryRouter>
        <ApiKeysView />
      </MemoryRouter>,
    );

    await user.click(screen.getByRole("button", { name: /edit/i }));

    expect(
      await screen.findByRole("dialog", { name: /edit api key/i }),
    ).toBeInTheDocument();
  });

  it("says the keys could not load, never as no keys, and asks again when told to", async () => {
    // given
    const refetch = vi.fn();
    mockUseMyTokens.mockReturnValue({
      data: undefined,
      isLoading: true,
      errorUpdateCount: 1,
      isFetching: false,
      refetch,
    });
    render(
      <MemoryRouter>
        <ApiKeysView />
      </MemoryRouter>,
    );

    // when
    await userEvent.click(screen.getByRole("button", { name: "Try again" }));

    // then
    expect(screen.getByRole("alert")).toHaveTextContent(
      "Could not load the API keys.",
    );
    expect(screen.queryByText("No keys yet")).not.toBeInTheDocument();
    expect(refetch).toHaveBeenCalledOnce();
  });
});
