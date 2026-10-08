import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

const mockConnections = vi.fn();
const mockDisconnect = vi.fn();
const mockDisconnectState = vi.fn();

vi.mock("../api/use-agent-connections", () => ({
  useMyConnections: () => mockConnections() as never,
  useDisconnectConnection: () => ({
    mutate: mockDisconnect,
    reset: vi.fn(),
    ...(mockDisconnectState() as object),
  }),
}));

import { ConnectedApps } from "./ConnectedApps";

const ACTIVE = {
  id: 4,
  clientName: "Claude Code",
  clientHost: "claude.ai",
  scopes: ["read", "topic:propose"],
  activatedAt: "2026-09-20T12:00:00Z",
  lastUsedAt: null,
  endsAt: "2027-03-19T12:00:00Z",
  status: "ACTIVE",
};

beforeEach(() => {
  vi.clearAllMocks();
  mockConnections.mockReturnValue({
    data: [ACTIVE, { ...ACTIVE, id: 5, status: "REVOKED" }],
  });
  mockDisconnectState.mockReturnValue({ isPending: false, isError: false });
});

describe("ConnectedApps", () => {
  it("lists active connections only, with their host, permissions and timeline", () => {
    render(<ConnectedApps now={new Date("2026-09-24T12:00:00Z")} />);

    expect(
      screen.getByRole("heading", { name: "Connected apps" }),
    ).toBeInTheDocument();
    expect(screen.getAllByRole("listitem")).toHaveLength(1);
    expect(screen.getByText("Claude Code")).toBeInTheDocument();
    expect(screen.getByText("claude.ai")).toBeInTheDocument();
    expect(screen.getByText("topic:propose")).toBeInTheDocument();
    expect(screen.getByText(/Connected Sep 20, 2026/)).toBeInTheDocument();
  });

  it("says the connected apps could not load, rather than hiding", async () => {
    const refetch = vi.fn();
    mockConnections.mockReturnValue({
      data: undefined,
      errorUpdateCount: 1,
      isFetching: false,
      refetch,
    });
    const user = userEvent.setup({ delay: null });
    render(<ConnectedApps now={new Date()} />);

    await user.click(screen.getByRole("button", { name: "Try again" }));

    expect(
      screen.getByRole("heading", { name: "Connected apps" }),
    ).toBeInTheDocument();
    expect(screen.getByRole("alert")).toHaveTextContent(
      "Could not load the connected apps.",
    );
    expect(refetch).toHaveBeenCalledOnce();
  });

  it("renders nothing without an active connection", () => {
    mockConnections.mockReturnValue({ data: [{ ...ACTIVE, status: "ENDED" }] });

    const { container } = render(<ConnectedApps now={new Date()} />);

    expect(container).toBeEmptyDOMElement();
  });

  it("disconnects after confirming", async () => {
    const user = userEvent.setup({ delay: null });
    render(<ConnectedApps now={new Date()} />);

    await user.click(
      screen.getByRole("button", { name: "Disconnect Claude Code" }),
    );
    const dialog = await screen.findByRole("alertdialog");
    await user.click(
      within(dialog).getByRole("button", { name: "Disconnect" }),
    );

    expect(mockDisconnect).toHaveBeenCalledWith(4, expect.anything());
  });

  it("says what disconnecting costs before confirming", async () => {
    const user = userEvent.setup({ delay: null });
    render(<ConnectedApps now={new Date()} />);

    await user.click(
      screen.getByRole("button", { name: "Disconnect Claude Code" }),
    );

    expect(await screen.findByRole("alertdialog")).toHaveTextContent(
      "The app loses access immediately. Connecting again asks for consent.",
    );
  });

  it("keeps the dialog open with the error when disconnecting fails", async () => {
    mockDisconnectState.mockReturnValue({ isPending: false, isError: true });
    const user = userEvent.setup({ delay: null });
    render(<ConnectedApps now={new Date()} />);

    await user.click(
      screen.getByRole("button", { name: "Disconnect Claude Code" }),
    );

    expect(
      await screen.findByText("Could not disconnect. Try again."),
    ).toBeInTheDocument();
  });
});
