import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useCurrentOrganization } from "@/features/admin/api/use-current-organization";
import { usePermissions } from "@/features/admin/api/use-permissions";
import { useUpdateCommunicationsEnabled } from "@/features/admin/api/use-update-communications-enabled";
import { CommunicationsKillSwitch } from "@/features/admin/ui/CommunicationsKillSwitch";
import { permissionState } from "@/test/permissions";

vi.mock("@/features/admin/api/use-current-organization", () => ({
  useCurrentOrganization: vi.fn(),
}));
vi.mock("@/features/admin/api/use-permissions", () => ({
  usePermissions: vi.fn(),
}));
vi.mock("@/features/admin/api/use-update-communications-enabled", () => ({
  useUpdateCommunicationsEnabled: vi.fn(),
}));

const mutate = vi.fn();

function mockOrg(communicationsEnabled: boolean) {
  vi.mocked(useCurrentOrganization).mockReturnValue({
    data: {
      id: "acme",
      name: "Acme",
      kind: "ENTERPRISE",
      communicationsEnabled,
    },
  } as never);
}

function renderOrganizationSettings(options: {
  communicationsEnabled: boolean;
  granted?: ReadonlyArray<string>;
}) {
  mockOrg(options.communicationsEnabled);
  const granted = options.granted ?? ["communication:write"];
  vi.mocked(usePermissions).mockReturnValue(
    permissionState((permission: string) => granted.includes(permission)),
  );
  render(<CommunicationsKillSwitch />);
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(useUpdateCommunicationsEnabled).mockReturnValue({
    mutate,
    isPending: false,
    isError: false,
    error: null,
  } as never);
});

describe("CommunicationsKillSwitch", () => {
  it("warns before switching every message off for the organization", async () => {
    // given
    const user = userEvent.setup({ delay: null });
    renderOrganizationSettings({ communicationsEnabled: true });

    // when
    await user.click(
      screen.getByRole("switch", { name: /email and chat notifications/i }),
    );

    // then
    expect(await screen.findByRole("alertdialog")).toHaveTextContent(
      /turns off email and chat/i,
    );
    expect(mutate).not.toHaveBeenCalled();
  });

  it("actually disables once the dialog is confirmed", async () => {
    // given
    const user = userEvent.setup({ delay: null });
    renderOrganizationSettings({ communicationsEnabled: true });
    await user.click(
      screen.getByRole("switch", { name: /email and chat notifications/i }),
    );

    // when
    await user.click(await screen.findByRole("button", { name: /^disable$/i }));

    // then
    await waitFor(() => expect(mutate).toHaveBeenCalledWith(false));
  });

  it("turns email back on with no confirmation dialog", async () => {
    // given
    const user = userEvent.setup({ delay: null });
    renderOrganizationSettings({ communicationsEnabled: false });

    // when
    await user.click(
      screen.getByRole("switch", { name: /email and chat notifications/i }),
    );

    // then
    expect(mutate).toHaveBeenCalledWith(true);
    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
  });

  /**
   * The description enumerates what goes quiet, and the gate checks this switch before it even
   * resolves a row's message type, so every kind of mail is in scope. An enumeration that lags
   * behind the message types the product sends is how an administrator ends up believing they
   * silenced everything and hearing otherwise from a recipient.
   *
   * One assertion per MessageType constant rather than one on the whole sentence: a fifth
   * message type has to fail here, while rewording the connective tissue around the list must
   * not. Adding a constant to MessageType means adding an item above and a line below.
   */
  it("names every kind of mail it stops, one for each message type", () => {
    // when
    renderOrganizationSettings({ communicationsEnabled: true });

    // then
    const control = screen.getByRole("switch", {
      name: /email and chat notifications/i,
    });
    expect(control).toHaveAccessibleDescription(/session reminders/i);
    expect(control).toHaveAccessibleDescription(/wrap-ups/i);
    expect(control).toHaveAccessibleDescription(/confirm a session happened/i);
    expect(control).toHaveAccessibleDescription(/quarterly roundup/i);
  });

  /**
   * The roundup goes to everyone the directory reports as present, community member or not, so
   * the scope clause cannot say "every community" while the list names the roundup. The dialog
   * says the same thing, and the two are read seconds apart.
   */
  it("says the scope is everyone in the organization, not every community", () => {
    // when
    renderOrganizationSettings({ communicationsEnabled: true });

    // then
    expect(
      screen.getByRole("switch", { name: /email and chat notifications/i }),
    ).toHaveAccessibleDescription(/for everyone in this organization/i);
  });

  /**
   * The switch stops mail and chat because both leave the organization; it does not stop the
   * in-app notification panel, because a panel inside a product a member is already signed
   * into is not axonia contacting them. The description must say so, since an administrator
   * who reads "stops all of it" without that carve-out will believe they silenced the panel
   * too and find out otherwise from a member.
   */
  it("scopes itself to outbound messages and says the in-app panel is unaffected", () => {
    // when
    renderOrganizationSettings({ communicationsEnabled: true });

    // then
    const control = screen.getByRole("switch", {
      name: /email and chat notifications/i,
    });
    expect(control).toHaveAccessibleDescription(/outward/i);
    expect(control).toHaveAccessibleDescription(
      /in-app notification panel is unaffected/i,
    );
  });

  it("shows the switch on when the organization has email enabled", () => {
    // when
    renderOrganizationSettings({ communicationsEnabled: true });

    // then
    expect(
      screen.getByRole("switch", { name: /email and chat notifications/i }),
    ).toBeChecked();
  });

  it("renders nothing for a caller without communication:write", () => {
    // when
    renderOrganizationSettings({
      communicationsEnabled: true,
      granted: [],
    });

    // then
    expect(screen.queryByRole("switch")).not.toBeInTheDocument();
  });

  it("renders nothing for a caller who only has organization:write", () => {
    // when - the generic right to write to the organization no longer carries
    // the kill switch; managing communication is its own permission
    renderOrganizationSettings({
      communicationsEnabled: true,
      granted: ["organization:write"],
    });

    // then
    expect(screen.queryByRole("switch")).not.toBeInTheDocument();
  });

  it("shows an error and leaves the switch untouched when the save fails", () => {
    // given - a mutation that has already failed (403, RLS rejection, dropped
    // network - the switch must not read as "ignored my click")
    vi.mocked(useUpdateCommunicationsEnabled).mockReturnValue({
      mutate,
      isPending: false,
      isError: true,
      error: new Error("boom"),
    } as never);

    // when
    renderOrganizationSettings({ communicationsEnabled: true });

    // then
    expect(screen.getByRole("alert")).toBeInTheDocument();
  });

  it("disables the switch while a save is in flight, so a second click can't reopen the dialog", () => {
    // given
    vi.mocked(useUpdateCommunicationsEnabled).mockReturnValue({
      mutate,
      isPending: true,
      isError: false,
      error: null,
    } as never);

    // when
    renderOrganizationSettings({ communicationsEnabled: true });

    // then
    expect(
      screen.getByRole("switch", { name: /email and chat notifications/i }),
    ).toBeDisabled();
  });

  it("renders nothing while the organization has not loaded yet", () => {
    // given
    vi.mocked(useCurrentOrganization).mockReturnValue({
      data: undefined,
    } as never);
    vi.mocked(usePermissions).mockReturnValue(permissionState(() => true));

    // when
    render(<CommunicationsKillSwitch />);

    // then
    expect(screen.queryByRole("switch")).not.toBeInTheDocument();
  });
});
