import { render, screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { answerStatus } from "@/selfhosted/features/mail-server/ui/mail-server-hooks.test-helpers";
import { MailNotConfiguredNotice } from "@/selfhosted/features/mail-server/ui/MailNotConfiguredNotice";

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

beforeEach(() => {
  answerStatus(false);
});

describe("the notice that mail is not configured", () => {
  it("says so plainly, never as a link", () => {
    // when
    render(<MailNotConfiguredNotice />);

    // then
    expect(screen.getByText("Mail not configured.")).toBeVisible();
    expect(screen.queryByRole("link")).not.toBeInTheDocument();
  });

  it("says nothing while mail is configured", () => {
    // given
    answerStatus(true);

    // when
    const { container } = render(<MailNotConfiguredNotice />);

    // then
    expect(container).toBeEmptyDOMElement();
  });

  it("says nothing when whether mail is configured cannot be read", () => {
    // given
    answerStatus(undefined, true);

    // when
    const { container } = render(<MailNotConfiguredNotice />);

    // then
    expect(container).toBeEmptyDOMElement();
  });
});
