import { act, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { ApiError } from "@/lib/api-client";
import { MailValueRefused } from "@/selfhosted/features/mail-server/domain/mail-server";
import {
  ACCEPTED,
  CONFIGURED,
} from "@/selfhosted/features/mail-server/domain/mail-server.test-helpers";
import {
  answerClears,
  answerSaves,
  answerTests,
  answerTestsLater,
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

const GENERIC = "Something went wrong. Please try again.";
const RECIPIENT_REFUSED = "Recipient is not a mail address.";
const STALE =
  "Changed by someone else since this form opened. Cancel to see the change.";
const STALE_IN_DIALOG =
  "Changed by someone else since this form opened. Close the form to see the change.";

beforeEach(() => {
  answerTests(ACCEPTED);
  answerSaves();
  answerClears();
});

function openForm() {
  const user = userEvent.setup();
  render(<MailServerForm view={CONFIGURED} onClose={vi.fn()} />);
  return user;
}

const save = () => screen.getByRole("button", { name: "Save" });
const fromAddress = () => screen.getByRole("textbox", { name: "From address" });
const recipient = () => screen.getByRole("textbox", { name: "Test recipient" });

async function sendTest(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByRole("button", { name: "Send test mail" }));
}

function stale() {
  return new ApiError(409, "Reload it.", "stale_revision", "Reload it.");
}

describe("the mail server form's refused calls", () => {
  it("marks a value the installation refused on its own field", async () => {
    // given
    answerTests(null, new MailValueRefused("fromAddress"));
    const user = openForm();

    // when
    await sendTest(user);

    // then
    expect(fromAddress()).toBeInvalid();
    expect(fromAddress()).toHaveFocus();
    expect(
      screen.getByText("From address is not a mail address."),
    ).toBeVisible();
    expect(screen.queryByText(GENERIC)).not.toBeInTheDocument();
  });

  it("says a refused password that was typed holds a hidden character, rather than asking for it again", async () => {
    // given
    answerTests(null, new MailValueRefused("password"));
    const user = openForm();
    await user.click(screen.getByRole("button", { name: "Replace" }));
    await user.type(screen.getByLabelText("Password"), "s3cret");

    // when
    await sendTest(user);

    // then
    expect(
      screen.getByText("Password holds a tab or another hidden character."),
    ).toBeVisible();
  });

  it("says nothing of a refusal about values the form no longer holds, after an edit while the test was in flight", async () => {
    // given
    const refuse = answerTestsLater();
    const user = openForm();
    await sendTest(user);
    await user.click(screen.getByRole("button", { name: "Replace" }));
    await user.type(screen.getByLabelText("Password"), "s3cret");

    // when
    act(() => refuse(new MailValueRefused("password")));

    // then
    expect(screen.getByLabelText("Password")).toBeValid();
    expect(
      screen.queryByText("Password holds a tab or another hidden character."),
    ).not.toBeInTheDocument();
    expect(
      screen.queryByText("Enter the password again."),
    ).not.toBeInTheDocument();
  });

  it("keeps the stored password drawn when a password refusal lands after the form moved on", async () => {
    // given
    const refuse = answerTestsLater();
    const user = openForm();
    await sendTest(user);
    await user.type(fromAddress(), "m");

    // when
    act(() => refuse(new MailValueRefused("password")));

    // then
    expect(screen.getByText("Stored")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Replace" })).toBeInTheDocument();
  });

  it("marks a refusal about the values the form still holds once the test answers", async () => {
    // given
    const refuse = answerTestsLater();
    const user = openForm();
    await sendTest(user);

    // when
    act(() => refuse(new MailValueRefused("password")));

    // then
    expect(screen.getByText("Enter the password again.")).toBeVisible();
  });

  it("asks for a refused password again after the recipient is edited while the test was in flight", async () => {
    // given
    const refuse = answerTestsLater();
    const user = openForm();
    await sendTest(user);
    await user.type(recipient(), "m");

    // when
    act(() => refuse(new MailValueRefused("password")));

    // then
    expect(screen.getByLabelText("Password")).toBeInvalid();
    expect(screen.getByText("Enter the password again.")).toBeVisible();
  });

  it("keeps a recipient refusal after a value is edited while the test was in flight", async () => {
    // given
    const refuse = answerTestsLater();
    const user = openForm();
    await sendTest(user);
    await user.type(fromAddress(), "m");

    // when
    act(() => refuse(new MailValueRefused("recipient")));

    // then
    expect(screen.getByText(RECIPIENT_REFUSED)).toBeVisible();
  });

  it("says nothing of a recipient refusal after the recipient is edited while the test was in flight", async () => {
    // given
    const refuse = answerTestsLater();
    const user = openForm();
    await sendTest(user);
    await user.type(recipient(), "m");

    // when
    act(() => refuse(new MailValueRefused("recipient")));

    // then
    expect(screen.queryByText(RECIPIENT_REFUSED)).not.toBeInTheDocument();
  });

  it("marks a refused recipient on the recipient", async () => {
    // given
    answerTests(null, new MailValueRefused("recipient"));
    const user = openForm();

    // when
    await sendTest(user);

    // then
    expect(screen.getByText(RECIPIENT_REFUSED)).toBeVisible();
  });

  it("drops the mark once the field is edited", async () => {
    // given
    answerTests(null, new MailValueRefused("fromAddress"));
    const user = openForm();
    await sendTest(user);

    // when
    await user.type(fromAddress(), "m");

    // then
    expect(
      screen.queryByText("From address is not a mail address."),
    ).not.toBeInTheDocument();
  });

  it("says a value was refused when the refusal names no field of the form", async () => {
    // given
    answerTests(
      null,
      new ApiError(400, "Bad.", "field_invalid", "baseRevision: required."),
    );
    const user = openForm();

    // when
    await sendTest(user);

    // then
    expect(screen.getByText("A value was refused.")).toBeVisible();
  });

  it("forgets that the test was no longer valid once a new test passes", async () => {
    // given
    const saves = answerSaves(
      new ApiError(422, "Send a test mail.", "untested", "Send a test mail."),
    );
    const user = openForm();
    await sendTest(user);
    await user.click(save());
    expect(screen.getByText("Test again before saving.")).toBeVisible();

    // when
    await sendTest(user);

    // then
    expect(
      screen.queryByText("Test again before saving."),
    ).not.toBeInTheDocument();
    expect(save()).toBeEnabled();
    expect(saves).toHaveBeenCalledTimes(1);
  });

  it("keeps Save disabled and says why once a save is refused as stale, even after a new test", async () => {
    // given
    answerSaves(stale());
    const user = openForm();
    await sendTest(user);
    await user.click(save());

    // when
    await sendTest(user);

    // then
    expect(screen.getByText(STALE)).toBeVisible();
    expect(save()).toBeDisabled();
  });

  it("marks the form stale behind the dialog when a clear is refused as stale", async () => {
    // given
    answerClears(stale());
    const user = openForm();
    await sendTest(user);
    await user.click(screen.getByRole("button", { name: "Clear" }));
    const dialog = within(screen.getByRole("alertdialog"));
    await user.click(dialog.getByRole("button", { name: "Clear" }));

    // when
    await user.click(dialog.getByRole("button", { name: "Cancel" }));

    // then
    expect(screen.getByText(STALE)).toBeVisible();
    expect(save()).toBeDisabled();
  });

  it("opens the Clear dialog again with no refusal from the last attempt", async () => {
    // given
    answerClears(stale());
    const user = openForm();
    await user.click(screen.getByRole("button", { name: "Clear" }));
    const first = within(screen.getByRole("alertdialog"));
    await user.click(first.getByRole("button", { name: "Clear" }));
    await user.click(first.getByRole("button", { name: "Cancel" }));

    // when
    await user.click(screen.getByRole("button", { name: "Clear" }));

    // then
    expect(
      within(screen.getByRole("alertdialog")).queryByText(STALE_IN_DIALOG),
    ).not.toBeInTheDocument();
  });
});
