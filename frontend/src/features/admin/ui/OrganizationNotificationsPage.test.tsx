import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { EditionProvider } from "@/edition-context";
import { usePermissions } from "@/features/admin/api/use-permissions";
import { OrganizationNotificationsPage } from "@/features/admin/ui/OrganizationNotificationsPage";
import { WITH_NOTIFICATIONS_CARD } from "@/test/edition-fixtures";
import { permissionState } from "@/test/permissions";

vi.mock("@/features/admin/ui/CommunicationsKillSwitch", () => ({
  CommunicationsKillSwitch: () => <p>Kill switch</p>,
}));
vi.mock("@/features/admin/ui/RoundupCard", () => ({
  RoundupCard: () => <p>Roundup card</p>,
}));
vi.mock("@/features/admin/api/use-permissions", () => ({
  usePermissions: vi.fn(),
}));

beforeEach(() => {
  vi.mocked(usePermissions).mockReturnValue(permissionState(() => true));
});

describe("OrganizationNotificationsPage", () => {
  it("gathers the organization-wide mail controls under one heading", () => {
    // when
    render(<OrganizationNotificationsPage />);

    // then
    expect(
      screen.getByRole("heading", { name: "Notifications" }),
    ).toBeInTheDocument();
    expect(screen.getByText("Kill switch")).toBeInTheDocument();
    expect(screen.getByText("Roundup card")).toBeInTheDocument();
  });

  it("says the permissions could not load, rather than an empty page", () => {
    // given
    vi.mocked(usePermissions).mockReturnValue(
      permissionState(() => false, { isError: true }),
    );

    // when
    render(<OrganizationNotificationsPage />);

    // then
    expect(screen.getByRole("alert")).toHaveTextContent(
      "Could not load the permissions.",
    );
    expect(
      screen.getByRole("heading", { name: "Notifications" }),
    ).toBeInTheDocument();
    expect(screen.queryByText("Kill switch")).not.toBeInTheDocument();
  });

  it("leads with the card an edition puts on the page, above the kill switch", () => {
    // when
    render(
      <EditionProvider edition={WITH_NOTIFICATIONS_CARD}>
        <OrganizationNotificationsPage />
      </EditionProvider>,
    );

    // then
    const card = screen.getByText("Edition card");
    const killSwitch = screen.getByText("Kill switch");
    expect(
      card.compareDocumentPosition(killSwitch) &
        Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
  });

  it("draws nothing of its own between the heading and the kill switch in an edition that leaves the slot empty", () => {
    // when
    const { container } = render(<OrganizationNotificationsPage />);

    // then
    const sections = container.firstElementChild?.children ?? [];
    expect(Array.from(sections, (section) => section.textContent)).toEqual([
      "Notifications",
      "Kill switch",
      "Roundup card",
    ]);
  });
});
