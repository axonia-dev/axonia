import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import type { PersonalToken } from "../domain/personal-access-token";

const mockUpdateMutate = vi.fn();

vi.mock("../api/use-personal-access-tokens", () => ({
  useUpdateToken: () => ({ mutate: mockUpdateMutate, isPending: false }),
}));

import { EditTokenDialog } from "./EditTokenDialog";

const NOW = new Date("2026-08-14T18:00:00Z");

const TOKEN: PersonalToken = {
  id: 7,
  name: "claude",
  scopes: ["members:read"],
  createdAt: "2026-08-14T18:00:00Z",
  lastUsedAt: null,
  expiresAt: "2026-08-16T18:00:00Z",
  status: "ACTIVE",
};

beforeEach(() => vi.clearAllMocks());

describe("EditTokenDialog", () => {
  it("opens on the key's current name and permissions", async () => {
    render(<EditTokenDialog token={TOKEN} now={NOW} onOpenChange={vi.fn()} />);

    expect(await screen.findByRole("textbox", { name: "Name" })).toHaveValue(
      "claude",
    );
    expect(
      screen.getByRole("switch", {
        name: "Read community member lists and learners",
      }),
    ).toBeChecked();
    expect(
      screen.getByRole("switch", {
        name: "Read schedule, communities, and library",
      }),
    ).not.toBeChecked();
  });

  it("states that the expiry cannot be moved", async () => {
    render(<EditTokenDialog token={TOKEN} now={NOW} onOpenChange={vi.fn()} />);

    expect(
      await screen.findByText(/the expiry is fixed when a key is created/i),
    ).toBeInTheDocument();
  });

  it("saves the changed permissions against the key's id", async () => {
    const user = userEvent.setup({ delay: null });
    render(<EditTokenDialog token={TOKEN} now={NOW} onOpenChange={vi.fn()} />);

    await user.click(
      await screen.findByRole("switch", { name: "Propose topics" }),
    );
    await user.click(screen.getByRole("button", { name: "Save" }));

    expect(mockUpdateMutate).toHaveBeenCalledWith(
      {
        id: 7,
        body: { name: "claude", scopes: ["members:read", "topic:propose"] },
      },
      expect.anything(),
    );
  });

  it("preserves a scope code the frontend catalog does not know when saving", async () => {
    const user = userEvent.setup({ delay: null });
    const tokenWithUnknownScope: PersonalToken = {
      ...TOKEN,
      scopes: ["members:read", "future:capability"],
    };
    render(
      <EditTokenDialog
        token={tokenWithUnknownScope}
        now={NOW}
        onOpenChange={vi.fn()}
      />,
    );

    const nameField = await screen.findByRole("textbox", { name: "Name" });
    await user.clear(nameField);
    await user.type(nameField, "renamed");
    await user.click(screen.getByRole("button", { name: "Save" }));

    expect(mockUpdateMutate).toHaveBeenCalledWith(
      {
        id: 7,
        body: {
          name: "renamed",
          scopes: ["members:read", "future:capability"],
        },
      },
      expect.anything(),
    );
  });
});
