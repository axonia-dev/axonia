import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { useCurrentOrganization } from "@/features/admin/api/use-current-organization";
import { usePermissions } from "@/features/admin/api/use-permissions";
import {
  useRoundupPreview,
  useRoundupSettings,
} from "@/features/admin/api/use-roundup-settings";
import { useUpdateRoundupEnabled } from "@/features/admin/api/use-update-roundup-enabled";
import { RoundupCard } from "@/features/admin/ui/RoundupCard";
import { permissionState } from "@/test/permissions";

vi.mock("@/features/admin/api/use-current-organization", () => ({
  useCurrentOrganization: vi.fn(),
}));
vi.mock("@/features/admin/api/use-permissions", () => ({
  usePermissions: vi.fn(),
}));
vi.mock("@/features/admin/api/use-roundup-settings", () => ({
  useRoundupSettings: vi.fn(),
  useRoundupPreview: vi.fn(),
}));
vi.mock("@/features/admin/api/use-update-roundup-enabled", () => ({
  useUpdateRoundupEnabled: vi.fn(),
}));

const mutate = vi.fn();
const refetchSettings = vi.fn();

interface CardOptions {
  kind: "ENTERPRISE" | "HUB";
  can: ReadonlyArray<string>;
  enabled?: boolean;
  loaded?: boolean;
  readFailed?: boolean;
  retrying?: boolean;
}

function renderCard(options: CardOptions) {
  vi.mocked(useCurrentOrganization).mockReturnValue({
    data: { id: "acme", name: "Acme", kind: options.kind },
  } as never);
  vi.mocked(usePermissions).mockReturnValue(
    permissionState((permission: string) => options.can.includes(permission)),
  );
  const retrying = options.retrying === true;
  const failed = options.readFailed === true;
  vi.mocked(useRoundupSettings).mockReturnValue({
    data:
      options.loaded === false || failed
        ? undefined
        : { organizationId: "acme", enabled: options.enabled ?? false },
    isError: failed && !retrying,
    errorUpdateCount: failed ? 1 : 0,
    isFetching: retrying,
    error: failed && !retrying ? new Error("boom") : null,
    refetch: refetchSettings,
  } as never);
  render(<RoundupCard />);
}

beforeEach(() => {
  vi.clearAllMocks();
  vi.mocked(useUpdateRoundupEnabled).mockReturnValue({
    mutate,
    isPending: false,
    isError: false,
    error: null,
  } as never);
  vi.mocked(useRoundupPreview).mockReturnValue({
    data: { subject: "Acme, last quarter", html: "<p>hi</p>" },
    isLoading: false,
    isError: false,
    error: null,
  } as never);
});

describe("RoundupCard", () => {
  it("is absent for a hub", () => {
    // when - the roundup is enterprise-only: a hub has no provisioned directory
    renderCard({ kind: "HUB", can: ["communication:write"] });

    // then
    expect(screen.queryByText(/quarterly roundup/i)).not.toBeInTheDocument();
  });

  it("is absent without the permission", () => {
    // when
    renderCard({ kind: "ENTERPRISE", can: [] });

    // then
    expect(screen.queryByText(/quarterly roundup/i)).not.toBeInTheDocument();
  });

  it("reads off for an organization that has never enabled it", () => {
    // when
    renderCard({
      kind: "ENTERPRISE",
      can: ["communication:write"],
      enabled: false,
    });

    // then
    expect(screen.getByRole("switch")).not.toBeChecked();
  });

  it("says who receives it, since that is the fact being decided on", () => {
    // when
    renderCard({ kind: "ENTERPRISE", can: ["communication:write"] });

    // then
    expect(screen.getByRole("switch")).toHaveAccessibleDescription(
      /everyone in the organization/i,
    );
  });

  it("shows the rendered message before anyone commits to sending it", async () => {
    // given
    const user = userEvent.setup({ delay: null });
    renderCard({
      kind: "ENTERPRISE",
      can: ["communication:write"],
      enabled: false,
    });

    // when
    await user.click(screen.getByRole("button", { name: /preview/i }));

    // then - and sandboxed: the rendered mail may not act on the app's origin
    const iframe = await screen.findByTitle(/roundup preview/i);
    expect(iframe).toHaveAttribute("srcdoc", "<p>hi</p>");
    expect(iframe).toHaveAttribute("sandbox", "");
  });

  it("turns the roundup on without asking for confirmation", async () => {
    // given
    const user = userEvent.setup({ delay: null });
    renderCard({
      kind: "ENTERPRISE",
      can: ["communication:write"],
      enabled: false,
    });

    // when
    await user.click(screen.getByRole("switch"));

    // then
    expect(mutate).toHaveBeenCalledWith(true);
    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
  });

  it("turns it back off without a confirmation either, there being no mail in flight to stop", async () => {
    // given
    const user = userEvent.setup({ delay: null });
    renderCard({
      kind: "ENTERPRISE",
      can: ["communication:write"],
      enabled: true,
    });

    // when
    await user.click(screen.getByRole("switch"));

    // then
    expect(mutate).toHaveBeenCalledWith(false);
    expect(screen.queryByRole("alertdialog")).not.toBeInTheDocument();
  });

  it("surfaces a failed save rather than leaving the switch looking ignored", () => {
    // given
    vi.mocked(useUpdateRoundupEnabled).mockReturnValue({
      mutate,
      isPending: false,
      isError: true,
      error: new Error("boom"),
    } as never);

    // when
    renderCard({ kind: "ENTERPRISE", can: ["communication:write"] });

    // then
    expect(screen.getByRole("alert")).toBeInTheDocument();
  });

  it("disables the switch while a save is in flight", () => {
    // given
    vi.mocked(useUpdateRoundupEnabled).mockReturnValue({
      mutate,
      isPending: true,
      isError: false,
      error: null,
    } as never);

    // when
    renderCard({ kind: "ENTERPRISE", can: ["communication:write"] });

    // then
    expect(screen.getByRole("switch")).toBeDisabled();
  });

  /**
   * A failed read must not look like a hub. Both used to return null, so a 500 or a dropped
   * network took the whole control away with no alert and no retry, while the kill switch
   * above it stayed put - and an administrator has no way to tell that apart from a roundup
   * their tenant was never offered.
   */
  it.each([
    ["once it failed", false],
    ["while it is asked for again, its retry pending", true],
  ])(
    "says the setting could not be read %s, rather than disappearing",
    (_, retrying) => {
      // when
      renderCard({
        kind: "ENTERPRISE",
        can: ["communication:write"],
        readFailed: true,
        retrying,
      });

      // then
      expect(screen.getByRole("alert")).toHaveTextContent(
        "Could not read whether the roundup is on.",
      );
      expect(screen.getByText("Quarterly roundup")).toBeVisible();
      expect(screen.queryByRole("switch")).not.toBeInTheDocument();
      expect(
        screen
          .getByRole("button", { name: "Try again" })
          .hasAttribute("data-pending"),
      ).toBe(retrying);
    },
  );

  it("offers a retry after a failed read, rather than only an apology", async () => {
    // given
    const user = userEvent.setup({ delay: null });
    renderCard({
      kind: "ENTERPRISE",
      can: ["communication:write"],
      readFailed: true,
    });

    // when
    await user.click(screen.getByRole("button", { name: /try again/i }));

    // then
    expect(refetchSettings).toHaveBeenCalled();
  });

  it("renders nothing until the settings have loaded", () => {
    // when
    renderCard({
      kind: "ENTERPRISE",
      can: ["communication:write"],
      loaded: false,
    });

    // then
    expect(screen.queryByRole("switch")).not.toBeInTheDocument();
  });

  it("says the preview is on its way rather than showing an empty dialog", async () => {
    // given
    const user = userEvent.setup({ delay: null });
    vi.mocked(useRoundupPreview).mockReturnValue({
      data: undefined,
      isLoading: true,
      isError: false,
      error: null,
    } as never);
    renderCard({ kind: "ENTERPRISE", can: ["communication:write"] });

    // when
    await user.click(screen.getByRole("button", { name: /preview/i }));

    // then
    expect(await screen.findByText(/loading preview/i)).toBeInTheDocument();
  });

  it("tells the reader when the preview cannot be rendered", async () => {
    // given
    const user = userEvent.setup({ delay: null });
    vi.mocked(useRoundupPreview).mockReturnValue({
      data: undefined,
      isLoading: false,
      isError: true,
      error: new Error("boom"),
    } as never);
    renderCard({ kind: "ENTERPRISE", can: ["communication:write"] });

    // when
    await user.click(screen.getByRole("button", { name: /preview/i }));

    // then
    expect(await screen.findByRole("alert")).toBeInTheDocument();
  });
});
