import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { usePermissions } from "@/features/admin/api/use-permissions";
import {
  ACCEPTED,
  CONFIGURED,
  NOT_CONFIGURED,
} from "@/selfhosted/features/mail-server/domain/mail-server.test-helpers";
import {
  answerChangesPending,
  answerClears,
  answerSaves,
  answerStatus,
  answerTests,
  answerView,
} from "@/selfhosted/features/mail-server/ui/mail-server-hooks.test-helpers";
import { MailServerCard } from "@/selfhosted/features/mail-server/ui/MailServerCard";
import { permissionState } from "@/test/permissions";

vi.mock("@/features/admin/api/use-permissions", () => ({
  usePermissions: vi.fn(),
}));
vi.mock("@/features/identity/api/use-me", () => ({
  useMe: () => ({ data: { email: "ana.ruiz@acme.com" } }),
}));
vi.mock("@/selfhosted/features/mail-server/api/use-mail-server", () => ({
  useMailServer: vi.fn(),
  useMailConfigured: vi.fn(),
  useMailServerChanges: vi.fn(),
  useTestMailServer: vi.fn(),
  useSaveMailServer: vi.fn(),
  useClearMailServer: vi.fn(),
}));

function card() {
  return within(screen.getByRole("region", { name: "Mail server" }));
}

beforeEach(() => {
  vi.mocked(usePermissions).mockReturnValue(permissionState(() => true));
  answerStatus(true);
  answerView(CONFIGURED);
  answerTests(ACCEPTED);
  answerSaves();
  answerClears();
});

async function openForm() {
  const user = userEvent.setup();
  render(<MailServerCard />);
  await user.click(card().getByRole("button", { name: "Edit" }));
  return user;
}

describe("the mail server card's focus", () => {
  it("moves focus to the first field as the form opens", async () => {
    // when
    await openForm();

    // then
    expect(card().getByRole("textbox", { name: "Host" })).toHaveFocus();
  });

  it("returns focus to Edit on Cancel", async () => {
    // given
    const user = await openForm();

    // when
    await user.click(card().getByRole("button", { name: "Cancel" }));

    // then
    expect(card().getByRole("button", { name: "Edit" })).toHaveFocus();
  });

  it("returns focus to Edit once a save succeeds", async () => {
    // given
    const user = await openForm();
    await user.click(card().getByRole("button", { name: "Send test mail" }));

    // when
    await user.click(card().getByRole("button", { name: "Save" }));

    // then
    expect(card().getByRole("button", { name: "Edit" })).toHaveFocus();
  });

  it("returns focus to the card's action once a clear succeeds", async () => {
    // given
    const user = await openForm();
    await user.click(card().getByRole("button", { name: "Clear" }));

    // when
    await user.click(
      within(screen.getByRole("alertdialog")).getByRole("button", {
        name: "Clear",
      }),
    );

    // then
    expect(card().getByRole("button", { name: "Edit" })).toHaveFocus();
  });

  it("returns focus to Set up when an unconfigured server's form is cancelled", async () => {
    // given
    const user = userEvent.setup();
    answerView(NOT_CONFIGURED);
    render(<MailServerCard />);
    await user.click(card().getByRole("button", { name: "Set up" }));

    // when
    await user.click(card().getByRole("button", { name: "Cancel" }));

    // then
    expect(card().getByRole("button", { name: "Set up" })).toHaveFocus();
  });
});

describe("the mail server card while it loads", () => {
  it("holds its place with its title while the mail server is read", () => {
    // given
    answerView(undefined);

    // when
    render(<MailServerCard />);

    // then
    const region = screen.getByRole("region", { name: "Mail server" });
    expect(region).toHaveAttribute("aria-busy", "true");
    expect(within(region).getByText("Loading…")).toBeVisible();
  });

  it("says the history is loading while its first page is read", async () => {
    // given
    const user = userEvent.setup();
    answerChangesPending();
    render(<MailServerCard />);

    // when
    await user.click(
      card().getByRole("button", {
        name: "History, last changed by Ana Ruiz on 2 Oct 2026",
      }),
    );

    // then
    expect(card().getByText("Loading…")).toBeVisible();
  });
});
