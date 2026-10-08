import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  ACCEPTED,
  CONFIGURED,
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

beforeEach(() => {
  answerTests(ACCEPTED);
  answerSaves();
  answerClears();
});

function openForm() {
  const user = userEvent.setup();
  render(<MailServerForm view={CONFIGURED} onClose={onClose} />);
  return user;
}

const save = () => screen.getByRole("button", { name: "Save" });
const host = () => screen.getByRole("textbox", { name: "Host" });

async function sendTest(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByRole("button", { name: "Send test mail" }));
}

describe("the mail server form", () => {
  it("keeps Save disabled until a test mail is accepted for what the form holds", async () => {
    // given
    const user = openForm();
    expect(save()).toBeDisabled();

    // when
    await sendTest(user);

    // then
    expect(
      screen.getByText("Test mail accepted by smtp.acme.com at 14:02."),
    ).toBeVisible();
    expect(save()).toBeEnabled();
  });

  it("disables Save again on any edit after the test, and drops the result", async () => {
    // given
    const user = openForm();
    await sendTest(user);

    // when
    await user.type(host(), "x");

    // then
    expect(save()).toBeDisabled();
    expect(screen.queryByText(/^Test mail accepted/)).not.toBeInTheDocument();
  });

  it("enables Save again once the edit is undone", async () => {
    // given
    const user = openForm();
    await sendTest(user);
    await user.type(host(), "x");

    // when
    await user.type(host(), "{Backspace}");

    // then
    expect(save()).toBeEnabled();
  });

  it("saves the tested values with the test's token and the revision the form opened at", async () => {
    // given
    const saves = answerSaves();
    const user = openForm();
    await sendTest(user);

    // when
    await user.click(save());

    // then
    expect(saves.mock.calls[0]?.[0]).toEqual({
      values: {
        host: "smtp.acme.com",
        port: 587,
        security: "STARTTLS",
        username: "axonia",
        password: null,
        fromAddress: "axonia@acme.com",
        fromName: "Acme Communities",
      },
      proof: { testToken: ACCEPTED.testToken, baseRevision: 3 },
    });
    expect(onClose).toHaveBeenCalled();
  });

  it("draws the stored password as text with Replace, never as an input holding it", () => {
    // when
    openForm();

    // then
    const password = within(screen.getByRole("group", { name: "Password" }));
    expect(password.getByText("••••••••")).toBeVisible();
    expect(password.getByRole("button", { name: "Replace" })).toBeVisible();
    expect(document.querySelectorAll('input[type="password"]')).toHaveLength(0);
  });

  it("swaps the stored password for an empty password input on Replace", async () => {
    // given
    const user = openForm();

    // when
    await user.click(screen.getByRole("button", { name: "Replace" }));

    // then
    expect(screen.getByLabelText("Password")).toHaveAttribute(
      "type",
      "password",
    );
    expect(screen.getByLabelText("Password")).toHaveValue("");
    expect(screen.queryByText("••••••••")).not.toBeInTheDocument();
  });

  it("tests a typed replacement password", async () => {
    // given
    const tests = answerTests(ACCEPTED);
    const user = openForm();
    await user.click(screen.getByRole("button", { name: "Replace" }));
    await user.type(screen.getByLabelText("Password"), "s3cret");

    // when
    await sendTest(user);

    // then
    expect(tests.mock.calls[0]?.[0]).toMatchObject({
      values: { password: "s3cret" },
    });
  });

  it("tests the security chosen", async () => {
    // given
    const tests = answerTests(ACCEPTED);
    const user = openForm();
    await user.click(screen.getByRole("radio", { name: "TLS" }));

    // when
    await sendTest(user);

    // then
    expect(tests.mock.calls[0]?.[0]).toMatchObject({
      values: { security: "TLS" },
    });
  });

  it("draws a field the deployment sets as read only, naming its variable", () => {
    // when
    openForm();

    // then
    const fromName = within(screen.getByRole("group", { name: "From name" }));
    expect(fromName.getByText("Acme Communities")).toBeVisible();
    expect(fromName.getByText("AXONIA_MAIL_FROM_NAME")).toBeVisible();
    expect(
      screen.queryByRole("textbox", { name: "From name" }),
    ).not.toBeInTheDocument();
  });

  it("prefills the test recipient with the caller's own address", () => {
    // when
    openForm();

    // then
    expect(screen.getByRole("textbox", { name: "Test recipient" })).toHaveValue(
      "ana.ruiz@acme.com",
    );
  });
});
