import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import type { PersonalToken } from "../domain/personal-access-token";
import { TokenRow } from "./TokenRow";

const NOW = new Date("2026-08-14T18:00:00Z");

function token(overrides: Partial<PersonalToken> = {}): PersonalToken {
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

function renderRow(t: PersonalToken) {
  const onRevoke = vi.fn();
  const onEdit = vi.fn();
  render(
    <ul>
      <TokenRow token={t} now={NOW} onRevoke={onRevoke} onEdit={onEdit} />
    </ul>,
  );
  return { onRevoke, onEdit };
}

describe("TokenRow", () => {
  it("names the key and its status", () => {
    renderRow(token());
    expect(screen.getByText("claude")).toBeInTheDocument();
    expect(screen.getByText("Active")).toBeInTheDocument();
  });

  it("collapses scopes past the fourth into a count", () => {
    renderRow(
      token({
        scopes: [
          "read",
          "members:read",
          "library:contribute",
          "topic:propose",
          "session:signup",
          "community:join",
        ],
      }),
    );
    expect(screen.getByText("read")).toBeInTheDocument();
    expect(screen.queryByText("session:signup")).not.toBeInTheDocument();
    expect(screen.getByText("+2")).toBeInTheDocument();
  });

  it("lists scopes in catalog order whatever order the server sent, unknown codes last", () => {
    renderRow(token({ scopes: ["future:scope", "topic:propose", "read"] }));
    const chips = screen
      .getAllByText(/^(read|topic:propose|future:scope)$/)
      .map((chip) => chip.textContent);
    expect(chips).toEqual(["read", "topic:propose", "future:scope"]);
  });

  it("colours an expiry inside the warning window", () => {
    renderRow(token({ expiresAt: "2026-08-16T18:00:00Z" }));
    expect(screen.getByText("Expires in 2 days")).toHaveClass("text-warning");
  });

  it("leaves a distant expiry uncoloured", () => {
    renderRow(token({ expiresAt: "2026-09-30T18:00:00Z" }));
    expect(screen.getByText(/^Expires/)).not.toHaveClass("text-warning");
  });

  it("offers no controls on a key that is already terminal", () => {
    renderRow(token({ status: "REVOKED" }));
    expect(screen.getByText("Revoked")).toBeInTheDocument();
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });

  it("asks to revoke the key it names", async () => {
    const user = userEvent.setup({ delay: null });
    const { onRevoke } = renderRow(token());

    await user.click(screen.getByRole("button", { name: "Revoke claude" }));

    expect(onRevoke).toHaveBeenCalledWith(expect.objectContaining({ id: 1 }));
  });

  it("asks to edit the key it names", async () => {
    const user = userEvent.setup({ delay: null });
    const { onEdit } = renderRow(token());

    await user.click(screen.getByRole("button", { name: "Edit claude" }));

    expect(onEdit).toHaveBeenCalledWith(expect.objectContaining({ id: 1 }));
  });
});
