import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  type MailServerView,
  MailValueRefused,
} from "@/selfhosted/features/mail-server/domain/mail-server";
import {
  ACCEPTED,
  CONFIGURED,
  NOT_CONFIGURED,
  viewWith,
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

const HINT = "The stored password is kept only for the saved server.";

beforeEach(() => {
  answerTests(ACCEPTED);
  answerSaves();
  answerClears();
});

function openForm(view: MailServerView = CONFIGURED) {
  const user = userEvent.setup();
  render(<MailServerForm view={view} onClose={vi.fn()} />);
  return user;
}

const host = () => screen.getByRole("textbox", { name: "Host" });
const sendTest = () => screen.getByRole("button", { name: "Send test mail" });

describe("the stored password on another server", () => {
  it("asks for the password once the host moves, saying why", async () => {
    // given
    const user = openForm();

    // when
    await user.type(host(), "x");

    // then
    const password = screen.getByLabelText("Password");
    expect(password).toHaveValue("");
    expect(password).toHaveAccessibleDescription(HINT);
    expect(screen.queryByText("Stored")).not.toBeInTheDocument();
  });

  it("asks for the password once security weakens to none, and not between encryptions", async () => {
    // given
    const user = openForm();
    await user.click(screen.getByRole("radio", { name: "TLS" }));
    expect(screen.getByText("Stored")).toBeInTheDocument();

    // when
    await user.click(screen.getByRole("radio", { name: "None" }));

    // then
    expect(screen.getByLabelText("Password")).toHaveValue("");
  });

  it("sends no test until the password for another server is typed", async () => {
    // given
    const tests = answerTests(ACCEPTED);
    const user = openForm();
    await user.type(host(), "x");

    // when
    await user.click(sendTest());

    // then
    expect(
      screen.getByText("Enter the password again for another server."),
    ).toBeVisible();
    expect(tests).not.toHaveBeenCalled();
  });

  it("marks the password for another server on the field alone, without the hint beside it", async () => {
    // given
    const user = openForm();
    await user.type(host(), "x");

    // when
    await user.click(sendTest());

    // then
    const password = screen.getByLabelText("Password");
    expect(password).toBeInvalid();
    expect(password).toHaveAccessibleDescription(
      "Enter the password again for another server.",
    );
    expect(screen.queryByText(HINT)).not.toBeInTheDocument();
  });

  it("tests another server with the password typed for it", async () => {
    // given
    const tests = answerTests(ACCEPTED);
    const user = openForm();
    await user.type(host(), "x");
    await user.type(screen.getByLabelText("Password"), "s3cret");

    // when
    await user.click(sendTest());

    // then
    expect(tests.mock.calls[0]?.[0]).toMatchObject({
      values: { host: "smtp.acme.comx", password: "s3cret" },
    });
  });

  it("draws the stored password again once the host moves back", async () => {
    // given
    const user = openForm();
    await user.type(host(), "x");
    await user.type(screen.getByLabelText("Password"), "s3cret");

    // when
    await user.type(host(), "{Backspace}");

    // then
    expect(screen.getByText("Stored")).toBeInTheDocument();
    expect(document.querySelectorAll('input[type="password"]')).toHaveLength(0);
  });

  it("says on a password the deployment sets that the installation refused it for another server", async () => {
    // given
    answerTests(null, new MailValueRefused("password"));
    const user = openForm(
      viewWith({
        password: {
          set: true,
          readable: true,
          lockedBy: "AXONIA_MAIL_PASSWORD",
        },
      }),
    );
    await user.type(host(), "x");

    // when
    await user.click(sendTest());

    // then
    const field = within(screen.getByRole("group", { name: "Password" }));
    expect(
      field.getByText(
        "The deployment sets this password for the current server.",
      ),
    ).toBeVisible();
    expect(
      screen.queryByText("Something went wrong. Please try again."),
    ).not.toBeInTheDocument();
  });

  it("asks for a password the installation refused for another server, over the stored one, and moves focus to it", async () => {
    // given
    answerTests(null, new MailValueRefused("password"));
    const user = openForm();

    // when
    await user.click(sendTest());

    // then
    const password = screen.getByLabelText("Password");
    expect(password).toHaveValue("");
    expect(password).toHaveFocus();
    expect(screen.getByText("Enter the password again.")).toBeVisible();
  });
});

describe("Keep stored, the way back from Replace", () => {
  const replace = () => screen.getByRole("button", { name: "Replace" });
  const keepStored = () => screen.getByRole("button", { name: "Keep stored" });

  it("draws the stored password again and returns the focus to Replace", async () => {
    // given
    const user = openForm();
    await user.click(replace());

    // when
    await user.click(keepStored());

    // then
    expect(screen.getByText("Stored")).toBeInTheDocument();
    expect(replace()).toHaveFocus();
  });

  it("holds the other word unseen, so the input keeps its width as they swap", async () => {
    // given
    const user = openForm();
    expect(within(replace()).getByText("Keep stored")).toHaveAttribute(
      "aria-hidden",
    );

    // when
    await user.click(replace());

    // then
    expect(within(keepStored()).getByText("Replace")).toHaveAttribute(
      "aria-hidden",
    );
  });

  it("sends no password once the stored one is kept", async () => {
    // given
    const tests = answerTests(ACCEPTED);
    const user = openForm();
    await user.click(replace());
    await user.type(screen.getByLabelText("Password"), "s3cret");
    await user.click(keepStored());

    // when
    await user.click(sendTest());

    // then
    expect(tests.mock.calls[0]?.[0]).toMatchObject({
      values: { password: null },
    });
  });

  it("enables Save again for a test accepted before Replace", async () => {
    // given
    const user = openForm();
    await user.click(sendTest());
    await user.click(replace());
    expect(screen.getByRole("button", { name: "Save" })).toBeDisabled();

    // when
    await user.click(keepStored());

    // then
    expect(screen.getByRole("button", { name: "Save" })).toBeEnabled();
  });

  it("is not offered for another server, which the stored password does not serve", async () => {
    // given
    const user = openForm();
    await user.click(replace());

    // when
    await user.type(host(), "x");

    // then
    expect(
      screen.queryByRole("button", { name: "Keep stored" }),
    ).not.toBeInTheDocument();
  });

  it("is not offered when no password is stored", () => {
    // when
    openForm(NOT_CONFIGURED);

    // then
    expect(screen.getByLabelText("Password")).toHaveValue("");
    expect(
      screen.queryByRole("button", { name: "Keep stored" }),
    ).not.toBeInTheDocument();
  });

  it("is not offered for a stored password that cannot be read", () => {
    // when
    openForm(
      viewWith({ password: { set: true, readable: false, lockedBy: null } }),
    );

    // then
    expect(
      screen.queryByRole("button", { name: "Keep stored" }),
    ).not.toBeInTheDocument();
  });
});
