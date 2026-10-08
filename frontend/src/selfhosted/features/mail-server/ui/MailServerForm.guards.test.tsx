import { act, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import {
  type MailServerView,
  MailValueRefused,
} from "@/selfhosted/features/mail-server/domain/mail-server";
import {
  ACCEPTED,
  CONFIGURED,
  viewWith,
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

const onClose = vi.fn();

const HINT =
  "Save is available once a test mail with these values is accepted.";

beforeEach(() => {
  answerTests(ACCEPTED);
  answerSaves();
  answerClears();
});

function openForm(view: MailServerView = CONFIGURED) {
  const user = userEvent.setup();
  const { rerender } = render(<MailServerForm view={view} onClose={onClose} />);
  return {
    user,
    readAgain: (again: MailServerView) =>
      rerender(<MailServerForm view={again} onClose={onClose} />),
  };
}

const save = () => screen.getByRole("button", { name: "Save" });
const password = () => screen.getByLabelText("Password");

async function sendTest(user: ReturnType<typeof userEvent.setup>) {
  await user.click(screen.getByRole("button", { name: "Send test mail" }));
}

describe("the mail server form's guards", () => {
  it("disables Save once a replacement password is typed after the test", async () => {
    // given
    const { user } = openForm();
    await sendTest(user);
    await user.click(screen.getByRole("button", { name: "Replace" }));

    // when
    await user.type(password(), "s3cret");

    // then
    expect(save()).toBeDisabled();
  });

  it("disables Save beside an empty replacement password, which would keep the stored one", async () => {
    // given
    const { user } = openForm();
    await sendTest(user);

    // when
    await user.click(screen.getByRole("button", { name: "Replace" }));

    // then
    expect(screen.getByText("A username needs a password.")).toBeVisible();
    expect(save()).toBeDisabled();
  });

  it("saves at the revision the form opened at, after the mail server is read again", async () => {
    // given
    const saves = answerSaves();
    const { user, readAgain } = openForm();
    readAgain(viewWith({ revision: 4 }));
    await sendTest(user);

    // when
    await user.click(save());

    // then
    expect(saves.mock.calls[0]?.[0]).toMatchObject({
      proof: { baseRevision: 3 },
    });
  });

  it("clears at the revision the form opened at, after the mail server is read again", async () => {
    // given
    const clears = answerClears();
    const { user, readAgain } = openForm();
    readAgain(viewWith({ revision: 4 }));
    await user.click(screen.getByRole("button", { name: "Clear" }));

    // when
    await user.click(
      within(screen.getByRole("alertdialog")).getByRole("button", {
        name: "Clear",
      }),
    );

    // then
    expect(clears.mock.calls[0]?.[0]).toBe(3);
  });

  it("draws a password the deployment sets as read only, naming its variable, with no Replace", () => {
    // when
    openForm(
      viewWith({
        password: {
          set: true,
          readable: true,
          lockedBy: "AXONIA_MAIL_PASSWORD",
        },
      }),
    );

    // then
    const field = within(screen.getByRole("group", { name: "Password" }));
    expect(field.getByText("AXONIA_MAIL_PASSWORD")).toBeVisible();
    expect(field.getByText("Set")).toBeInTheDocument();
    expect(
      screen.queryByRole("button", { name: "Replace" }),
    ).not.toBeInTheDocument();
  });

  it("reads the stored password as stored, not as eight bullets", () => {
    // when
    openForm();

    // then
    const field = within(screen.getByRole("group", { name: "Password" }));
    expect(field.getByText("Stored")).toBeInTheDocument();
    expect(field.getByText("••••••••")).toHaveAttribute("aria-hidden", "true");
  });

  it("sends no test to a recipient that is not a mail address, and says why", async () => {
    // given
    const tests = answerTests(ACCEPTED);
    const { user } = openForm();
    const recipient = screen.getByRole("textbox", { name: "Test recipient" });
    await user.clear(recipient);
    await user.type(recipient, "ana");

    // when
    await sendTest(user);

    // then
    expect(screen.getByText("Recipient is not a mail address.")).toBeVisible();
    expect(tests).not.toHaveBeenCalled();
  });

  it("announces the test's result in the region that already said why Save waits", async () => {
    // given
    const { user } = openForm();
    const status = screen.getByRole("status");
    expect(status).toHaveTextContent(HINT);

    // when
    await sendTest(user);

    // then
    expect(status).toHaveTextContent(
      "Test mail accepted by smtp.acme.com at 14:02.",
    );
  });

  it("tells a screen reader on Save why it waits", () => {
    // when
    openForm();

    // then
    expect(save()).toHaveAccessibleDescription(HINT);
  });

  it("moves focus into the form as it opens", () => {
    // when
    openForm();

    // then
    expect(screen.getByRole("textbox", { name: "Host" })).toHaveFocus();
  });

  it("moves focus into the empty password input on Replace", async () => {
    // given
    const { user } = openForm();

    // when
    await user.click(screen.getByRole("button", { name: "Replace" }));

    // then
    expect(password()).toHaveFocus();
  });

  it("moves focus to the password a test for another server waits for", async () => {
    // given
    const { user } = openForm();
    await user.type(screen.getByRole("textbox", { name: "Host" }), "x");

    // when
    await sendTest(user);

    // then
    expect(password()).toBeInvalid();
    expect(password()).toHaveFocus();
  });

  it("moves focus to a password the installation refused once the test answers", async () => {
    // given
    const refuse = answerTestsLater();
    const { user } = openForm();
    await sendTest(user);

    // when
    act(() => refuse(new MailValueRefused("password")));

    // then
    expect(password()).toHaveFocus();
  });

  it("moves focus to a refused password when the focus fell to the page while the test was in flight", async () => {
    // given
    const refuse = answerTestsLater();
    const { user } = openForm();
    await sendTest(user);
    act(() => (document.activeElement as HTMLElement).blur());

    // when
    act(() => refuse(new MailValueRefused("password")));

    // then
    expect(password()).toHaveFocus();
  });

  it("leaves focus where the reader moved it while the test was in flight", async () => {
    // given
    const refuse = answerTestsLater();
    const { user } = openForm();
    await sendTest(user);
    const fromAddress = screen.getByRole("textbox", { name: "From address" });
    await user.click(fromAddress);

    // when
    act(() => refuse(new MailValueRefused("password")));

    // then
    expect(password()).toBeInvalid();
    expect(fromAddress).toHaveFocus();
  });
});
