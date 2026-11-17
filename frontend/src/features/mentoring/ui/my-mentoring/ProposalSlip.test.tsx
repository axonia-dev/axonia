import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import type { Proposal } from "@/features/mentoring/domain/mentoring";
import { proposalOf } from "@/features/mentoring/test-data";
import { ProposalSlip } from "@/features/mentoring/ui/my-mentoring/ProposalSlip";

const TODAY = "2026-11-17";

function slip(
  proposal: Proposal,
  direction: "received" | "sent",
  refusal?: string,
) {
  const onAnswer = vi.fn();
  render(
    <ul>
      <ProposalSlip
        proposal={proposal}
        direction={direction}
        today={TODAY}
        isPending={false}
        onAnswer={onAnswer}
        {...(refusal === undefined ? {} : { refusal })}
      />
    </ul>,
  );
  return onAnswer;
}

const declined = proposalOf({
  status: "DECLINED",
  reopenableUntil: "2026-12-16T10:00:00Z",
});

beforeEach(() => {
  vi.useFakeTimers({ toFake: ["Date"] });
  vi.setSystemTime(new Date("2026-11-20T10:00:00Z"));
});

afterEach(() => {
  vi.useRealTimers();
});

describe("ProposalSlip", () => {
  it("a sent open proposal counts its days left and can be withdrawn", async () => {
    // given
    const onAnswer = slip(proposalOf({ lapsesOn: "2026-12-12" }), "sent");

    // when
    await userEvent.click(screen.getByRole("button", { name: "Withdraw" }));

    // then
    expect(screen.getByText("25 days left")).toBeInTheDocument();
    expect(onAnswer).toHaveBeenCalledWith("withdraw");
  });

  it("names the day, the community and the topics, never the message", () => {
    // when
    slip(proposalOf({ topics: ["Testing strategy"] }), "sent");

    // then
    expect(
      screen.getByText("· sent 16 Nov 2026 · Platform · Testing strategy"),
    ).toBeInTheDocument();
    expect(screen.queryByText(/Happy to pair up/)).not.toBeInTheDocument();
  });

  it("a declined proposal received reads Not this time and can be reopened", async () => {
    // given
    const onAnswer = slip(declined, "received");

    // when
    await userEvent.click(screen.getByRole("button", { name: "Reopen" }));

    // then
    expect(screen.getByText("Not this time")).toBeInTheDocument();
    expect(onAnswer).toHaveBeenCalledWith("reopen");
  });

  it("a declined proposal sent cannot be reopened by its sender", () => {
    // when
    slip(declined, "sent");

    // then
    expect(screen.getByText("Not this time")).toBeInTheDocument();
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });

  it("shows why a reopen was refused", () => {
    // when
    slip(declined, "received", "This proposal is no longer open.");

    // then
    expect(screen.getByRole("alert")).toHaveTextContent(
      "This proposal is no longer open.",
    );
  });
});
