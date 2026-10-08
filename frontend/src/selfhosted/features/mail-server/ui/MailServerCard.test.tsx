import { render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { usePermissions } from "@/features/admin/api/use-permissions";
import {
  useMailServer,
  useMailServerChanges,
} from "@/selfhosted/features/mail-server/api/use-mail-server";
import {
  CONFIGURED,
  FIRST_SETUP,
  FROM_NAME_CHANGE,
  NOT_CONFIGURED,
  viewWith,
} from "@/selfhosted/features/mail-server/domain/mail-server.test-helpers";
import {
  answerChanges,
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

function grant(...held: string[]) {
  vi.mocked(usePermissions).mockReturnValue(
    permissionState((permission) => held.includes(permission)),
  );
}

function card() {
  return within(screen.getByRole("region", { name: "Mail server" }));
}

beforeEach(() => {
  grant("communication:write", "mail_server:read", "mail_server:write");
  answerStatus(true);
  answerChanges([]);
  answerTests(null);
  answerSaves();
  answerClears();
});

describe("the mail server card", () => {
  it("draws an unconfigured server as Not configured, with Set up and no facts, even those the deployment sets", () => {
    // given
    answerView({
      ...NOT_CONFIGURED,
      host: { value: "smtp.acme.com", lockedBy: "AXONIA_MAIL_HOST" },
      fromAddress: {
        value: "axonia@acme.com",
        lockedBy: "AXONIA_MAIL_FROM_ADDRESS",
      },
    });

    // when
    render(<MailServerCard />);

    // then
    expect(card().getByText("Not configured")).toBeVisible();
    expect(card().getByRole("button", { name: "Set up" })).toBeVisible();
    expect(card().queryByText(/^From /)).not.toBeInTheDocument();
    expect(card().queryByText(/smtp\.acme\.com/)).not.toBeInTheDocument();
  });

  it("states a configured server in one line of facts, with Edit and its history", () => {
    // given
    answerView(CONFIGURED);

    // when
    render(<MailServerCard />);

    // then
    expect(card().getByText("Configured")).toBeVisible();
    expect(card().getByText("smtp.acme.com:587, STARTTLS")).toBeVisible();
    expect(
      card().getByText("From Acme Communities <axonia@acme.com>"),
    ).toBeVisible();
    expect(card().getByRole("button", { name: "Edit" })).toBeVisible();
    expect(
      card().getByRole("button", {
        name: "History, last changed by Ana Ruiz on 2 Oct 2026",
      }),
    ).toBeVisible();
  });

  /** The line is shifted a dot's width under a clip, so a fact that wraps must start its later
   *  lines after its dot's box: a dot inline in the text would put them under the clip. */
  it("keeps each fact's dot in a box of its own beside the text that wraps", () => {
    // given
    answerView(CONFIGURED);

    // when
    render(<MailServerCard />);

    // then
    const fact = card().getByText("From Acme Communities <axonia@acme.com>");
    expect(fact).toHaveClass("min-w-0", "wrap-anywhere");
    expect(fact.previousElementSibling).toHaveClass("w-4", "shrink-0");
    expect(fact.parentElement).toHaveClass("flex", "min-w-0");
  });

  it("shows a reader the facts and no way to change them", () => {
    // given
    grant("mail_server:read");
    answerView(CONFIGURED);

    // when
    render(<MailServerCard />);

    // then
    expect(card().getByText("smtp.acme.com:587, STARTTLS")).toBeVisible();
    expect(
      card().queryByRole("button", { name: "Edit" }),
    ).not.toBeInTheDocument();
  });

  it("opens the form inside the card on Edit", async () => {
    // given
    const user = userEvent.setup();
    answerView(CONFIGURED);
    render(<MailServerCard />);

    // when
    await user.click(card().getByRole("button", { name: "Edit" }));

    // then
    expect(card().getByRole("textbox", { name: "Host" })).toHaveValue(
      "smtp.acme.com",
    );
    expect(
      card().queryByRole("button", { name: "Edit" }),
    ).not.toBeInTheDocument();
  });

  it("says delivery is failing, with the last refusal", () => {
    // given
    answerView(
      viewWith({
        state: "delivery_failing",
        lastFailure: {
          reason: "authentication_failed",
          at: "2026-10-02T21:02:00.000Z",
        },
      }),
    );

    // when
    render(<MailServerCard />);

    // then
    expect(card().getByText("Delivery failing")).toBeVisible();
    expect(
      card().getByText(
        "Last refusal: Authentication failed, 2 Oct 2026 at 14:02.",
      ),
    ).toBeVisible();
  });

  it("says the deployment changed the server since the last test", () => {
    // given
    answerView(
      viewWith({ state: "changed_since_test", changedSinceTest: true }),
    );

    // when
    render(<MailServerCard />);

    // then
    expect(card().getByText("Configured")).toBeVisible();
    expect(
      card().getByText("Changed by the deployment since the last test."),
    ).toBeVisible();
  });

  it("says the stored password cannot be read", () => {
    // given
    answerView(
      viewWith({
        state: "secret_unreadable",
        password: { set: true, readable: false, lockedBy: null },
      }),
    );

    // when
    render(<MailServerCard />);

    // then
    expect(card().getByText("Password can’t be read")).toBeVisible();
    expect(
      card().getByText("Mail is not sent until the password is entered again."),
    ).toBeVisible();
  });

  it("offers to read the mail server again when it could not be read", () => {
    // given
    answerView(undefined, true);

    // when
    render(<MailServerCard />);

    // then
    expect(card().getByRole("alert")).toHaveTextContent(
      "Could not load the mail server.",
    );
    expect(card().getByRole("button", { name: "Try again" })).toBeVisible();
  });

  it("opens the history on demand, and reads it only then", async () => {
    // given
    const user = userEvent.setup();
    answerView(CONFIGURED);
    answerChanges([FROM_NAME_CHANGE, FIRST_SETUP], true);
    render(<MailServerCard />);
    expect(useMailServerChanges).not.toHaveBeenCalled();

    // when
    await user.click(
      card().getByRole("button", {
        name: "History, last changed by Ana Ruiz on 2 Oct 2026",
      }),
    );

    // then
    expect(
      card().getByText("From name: axonia → Acme Communities"),
    ).toBeVisible();
    expect(card().getByText("2 Oct 2026 at 14:03")).toBeVisible();
    expect(card().getByText("Deleted member")).toBeVisible();
    expect(card().getByText("Set: host, password")).toBeVisible();
    expect(card().getByRole("button", { name: "Show older" })).toBeVisible();
  });

  it("shows a viewer who cannot read the mail server only that mail is off, and reads nothing more", () => {
    // given
    grant("communication:write");
    answerStatus(false);

    // when
    render(<MailServerCard />);

    // then
    expect(screen.getByText("Mail not configured.")).toBeVisible();
    expect(
      screen.queryByRole("region", { name: "Mail server" }),
    ).not.toBeInTheDocument();
    expect(useMailServer).not.toHaveBeenCalled();
  });

  it("draws nothing until the permissions are known", () => {
    // given
    vi.mocked(usePermissions).mockReturnValue(
      permissionState(() => false, { isLoading: true }),
    );
    answerStatus(false);

    // when
    const { container } = render(<MailServerCard />);

    // then
    expect(container).toBeEmptyDOMElement();
  });
});
