import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { ApiError } from "@/lib/api-client";
import type { MailServerView } from "@/selfhosted/features/mail-server/domain/mail-server";
import {
  ACCEPTED,
  CONFIGURED,
  NOT_CONFIGURED,
} from "@/selfhosted/features/mail-server/domain/mail-server.test-helpers";
import {
  answerClears,
  answerSaves,
  answerTests,
} from "@/selfhosted/features/mail-server/ui/mail-server-hooks.test-helpers";
import { MailServerForm } from "@/selfhosted/features/mail-server/ui/MailServerForm";

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

const onClose = vi.fn();

const STALE =
  "Changed by someone else since this form opened. Cancel to see the change.";

beforeEach(() => {
  answerTests(ACCEPTED);
  answerSaves();
  answerClears();
});

function openForm(view: MailServerView = CONFIGURED) {
  const user = userEvent.setup();
  render(<MailServerForm view={view} onClose={onClose} />);
  return user;
}

const save = () => screen.getByRole("button", { name: "Save" });

async function testThenSave(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByRole("button", { name: "Send test mail" }));
  await user.click(save());
}

describe("the mail server form's outcomes", () => {
  it("states a refused test in plain words with the server's reply beneath, and keeps Save disabled", async () => {
    // given
    answerTests({
      accepted: false,
      reason: "authentication_failed",
      reply: "535 5.7.8 Authentication credentials invalid",
    });
    const user = openForm();

    // when
    await user.click(screen.getByRole("button", { name: "Send test mail" }));

    // then
    const refusal = screen.getByRole("alert");
    expect(refusal).toHaveTextContent("Authentication failed.");
    expect(refusal).toHaveTextContent(
      "535 5.7.8 Authentication credentials invalid",
    );
    expect(save()).toBeDisabled();
  });

  it("names what is wrong instead of sending a test", async () => {
    // given
    const tests = answerTests(ACCEPTED);
    const user = openForm(NOT_CONFIGURED);

    // when
    await user.click(screen.getByRole("button", { name: "Send test mail" }));

    // then
    expect(screen.getByText("Host is required.")).toBeVisible();
    expect(screen.getByText("From address is required.")).toBeVisible();
    expect(tests).not.toHaveBeenCalled();
  });

  it("asks for a new test when the save finds the test no longer valid, and disables Save", async () => {
    // given
    answerSaves(
      new ApiError(422, "Send a test mail.", "untested", "Send a test mail."),
    );
    const user = openForm();

    // when
    await testThenSave(user);

    // then
    expect(screen.getByText("Test again before saving.")).toBeVisible();
    expect(save()).toBeDisabled();
  });

  it("says the mail server changed since the form opened", async () => {
    // given
    answerSaves(
      new ApiError(409, "Reload it.", "stale_revision", "Reload it."),
    );
    const user = openForm();

    // when
    await testThenSave(user);

    // then
    expect(screen.getByText(STALE)).toBeVisible();
    expect(onClose).not.toHaveBeenCalled();
  });

  it("says when too many test mails were sent", async () => {
    // given
    answerTests(
      null,
      new ApiError(429, "Too many.", "mail_test_rate_limited", "Too many."),
    );
    const user = openForm();

    // when
    await user.click(screen.getByRole("button", { name: "Send test mail" }));

    // then
    expect(
      screen.getByText("Too many test mails. Try again in a few minutes."),
    ).toBeVisible();
  });

  it("asks before clearing, then clears and closes", async () => {
    // given
    const clears = answerClears();
    const user = openForm();
    await user.click(screen.getByRole("button", { name: "Clear" }));
    const dialog = within(screen.getByRole("alertdialog"));
    expect(
      dialog.getByText(
        "Mail stops until it is set up again. Fields set by the deployment stay.",
      ),
    ).toBeVisible();

    // when
    await user.click(dialog.getByRole("button", { name: "Clear" }));

    // then
    expect(clears.mock.calls[0]?.[0]).toBe(3);
    expect(onClose).toHaveBeenCalled();
  });

  it("says in the dialog that the form must close to show what changed, when a clear finds it so", async () => {
    // given
    answerClears(
      new ApiError(409, "Reload it.", "stale_revision", "Reload it."),
    );
    const user = openForm();
    await user.click(screen.getByRole("button", { name: "Clear" }));
    const dialog = within(screen.getByRole("alertdialog"));

    // when
    await user.click(dialog.getByRole("button", { name: "Clear" }));

    // then
    expect(
      dialog.getByText(
        "Changed by someone else since this form opened. Close the form to see the change.",
      ),
    ).toBeVisible();
    expect(onClose).not.toHaveBeenCalled();
  });

  it("offers nothing to clear on an unconfigured server", () => {
    // when
    openForm(NOT_CONFIGURED);

    // then
    expect(
      screen.queryByRole("button", { name: "Clear" }),
    ).not.toBeInTheDocument();
  });

  it("closes on Cancel without saving", async () => {
    // given
    const saves = answerSaves();
    const user = openForm();

    // when
    await user.click(screen.getByRole("button", { name: "Cancel" }));

    // then
    expect(onClose).toHaveBeenCalled();
    expect(saves).not.toHaveBeenCalled();
  });
});
