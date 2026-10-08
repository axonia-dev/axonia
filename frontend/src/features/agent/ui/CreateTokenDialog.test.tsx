import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

const mockCreateMutate = vi.fn();

vi.mock("../api/use-personal-access-tokens", () => ({
  useCreateToken: () => ({ mutate: mockCreateMutate, isPending: false }),
}));

import { CreateTokenDialog } from "./CreateTokenDialog";

function renderDialog() {
  render(<CreateTokenDialog isOpen onOpenChange={vi.fn()} />);
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

beforeEach(() => {
  vi.clearAllMocks();
});

describe("CreateTokenDialog", () => {
  it("offers the schedule:manage capability, so an agent can backfill a community's history", async () => {
    // when
    renderDialog();

    // then
    expect(
      await screen.findByRole("switch", { name: "Create and shape sessions" }),
    ).toBeInTheDocument();
  });

  it("offers the learning:declare capability as a write permission", async () => {
    // when
    renderDialog();

    // then
    const writes = await screen.findByRole("group", {
      name: "Write permissions",
    });
    expect(
      within(writes).getByRole("switch", { name: "Declare areas to learn" }),
    ).toBeInTheDocument();
  });

  it("offers the member-list scope as a read permission, not a write one", async () => {
    // when
    renderDialog();

    // then
    const reads = await screen.findByRole("group", {
      name: "Read permissions",
    });
    expect(
      within(reads).getByRole("switch", {
        name: "Read community member lists and learners",
      }),
    ).toBeInTheDocument();

    const writes = screen.getByRole("group", { name: "Write permissions" });
    expect(
      within(writes).queryByRole("switch", {
        name: "Read community member lists and learners",
      }),
    ).not.toBeInTheDocument();
  });

  it("mints a token carrying members:read when it is ticked", async () => {
    const user = userEvent.setup({ delay: null });
    renderDialog();

    // when
    await user.type(await screen.findByLabelText("Name"), "Claude desktop");
    await user.click(
      screen.getByRole("switch", {
        name: "Read community member lists and learners",
      }),
    );
    await pickExpiryDate(user);
    await user.click(screen.getByRole("button", { name: "Create" }));

    // then
    expect(mockCreateMutate).toHaveBeenCalledWith(
      expect.objectContaining({
        name: "Claude desktop",
        scopes: ["members:read"],
        expiresAt: "2027-08-01T23:59:59Z",
      }),
      expect.anything(),
    );
  });

  it("mints a token with no expiry, sending null rather than a blank date", async () => {
    const user = userEvent.setup({ delay: null });
    renderDialog();

    // when
    await user.type(await screen.findByLabelText("Name"), "Claude desktop");
    await user.click(
      screen.getByRole("switch", {
        name: "Read community member lists and learners",
      }),
    );
    await user.click(screen.getByRole("button", { name: "Create" }));

    // then
    expect(mockCreateMutate).toHaveBeenCalledWith(
      expect.objectContaining({
        name: "Claude desktop",
        scopes: ["members:read"],
        expiresAt: null,
      }),
      expect.anything(),
    );
  });

  it("keeps the dialog open and hands the secret over in place", async () => {
    const user = userEvent.setup({ delay: null });
    mockCreateMutate.mockImplementation(
      (
        _body: unknown,
        opts: { onSuccess: (res: { token: string }) => void },
      ) => {
        opts.onSuccess({ token: "axp_secret123" });
      },
    );
    renderDialog();

    await user.type(
      await screen.findByRole("textbox", { name: "Name" }),
      "claude",
    );
    await user.click(
      screen.getByRole("switch", {
        name: "Read community member lists and learners",
      }),
    );
    await pickExpiryDate(user);
    await user.click(screen.getByRole("button", { name: "Create" }));

    expect(
      screen.getByRole("heading", { name: "Key created" }),
    ).toBeInTheDocument();
    expect(screen.getByText("axp_secret123")).toBeInTheDocument();
    expect(
      screen.getByText(/cannot be retrieved after this dialog closes/i),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Done" })).toBeInTheDocument();
  });

  it("does not show a stale secret when the dialog is reopened after Done", async () => {
    const user = userEvent.setup({ delay: null });
    mockCreateMutate.mockImplementation(
      (
        _body: unknown,
        opts: { onSuccess: (res: { token: string }) => void },
      ) => {
        opts.onSuccess({ token: "axp_stale456" });
      },
    );
    const onOpenChange = vi.fn();
    const { rerender } = render(
      <CreateTokenDialog isOpen onOpenChange={onOpenChange} />,
    );

    await user.type(
      await screen.findByRole("textbox", { name: "Name" }),
      "claude",
    );
    await user.click(
      screen.getByRole("switch", {
        name: "Read community member lists and learners",
      }),
    );
    await pickExpiryDate(user);
    await user.click(screen.getByRole("button", { name: "Create" }));
    expect(
      screen.getByRole("heading", { name: "Key created" }),
    ).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Done" }));
    expect(onOpenChange).toHaveBeenCalledWith(false);

    rerender(<CreateTokenDialog isOpen={false} onOpenChange={onOpenChange} />);
    rerender(<CreateTokenDialog isOpen onOpenChange={onOpenChange} />);

    expect(
      await screen.findByRole("heading", { name: "Create API key" }),
    ).toBeInTheDocument();
    expect(
      screen.queryByRole("heading", { name: "Key created" }),
    ).not.toBeInTheDocument();
    expect(screen.queryByText("axp_stale456")).not.toBeInTheDocument();
  });
});
