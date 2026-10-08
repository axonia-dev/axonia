import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import type { PersonalToken } from "../domain/personal-access-token";
import { TokenList } from "./TokenList";

const NOW = new Date("2026-08-14T18:00:00Z");

function token(overrides: Partial<PersonalToken>): PersonalToken {
  return {
    id: 1,
    name: "claude",
    scopes: ["read"],
    createdAt: "2026-08-14T18:00:00Z",
    lastUsedAt: null,
    expiresAt: null,
    status: "ACTIVE",
    ...overrides,
  };
}

const TOKENS = [
  token({ id: 1, name: "claude" }),
  token({ id: 2, name: "old laptop", status: "REVOKED" }),
  token({ id: 3, name: "prototype", status: "EXPIRED" }),
];

describe("TokenList", () => {
  it("shows active keys and hides terminal ones behind a counted toggle", () => {
    render(
      <TokenList
        tokens={TOKENS}
        now={NOW}
        onRevoke={vi.fn()}
        onEdit={vi.fn()}
      />,
    );

    expect(screen.getByText("claude")).toBeInTheDocument();
    expect(screen.queryByText("old laptop")).not.toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Show revoked and expired (2)" }),
    ).toHaveAttribute("aria-expanded", "false");
  });

  it("reveals the terminal keys when the toggle is used", async () => {
    const user = userEvent.setup({ delay: null });
    render(
      <TokenList
        tokens={TOKENS}
        now={NOW}
        onRevoke={vi.fn()}
        onEdit={vi.fn()}
      />,
    );

    await user.click(
      screen.getByRole("button", { name: "Show revoked and expired (2)" }),
    );

    expect(screen.getByText("old laptop")).toBeInTheDocument();
    expect(screen.getByText("prototype")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Hide revoked and expired (2)" }),
    ).toHaveAttribute("aria-expanded", "true");
  });

  it("renders terminal rows directly, with no toggle, when there are no active keys", () => {
    render(
      <TokenList
        tokens={[
          token({ id: 2, name: "old laptop", status: "REVOKED" }),
          token({ id: 3, name: "prototype", status: "EXPIRED" }),
        ]}
        now={NOW}
        onRevoke={vi.fn()}
        onEdit={vi.fn()}
      />,
    );

    expect(screen.getByText("old laptop")).toBeInTheDocument();
    expect(screen.getByText("prototype")).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /revoked and expired/i }),
    ).not.toBeInTheDocument();
  });

  it("shows terminal rows without a click once the last active key turns terminal", () => {
    const activeToken = token({ id: 1, name: "claude", status: "ACTIVE" });
    const terminalToken = token({
      id: 2,
      name: "old laptop",
      status: "REVOKED",
    });
    const { rerender } = render(
      <TokenList
        tokens={[activeToken, terminalToken]}
        now={NOW}
        onRevoke={vi.fn()}
        onEdit={vi.fn()}
      />,
    );

    expect(screen.queryByText("old laptop")).not.toBeInTheDocument();

    rerender(
      <TokenList
        tokens={[{ ...activeToken, status: "REVOKED" }, terminalToken]}
        now={NOW}
        onRevoke={vi.fn()}
        onEdit={vi.fn()}
      />,
    );

    expect(screen.getByText("claude")).toBeInTheDocument();
    expect(screen.getByText("old laptop")).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: /revoked and expired/i }),
    ).not.toBeInTheDocument();
  });

  it("offers no toggle when every key is active", () => {
    render(
      <TokenList
        tokens={[token({ id: 1 })]}
        now={NOW}
        onRevoke={vi.fn()}
        onEdit={vi.fn()}
      />,
    );

    expect(
      screen.queryByRole("button", { name: /revoked and expired/i }),
    ).not.toBeInTheDocument();
  });
});
